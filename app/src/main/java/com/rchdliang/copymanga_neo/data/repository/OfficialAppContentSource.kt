package com.rchdliang.copymanga_neo.data.repository

import com.rchdliang.copymanga_neo.data.local.AuthDataStore
import com.rchdliang.copymanga_neo.data.local.NetworkSettingsStore
import com.rchdliang.copymanga_neo.data.remote.api.MangaApiService
import com.rchdliang.copymanga_neo.data.remote.api.OfficialDownloadApiService
import com.rchdliang.copymanga_neo.data.remote.dto.OfficialChapterResult
import com.rchdliang.copymanga_neo.domain.model.ChapterCatalog
import com.rchdliang.copymanga_neo.domain.model.ChapterContent
import com.rchdliang.copymanga_neo.domain.model.ChapterGroup
import com.rchdliang.copymanga_neo.domain.model.ChapterInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** App 目录与阅读独立实现，失败直接交给界面，不隐式切回 WWW。 */
@Singleton
class OfficialAppContentSource @Inject constructor(
    private val api: MangaApiService,
    private val settings: NetworkSettingsStore,
    private val downloads: OfficialDownloadApiService,
    private val auth: AuthDataStore
) {
    private data class DownloadKey(
        val networkGeneration: Long,
        val authGeneration: Long,
        val pathWord: String,
        val uuid: String
    )

    private val downloadMutex = Mutex()
    private var exhaustedSession: Pair<Long, Long>? = null
    private var checkedQuotaSession: Pair<Long, Long>? = null
    private var quotaCheckedAt = 0L
    private val downloadContents =
        object : LinkedHashMap<DownloadKey, ChapterContent>(32, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<DownloadKey, ChapterContent>?) =
                size > 32
        }

    suspend fun catalog(pathWord: String): Result<ChapterCatalog> = capture {
        val generation = settings.generation
        fun checkGeneration() {
            if (settings.generation != generation) throw IOException("网络设置已更改，请刷新重试")
        }

        val response = api.getComicDetail(pathWord)
        checkGeneration()
        val body = response.body()
        if (!response.isSuccessful || body?.code != 200) {
            response.errorBody()?.close()
            throw IOException(body?.message ?: "App 目录分组获取失败")
        }
        val detail = body.results
        if (detail.isBanned || detail.isLock) throw IOException("漫画暂不可访问")
        val groups = detail.groups.values.map { ChapterGroup(it.pathWord, it.total, it.name) }
            ?: throw IOException("App 未返回章节分组")
        val chapters = linkedMapOf<String, List<ChapterInfo>>()
        for (group in groups) {
            val collected = mutableListOf<ChapterInfo>()
            val seen = mutableSetOf<String>()
            var total = group.total
            var offset = 0
            while (offset < total) {
                checkGeneration()
                if (offset >= 50_000) throw IOException("章节目录过大，请稍后重试")
                val pageResponse =
                    api.getChapters(pathWord, group.pathWord, limit = 100, offset = offset)
                checkGeneration()
                val pageBody = pageResponse.body()
                if (!pageResponse.isSuccessful || pageBody?.code != 200) {
                    pageResponse.errorBody()?.close()
                    throw IOException(pageBody?.message ?: "App 章节目录获取失败")
                }
                val page = pageBody.results
                if (page.list.isNullOrEmpty() || page.list.any { !seen.add(it.uuid) }) {
                    throw IOException("App 章节目录为空或重复，未保存不完整目录")
                }
                collected.addAll(page.toList().list)
                offset += page.list.size
                total = maxOf(total, page.total)
            }
            chapters[group.pathWord] = collected
        }
        checkGeneration()
        ChapterCatalog(groups, chapters)
    }

    suspend fun chapter(pathWord: String, uuid: String): Result<ChapterContent> = capture {
        val response = api.getOfficialChapter(pathWord, uuid)
        val body = response.body()
        if (!response.isSuccessful || body?.code != 200) {
            response.errorBody()?.close()
            throw IOException(body?.message ?: "App 章节内容获取失败")
        }
        val result = body.results ?: throw IOException("App 章节结果为空")
        parseChapter(result, uuid)
    }

    /** 同一会话内复用成功授权的图片列表，图片重试不会重新扣额度。 */
    suspend fun download(pathWord: String, uuid: String): Result<ChapterContent> = capture {
        downloadMutex.withLock {
            val token = auth.getTokenSync()?.takeIf { it.isNotBlank() }
                ?: throw IOException("请先登录后下载")
            val key = DownloadKey(settings.generation, auth.getAuthGeneration(), pathWord, uuid)
            fun checkSession() {
                if (settings.generation != key.networkGeneration ||
                    auth.getAuthGeneration() != key.authGeneration || auth.getTokenSync() != token
                ) throw IOException("网络设置或登录状态已更改，请重新下载")
            }
            downloadContents.keys.removeAll {
                it.networkGeneration != key.networkGeneration || it.authGeneration != key.authGeneration
            }
            checkSession()
            downloadContents[key]?.let { return@withLock it }
            val session = key.networkGeneration to key.authGeneration
            if (checkedQuotaSession != session ||
                android.os.SystemClock.elapsedRealtime() - quotaCheckedAt >= 5 * 60 * 1000L
            ) {
                val infoResponse = api.getMemberInfoWithCredential("Token $token")
                checkSession()
                if (infoResponse.isSuccessful && infoResponse.body()?.code == 200) {
                    checkedQuotaSession = session
                    quotaCheckedAt = android.os.SystemClock.elapsedRealtime()
                    val remaining = infoResponse.body()?.results?.dayDownloads
                    if (remaining == 0) exhaustedSession = session
                    else if (remaining != null && remaining > 0) exhaustedSession = null
                } else infoResponse.errorBody()?.close()
            }
            suspend fun readingContent(): ChapterContent {
                val reading = api.getOfficialChapter(
                    pathWord, uuid,
                    headers = com.rchdliang.copymanga_neo.data.remote.CopyMangaNetworkConfig.noAuthApiHeaders(),
                    authorization = "Token $token"
                )
                checkSession()
                val readingBody = reading.body()
                if (!reading.isSuccessful || readingBody?.code != 200) {
                    reading.errorBody()?.close()
                    throw IOException(readingBody?.message ?: "阅读接口获取下载内容失败")
                }
                val content =
                    parseChapter(readingBody.results ?: throw IOException("阅读章节结果为空"), uuid)
                downloadContents[key] = content
                return content
            }
            if (exhaustedSession == session) return@withLock readingContent()
            val response = downloads.chapter(pathWord, uuid, "Token $token")
            checkSession()
            val body = response.body()
            if (!response.isSuccessful || body?.code != 200) {
                val error = response.errorBody()?.use { it.string() }
                val message = body?.message ?: runCatching {
                    com.google.gson.JsonParser.parseString(error).asJsonObject.get("message")?.asString
                }.getOrNull()
                if (response.code() != 401 && body?.code != 401 &&
                    com.rchdliang.copymanga_neo.domain.model.DownloadQuotaPolicy.isExhausted(message)
                ) {
                    exhaustedSession = session
                    return@withLock readingContent()
                }
                throw IOException(
                    message ?: "下载授权失败（HTTP ${response.code()}），未自动重试"
                )
            }
            val result = body.results ?: throw IOException("下载授权结果为空，未自动重试")
            if (result.isLogin != true) throw IOException("请先登录后下载")
            val content = parseChapter(result, uuid)
            if (result.dayDownloads == 0) exhaustedSession = session
            checkSession()
            downloadContents[key] = content
            content
        }
    }

    /** 阅读和下载复用页序与完整性校验，避免按不同次序保存图片。 */
    private fun parseChapter(result: OfficialChapterResult, uuid: String): ChapterContent {
        if (result.isLock || result.isBanned) throw IOException("章节暂不可阅读")
        val chapter = result.chapter ?: throw IOException("App 未返回章节内容")
        if (chapter.uuid != uuid) throw IOException("App 返回的章节与请求不一致")
        val contents = chapter.contents.orEmpty()
        val words = chapter.words ?: throw IOException("App 未返回章节图片页序")
        if (words.size != contents.size || words.any { it < 0 } || words.distinct().size != words.size) {
            throw IOException("App 章节图片页序无效，未保存不完整章节")
        }
        // words[i] 是 contents[i] 的页序，配对后排序；不能作为 contents 的数组下标。
        val urls = words.zip(contents).sortedBy { it.first }.map { (_, item) ->
            val url = item.url?.toHttpUrlOrNull() ?: throw IOException("App 图片地址无效")
            if (url.username.isNotEmpty() || url.password.isNotEmpty()) throw IOException("App 图片地址无效")
            url.toString()
        }.orEmpty()
        if (urls.isEmpty() || chapter.size?.let { it > 0 && urls.size != it } == true) {
            throw IOException("App 返回的章节图片为空或不完整，未切换网页路径")
        }
        return ChapterContent(urls, chapter.prev, chapter.next, chapter.name.orEmpty())
    }

    private suspend fun <T> capture(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }
}
