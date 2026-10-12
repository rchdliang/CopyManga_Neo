package com.rchdliang.copymanga_neo.data.repository

import com.rchdliang.copymanga_neo.data.local.AuthDataStore
import com.rchdliang.copymanga_neo.data.local.NetworkSettingsStore
import com.rchdliang.copymanga_neo.data.novel.NovelJson
import com.rchdliang.copymanga_neo.data.remote.api.MemberWriteApiService
import com.rchdliang.copymanga_neo.domain.model.LocalReadingSession
import com.rchdliang.copymanga_neo.domain.model.NovelShelfPage
import com.rchdliang.copymanga_neo.domain.model.RequestMode
import com.rchdliang.copymanga_neo.domain.repository.NovelShelfRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NovelShelfRepositoryImpl @Inject constructor(
    private val api: MemberWriteApiService,
    private val auth: AuthDataStore,
    private val network: NetworkSettingsStore
) : NovelShelfRepository {
    private data class Session(
        val reading: LocalReadingSession,
        val token: String,
        val networkGeneration: Long
    )

    private suspend fun session(): Session {
        auth.awaitReady()
        val reading = auth.getReadingSessionSync() ?: error("账号状态尚未就绪")
        val token = auth.getTokenSync()?.takeIf { it.isNotBlank() } ?: error("请先登录后使用书架")
        return Session(reading, token, network.generation)
    }

    private fun ensureSession(session: Session) {
        check(
            auth.getReadingSessionSync() == session.reading && auth.getTokenSync() == session.token &&
                    network.generation == session.networkGeneration &&
                    network.settings.value.requestMode == session.reading.mode
        ) { "账号或请求模式已变化，请刷新书架" }
    }

    /** 固定 Token 的请求跳过认证拦截器；仅清理捕获的失效会话。 */
    private fun checkAuthentication(session: Session, httpCode: Int, businessCode: Int?) {
        if (httpCode != 401 && businessCode != 401) return
        network.ifCurrent(session.networkGeneration) {
            auth.clearAuthSyncIfTokenMatches(
                session.token,
                session.reading.mode,
                session.reading.generation
            )
        }
        error("登录已过期，请重新登录")
    }

    private suspend fun page(session: Session, offset: Int): NovelShelfPage {
        require(offset >= 0)
        ensureSession(session)
        val app = session.reading.mode == RequestMode.OFFICIAL_APP
        val response = api.novelFavorites(
            "Token ${session.token}", offset, limit = if (app) 20 else 21,
            update = if (app) null else true, requestMode = session.reading.mode
        )
        val body = response.body()
        checkAuthentication(session, response.code(), body?.get("code")?.asInt)
        ensureSession(session)
        check(response.isSuccessful && body?.get("code")?.asInt == 200) {
            body?.let { NovelJson.text(it, "message") }?.takeIf { it.isNotBlank() }
                ?: "小说书架加载失败"
        }
        val result = body.getAsJsonObject("results") ?: throw IOException("小说书架缺少数据")
        return NovelJson.shelfPage(result, offset)
    }

    private suspend fun contains(session: Session, path: String): Boolean {
        ensureSession(session)
        val response =
            api.novelQuery(path, "Token ${session.token}", requestMode = session.reading.mode)
        val body = response.body()
        checkAuthentication(session, response.code(), body?.code)
        ensureSession(session)
        check(response.isSuccessful && body?.code == 200) { body?.message ?: "书架状态读取失败" }
        val status = body.results ?: throw IOException("书架状态缺少数据")
        if (!status.isLogin) checkAuthentication(session, 401, 401)
        return status.collect != null
    }

    override suspend fun list(offset: Int): Result<NovelShelfPage> =
        result { page(session(), offset) }

    override suspend fun contains(path: String): Result<Boolean> = result {
        require(path.isNotBlank())
        contains(session(), path)
    }

    override suspend fun add(bookId: String, path: String): Result<Unit> =
        setCollected(bookId, path, true)

    override suspend fun remove(bookId: String, path: String): Result<Unit> =
        setCollected(bookId, path, false)

    private suspend fun setCollected(
        bookId: String,
        path: String,
        collected: Boolean
    ): Result<Unit> = result {
        require(bookId.isNotBlank() && path.isNotBlank())
        val session = session()
        ensureSession(session)
        val response = api.addNovelFavorite(
            bookId,
            "Token ${session.token}",
            isCollect = if (collected) 1 else 0,
            requestMode = session.reading.mode
        )
        checkAuthentication(session, response.code(), response.body()?.code)
        ensureSession(session)
        check(response.isSuccessful && response.body()?.code == 200) {
            response.body()?.message?.takeIf { it.isNotBlank() } ?: "收藏操作未确认，请刷新后检查"
        }
        check(contains(session, path) == collected) { "云端收藏状态尚未确认，请刷新后检查" }
    }

    override suspend fun removeAll(collectIds: List<Int>): Result<Unit> = result {
        require(collectIds.isNotEmpty() && collectIds.all { it > 0 })
        val ids = collectIds.distinct()
        val session = session()
        ensureSession(session)
        val authorization = "Token ${session.token}"
        val mode = session.reading.mode
        val response = if (mode == RequestMode.OFFICIAL_APP) {
            api.removeNovelFavoritesApp(ids, authorization, requestMode = mode)
        } else {
            api.removeNovelFavoritesWeb(
                ids.mapIndexed { index, id -> "ids[$index]" to id }.toMap(),
                authorization, requestMode = mode
            )
        }
        val body = response.body()
        checkAuthentication(session, response.code(), body?.get("code")?.asInt)
        ensureSession(session)
        check(response.isSuccessful && body?.get("code")?.asInt == 200) {
            body?.let { NovelJson.text(it, "message") }?.takeIf { it.isNotBlank() }
                ?: "取消收藏未确认，请刷新后检查"
        }
        val removed = body.getAsJsonArray("results")?.map { it.asInt }.orEmpty()
        check(removed.containsAll(ids)) { "部分收藏未确认移除，请刷新书架后检查" }
    }

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
