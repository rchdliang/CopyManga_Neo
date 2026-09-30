package com.rchdliang.copymanga_neo.data.repository

import com.google.gson.JsonElement
import com.rchdliang.copymanga_neo.data.local.AuthDataStore
import com.rchdliang.copymanga_neo.data.local.NetworkSettingsStore
import com.rchdliang.copymanga_neo.data.remote.api.CommentApiService
import com.rchdliang.copymanga_neo.domain.model.CommentKind
import com.rchdliang.copymanga_neo.domain.model.CommentPage
import com.rchdliang.copymanga_neo.domain.model.CommentTarget
import com.rchdliang.copymanga_neo.domain.model.CommentText
import com.rchdliang.copymanga_neo.domain.model.MangaComment
import com.rchdliang.copymanga_neo.domain.repository.CommentRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CommentRepositoryImpl @Inject constructor(
    private val api: CommentApiService,
    private val auth: AuthDataStore,
    private val networkSettings: NetworkSettingsStore
) : CommentRepository {
    override suspend fun getComments(
        target: CommentTarget,
        offset: Int,
        replyId: String?
    ): Result<CommentPage> = result {
        require(target.id.isNotBlank() && offset >= 0) { "评论标识无效" }
        val response = when (target.kind) {
            CommentKind.COMIC -> api.comicComments(
                target.id,
                replyId?.takeIf { it.isNotBlank() },
                offset
            )

            CommentKind.CHAPTER -> api.chapterComments(target.id, offset)
        }
        checkResponse(response.code(), response.body()?.code, response.body()?.message)
        val page = response.body()?.results ?: throw IOException("评论响应缺少数据")
        val rows = page.list ?: throw IOException("评论响应缺少列表")
        val comments = rows.mapNotNull { dto ->
            val id =
                dto?.id?.text()?.takeIf { it.isNotBlank() && it != "0" } ?: return@mapNotNull null
            MangaComment(
                id,
                dto.username.orEmpty(),
                dto.avatar,
                dto.comment.orEmpty(),
                dto.createdAt.orEmpty(),
                dto.replyCount?.text()?.toIntOrNull()?.coerceAtLeast(0) ?: 0,
                dto.parentUsername
            )
        }
        val next = (page.offset?.coerceAtLeast(offset) ?: offset) + rows.size
        val total = page.total?.text()?.toIntOrNull()?.coerceAtLeast(0) ?: next
        CommentPage(comments, total, next.takeIf { rows.isNotEmpty() && it < total })
    }

    override suspend fun postComment(
        target: CommentTarget,
        content: String,
        replyId: String?
    ): Result<Unit> = result {
        require(target.id.isNotBlank()) { "评论标识无效" }
        require(CommentText.isValid(content)) { "评论需要 3～200 个字" }
        val token = auth.getTokenSync()?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("请先登录后发表评论")
        val generation = auth.getAuthGeneration()
        val networkGeneration = networkSettings.generation
        // 固定本次操作的账号，排队期间切换登录也不会改用其他人的 Token。
        val response = when (target.kind) {
            CommentKind.COMIC -> api.postComicComment(
                target.id,
                content.trim(),
                replyId,
                "Token $token"
            )

            CommentKind.CHAPTER -> api.postChapterComment(target.id, content.trim(), "Token $token")
        }
        if (response.code() == 401 || response.body()?.code == 401) {
            networkSettings.ifCurrent(networkGeneration) { auth.clearAuthSyncIfTokenMatches(token) }
        }
        checkResponse(response.code(), response.body()?.code, response.body()?.message)
        check(auth.getAuthGeneration() == generation && auth.getTokenSync() == token) { "登录状态已变化，请刷新评论确认发表结果" }
    }

    private fun checkResponse(http: Int, code: Int?, message: String?) {
        if (http == 401 || code == 401) throw IllegalStateException("登录已过期，请重新登录")
        if (http !in 200..299) throw IOException("评论请求失败（HTTP $http）")
        if (code != 200) throw IllegalStateException(
            message?.takeIf { it.isNotBlank() }?.take(300) ?: "评论请求未成功（code=$code）"
        )
    }

    private fun JsonElement.text(): String? = if (isJsonPrimitive) asString else null
    private suspend fun <T> result(block: suspend () -> T): Result<T> =
        withContext(Dispatchers.IO) {
            try {
                Result.success(block())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
}
