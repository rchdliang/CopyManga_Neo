package com.rchdliang.copymanga_neo.data.repository

import com.google.gson.JsonElement
import com.rchdliang.copymanga_neo.data.local.AuthDataStore
import com.rchdliang.copymanga_neo.data.local.NetworkSettingsStore
import com.rchdliang.copymanga_neo.data.remote.api.CommentApiService
import com.rchdliang.copymanga_neo.domain.model.CommentKind
import com.rchdliang.copymanga_neo.domain.model.CommentPage
import com.rchdliang.copymanga_neo.domain.model.CommentPolicy
import com.rchdliang.copymanga_neo.domain.model.CommentReplyPreview
import com.rchdliang.copymanga_neo.domain.model.CommentTarget
import com.rchdliang.copymanga_neo.domain.model.CommentText
import com.rchdliang.copymanga_neo.domain.model.MangaComment
import com.rchdliang.copymanga_neo.domain.model.RequestMode
import com.rchdliang.copymanga_neo.domain.repository.CommentClosedException
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
    override suspend fun getReportConfig(target: CommentTarget): Result<com.rchdliang.copymanga_neo.domain.model.CommentReportConfig> =
        result {
            val appMode = networkSettings.settings.value.requestMode == RequestMode.OFFICIAL_APP
            val generation = networkSettings.generation
            val headers = CommentApiService.readHeaders(appMode)
            val response = when (target.kind) {
                CommentKind.COMIC -> api.reportConfig(headers = headers)
                CommentKind.CHAPTER -> api.chapterReportConfig(headers = headers)
            }
            check(networkSettings.generation == generation) { "网络设置已更改，请刷新重试" }
            checkResponse(response.code(), response.body()?.code, response.body()?.message)
            val config = response.body()?.results ?: throw IOException("举报配置缺少数据")
            val types = config.types.orEmpty().mapNotNull { row ->
                val type = row?.type?.takeIf { it > 0 } ?: return@mapNotNull null
                val name = row.name?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                com.rchdliang.copymanga_neo.domain.model.CommentReportType(type, name)
            }.distinctBy { it.type }
            check(types.isNotEmpty()) { "暂无可用的举报类型" }
            com.rchdliang.copymanga_neo.domain.model.CommentReportConfig(
                types,
                config.tip.orEmpty()
            )
        }

    override suspend fun reportComment(
        target: CommentTarget,
        commentId: String,
        type: Int,
        reason: String
    ): Result<Unit> = result {
        require(target.id.isNotBlank() && commentId.isNotBlank() && type > 0) { "请选择举报类型" }
        require(CommentText.length(reason) <= CommentText.MAX_LENGTH) { "举报描述最多 200 字" }
        val token = auth.getTokenSync()?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("请先登录后举报")
        val generation = auth.getAuthGeneration()
        val networkGeneration = networkSettings.generation
        val mode = networkSettings.settings.value.requestMode
        val response = when (target.kind) {
            CommentKind.COMIC -> api.reportComment(
                target.id,
                commentId,
                type,
                reason.trim(),
                "Token $token",
                headers = CommentApiService.reportHeaders(
                    networkSettings.settings.value.requestMode == RequestMode.OFFICIAL_APP
                )
            )

            CommentKind.CHAPTER -> api.reportChapterComment(
                commentId,
                target.id,
                type,
                reason.trim(),
                "Token $token"
            )
        }
        if (response.code() == 401 || response.body()?.code == 401) {
            networkSettings.ifCurrent(networkGeneration) {
                auth.clearAuthSyncIfTokenMatches(
                    token,
                    mode,
                    generation
                )
            }
        }
        checkWriteResponse(response)
        check(networkSettings.generation == networkGeneration) { "网络设置已变化，请确认举报结果" }
        check(auth.getAuthGeneration() == generation && auth.getTokenSync() == token) { "登录状态已变化，请确认举报结果" }
    }

    override suspend fun getComments(
        target: CommentTarget,
        offset: Int,
        replyId: String?
    ): Result<CommentPage> = readComments(target, offset, replyId)

    override suspend fun getReplyPreview(
        target: CommentTarget,
        commentId: String
    ): Result<CommentReplyPreview> =
        readComments(target, 0, commentId, limit = 3).map { page ->
            val replies = page.comments.distinctBy { it.id }
            // 少量回复用实际返回数量修正屏蔽造成的虚高；较多回复沿用 API 总数。
            CommentReplyPreview(
                comments = replies.take(2),
                total = if (replies.size < 3) replies.size else page.total.coerceAtLeast(replies.size)
            )
        }

    private suspend fun readComments(
        target: CommentTarget,
        offset: Int,
        replyId: String?,
        limit: Int = 20
    ): Result<CommentPage> = result {
        require(target.id.isNotBlank() && offset >= 0) { "评论标识无效" }
        val generation = networkSettings.generation
        val appMode = networkSettings.settings.value.requestMode == RequestMode.OFFICIAL_APP
        val headers = CommentApiService.readHeaders(appMode)
        val response = when (target.kind) {
            CommentKind.COMIC -> api.comicComments(
                target.id,
                replyId?.takeIf { it.isNotBlank() } ?: "".takeIf { appMode },
                offset,
                if (appMode) limit.coerceAtMost(10) else limit,
                headers = headers
            )

            CommentKind.CHAPTER -> api.chapterComments(
                target.id, offset, limit = if (appMode) 10 else 30, headers = headers
            )
        }
        check(networkSettings.generation == generation) { "网络设置已更改，请刷新重试" }
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
        val returnedOffset = page.offset ?: offset
        check(returnedOffset == offset) { "评论分页位置无效，请重试" }
        val window = page.limit ?: rows.size
        check(window >= 0 && (rows.isEmpty() || window > 0)) { "评论分页大小无效，请重试" }
        val nextLong = offset.toLong() + window
        check(nextLong <= Int.MAX_VALUE) { "评论分页位置无效，请重试" }
        val next = nextLong.toInt()
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
        val mode = networkSettings.settings.value.requestMode
        val appMode = networkSettings.settings.value.requestMode == RequestMode.OFFICIAL_APP
        val headers = CommentApiService.postHeaders(appMode)
        require(target.kind != CommentKind.CHAPTER || replyId.isNullOrBlank()) { "章节吐槽不支持回复" }
        val response = when (target.kind) {
            CommentKind.COMIC -> api.postComicComment(
                target.id,
                content.trim(),
                replyId?.takeIf { it.isNotBlank() } ?: "".takeIf { appMode },
                "Token $token",
                headers = headers
            )

            CommentKind.CHAPTER -> api.postChapterComment(
                target.id, content.trim(), "Token $token", headers = headers
            )
        }
        if (response.code() == 401 || response.body()?.code == 401) {
            networkSettings.ifCurrent(networkGeneration) {
                auth.clearAuthSyncIfTokenMatches(
                    token,
                    mode,
                    generation
                )
            }
        }
        checkWriteResponse(response)
        check(networkSettings.generation == networkGeneration) { "网络设置已变化，请刷新评论确认发表结果" }
        check(auth.getAuthGeneration() == generation && auth.getTokenSync() == token) { "登录状态已变化，请刷新评论确认发表结果" }
    }

    /** HTTP 210 的业务校验也从错误正文读取，避免丢失评论长度及新账号等待提示。 */
    private fun checkWriteResponse(
        response: retrofit2.Response<com.rchdliang.copymanga_neo.data.remote.dto.CommentPostResponseDto>
    ) {
        val body = response.body()
        val error = response.errorBody()?.use { it.string() }
        val json =
            runCatching { com.google.gson.JsonParser.parseString(error).asJsonObject }.getOrNull()
        val code = body?.code ?: runCatching { json?.get("code")?.asInt }.getOrNull()
        val message = body?.message ?: runCatching {
            json?.get("message")?.takeUnless { it.isJsonNull }?.asString
        }.getOrNull()
        checkResponse(response.code(), code, message)
    }

    private fun checkResponse(http: Int, code: Int?, message: String?) {
        if (CommentPolicy.isClosedResponse(http, code, message)) {
            throw CommentClosedException(message?.take(300) ?: "该漫画不允许评论")
        }
        if (http == 401 || code == 401) throw IllegalStateException("登录已过期，请重新登录")
        if (http !in 200..299) throw IOException(
            message?.takeIf { it.isNotBlank() }?.take(300) ?: "评论请求失败（HTTP $http）"
        )
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
