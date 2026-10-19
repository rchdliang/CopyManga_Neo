package com.rchdliang.copymanga_neo.data.repository

import com.google.gson.JsonParser
import com.rchdliang.copymanga_neo.data.local.ComicThemeCache
import com.rchdliang.copymanga_neo.data.local.MemoryComicThemeCache
import com.rchdliang.copymanga_neo.data.remote.api.MangaApiService
import com.rchdliang.copymanga_neo.data.remote.dto.ChapterListResult
import com.rchdliang.copymanga_neo.data.remote.util.ChapterCatalogDecryptor
import com.rchdliang.copymanga_neo.data.remote.util.ChapterDecryptor
import com.rchdliang.copymanga_neo.di.WwwRetrofit
import com.rchdliang.copymanga_neo.domain.model.BrowseHistoryPage
import com.rchdliang.copymanga_neo.domain.model.CachedComicThemes
import com.rchdliang.copymanga_neo.domain.model.ChapterCatalog
import com.rchdliang.copymanga_neo.domain.model.ChapterContent
import com.rchdliang.copymanga_neo.domain.model.ChapterList
import com.rchdliang.copymanga_neo.domain.model.ComicDetail
import com.rchdliang.copymanga_neo.domain.model.ComicPage
import com.rchdliang.copymanga_neo.domain.model.ComicStatus
import com.rchdliang.copymanga_neo.domain.model.ComicSummary
import com.rchdliang.copymanga_neo.domain.model.ComicTopic
import com.rchdliang.copymanga_neo.domain.model.FavoriteItem
import com.rchdliang.copymanga_neo.domain.model.HomeComicsData
import com.rchdliang.copymanga_neo.domain.model.QueryType
import com.rchdliang.copymanga_neo.domain.model.Theme
import com.rchdliang.copymanga_neo.domain.model.TrialContent
import com.rchdliang.copymanga_neo.domain.repository.MangaRepository
import com.rchdliang.copymanga_neo.domain.repository.RiskControlException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds

/**
 * 漫画数据仓库实现
 */
@Singleton
class MangaRepositoryImpl @Inject constructor(
    private val apiService: MangaApiService,
    @param:WwwRetrofit private val wwwApiService: MangaApiService,
    private val themeCache: ComicThemeCache = MemoryComicThemeCache(),
    private val networkSettings: com.rchdliang.copymanga_neo.data.local.NetworkSettingsStore,
    private val officialContent: OfficialAppContentSource,
    private val networkLog: com.rchdliang.copymanga_neo.data.local.LocalNetworkLogStore? = null,
    private val memberWrites: com.rchdliang.copymanga_neo.data.remote.api.MemberWriteApiService,
    private val auth: com.rchdliang.copymanga_neo.data.local.AuthDataStore
) : MangaRepository {

    private fun usesOfficialApp() = networkSettings.settings.value.requestMode ==
            com.rchdliang.copymanga_neo.domain.model.RequestMode.OFFICIAL_APP

    private data class CachedCatalog(val result: Result<ChapterCatalog>, val expiresAt: Long)

    private val catalogMutex = Mutex()
    private val catalogCache = object : LinkedHashMap<String, CachedCatalog>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedCatalog>?): Boolean =
            size > 8
    }

    override suspend fun getChapterCatalog(
        pathWord: String,
        forceRefresh: Boolean
    ): Result<ChapterCatalog> =
        withContext(Dispatchers.IO) {
            catalogMutex.withLock {
                val generation = networkSettings.generation
                val appMode = usesOfficialApp()
                val cacheKey = "$generation:$pathWord"
                if (!forceRefresh) {
                    catalogCache[cacheKey]?.takeIf { it.expiresAt > System.nanoTime() }?.let {
                        return@withLock it.result
                    }
                }
                val result = try {
                    if (appMode) {
                        officialContent.catalog(pathWord)
                    } else {
                        val page = wwwApiService.getComicHtml(pathWord)
                        if (!page.isSuccessful) {
                            page.errorBody()?.close()
                            error("获取漫画网页失败：HTTP ${page.code()}")
                        }
                        val html = page.body()?.use { it.string() } ?: error("漫画网页内容为空")
                        val protocol =
                            com.rchdliang.copymanga_neo.data.remote.util.WwwCatalogProtocol.fromHtml(
                                html
                            )
                        check(generation == networkSettings.generation) { "网络设置已更改，请刷新重试" }
                        val response = wwwApiService.getChapterCatalog(
                            pathWord,
                            com.rchdliang.copymanga_neo.data.remote.CopyMangaNetworkConfig.chapterCatalogHeaders(
                                pathWord,
                                protocol.dnts
                            ),
                            protocol
                        )
                        if (!response.isSuccessful) {
                            response.errorBody()?.close()
                            error("获取 WWW 章节目录失败：HTTP ${response.code()}")
                        }
                        val json = response.body()?.use { it.string() } ?: error("WWW 章节目录为空")
                        Result.success(
                            ChapterCatalogDecryptor.parseResponse(
                                json,
                                pathWord,
                                protocol.key
                            )
                        )
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: RiskControlException) {
                    // 风控由全局闸门管理；不缓存空目录或短期失败，也不回退分页请求。
                    catalogCache.remove(cacheKey)
                    return@withLock Result.failure(e)
                } catch (e: Exception) {
                    if (!appMode) networkLog?.catalogFailure(pathWord, e)
                    android.util.Log.w(
                        "MangaRepository",
                        "WWW 章节目录不可用，将回退 API：${e.message}"
                    )
                    Result.failure(e)
                }
                if (generation != networkSettings.generation) return@withLock Result.failure(
                    Exception("网络设置已更改，请刷新重试")
                )
                if (appMode && result.isFailure) return@withLock result
                // 失败短暂缓存，避免连续阅读或下载时反复等待同一个失败请求；手动刷新不受影响。
                val lifetime = if (result.isSuccess) 300L else 30L
                catalogCache[cacheKey] = CachedCatalog(
                    result,
                    System.nanoTime() + TimeUnit.SECONDS.toNanos(lifetime)
                )
                result
            }
        }

    override val cachedComicThemes = themeCache.entries
    private val themeRetryMutex = Mutex()
    private val themeRequests = com.rchdliang.copymanga_neo.data.remote.ThemeRequestScheduler(
        settings = networkSettings.settings,
        currentSettings = { networkSettings.settings.value }
    )
    private val themeRetryAfter = mutableMapOf<String, Long>()

    private suspend fun cacheThemes(pathWord: String, themes: List<Theme>) {
        themeCache.save(
            pathWord, CachedComicThemes(
                themes.toList(),
                System.currentTimeMillis() + TimeUnit.DAYS.toMillis(7) +
                        Random.nextLong(TimeUnit.DAYS.toMillis(2))
            )
        )
    }

    override suspend fun getComicThemes(pathWord: String): Result<List<Theme>> {
        // 标签过期只影响后台刷新计划，离线或风控时仍可使用旧值。
        cachedComicThemes.first()[pathWord]?.let { return Result.success(it.themes) }
        return refreshComicThemes(pathWord)
    }

    override suspend fun refreshComicThemes(pathWord: String): Result<List<Theme>> =
        themeRequests.withKey(pathWord) {
            if (pathWord.isBlank()) return@withKey Result.failure(IllegalArgumentException("漫画标识为空"))
            suspend fun freshThemes(): List<Theme>? = cachedComicThemes.first()[pathWord]
                ?.takeIf { it.refreshAfterMillis > System.currentTimeMillis() }?.themes
            freshThemes()?.let { return@withKey Result.success(it) }
            val retryAt = themeRetryMutex.withLock { themeRetryAfter[pathWord] ?: 0L }
            if (retryAt > System.nanoTime()) return@withKey Result.failure(Exception("标签请求暂缓，稍后重试"))
            themeRequests.request {
                // 排队期间详情页面可能已写入新标签，不重复消耗请求。
                freshThemes()?.let { return@request Result.success(it) }
                val result = withTimeoutOrNull(8.seconds) { fetchComicThemes(pathWord) }
                    ?: Result.failure(Exception("标签请求超时"))
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                result.exceptionOrNull()?.let { if (it is CancellationException) throw it }
                themeRetryMutex.withLock {
                    if (result.isFailure) themeRetryAfter[pathWord] = System.nanoTime() + TimeUnit.MINUTES.toNanos(15)
                    else themeRetryAfter.remove(pathWord)
                }
                result
            }
        }

    private suspend fun fetchComicThemes(pathWord: String): Result<List<Theme>> {
        return try {
            val response = apiService.getComicDetail(pathWord)
            val body = response.body()
            if (response.code() == 210 || body?.code == 210) {
                return Result.failure(
                    RiskControlException(
                        body?.message ?: "官方暂时限制了当前网络的访问"
                    )
                )
            }
            if (response.isSuccessful && body?.code == 200) {
                val themes = body.results.comicDetail.theme?.map { it.toTheme() }
                    ?: return Result.failure(Exception("漫画详情未返回标签信息"))
                cacheThemes(pathWord, themes)
                Result.success(themes)
            } else {
                Result.failure(Exception(body?.message ?: "读取漫画标签失败"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getHomeComics(): Result<HomeComicsData> = supervisorScope {
        try {
            val homeResponseDeferred = async { apiService.getHomeComics() }
            val recResponseDeferred = async {
                try {
                    apiService.getRecComics(limit = 6, freeType = null)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (e is RiskControlException) throw e
                    android.util.Log.w("MangaRepository", "推荐请求失败", e)
                    null
                }
            }
            val finishResponseDeferred = async {
                try {
                    apiService.getFinishedComics(limit = 6)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (e is RiskControlException) throw e
                    android.util.Log.w("MangaRepository", "完结请求失败", e)
                    null
                }
            }

            val homeResponse = homeResponseDeferred.await()
            val recResponse = recResponseDeferred.await()
            val finishResponse = finishResponseDeferred.await()

            if (!homeResponse.isSuccessful || homeResponse.body()?.code != 200) {
                val errorBody = homeResponse.errorBody()?.string()
                android.util.Log.e("MangaRepository", "Error response: $errorBody")
                return@supervisorScope Result.failure(Exception("HTTP ${homeResponse.code()}: ${homeResponse.message()}"))
            }

            val body = homeResponse.body()
            android.util.Log.d("MangaRepository", "Response body is null: ${body == null}")

            val home = body?.results
                ?: return@supervisorScope Result.failure(Exception("Response body is null"))

            val recPage = recResponse?.takeIf { it.isSuccessful && it.body()?.code == 200 }
                ?.body()?.results ?: home.recComics
            val recComics = recPage?.list.orEmpty().map { it.comic }
            val hotComics = home.hotComics.orEmpty().map { it.comic }
            val newComics = home.newComics.orEmpty().map { it.comic }
            val finishPage = finishResponse?.takeIf { it.isSuccessful && it.body()?.code == 200 }
                ?.body()?.results
            val finishComics = finishPage?.list ?: home.finishComics?.list.orEmpty()
            val rankComics = home.rankDayComics?.list.orEmpty().map { it.comic }
            val topics =
                (home.topicsList ?: home.topics)?.list.orEmpty().filter { it.type == 1 }.map {
                    ComicTopic(it.pathWord, it.title, it.cover, it.brief)
                }

            android.util.Log.d(
                "MangaRepository",
                "Loaded ${recComics.size} recComics, ${hotComics.size} hotComics, ${newComics.size} newComics, ${finishComics.size} finishComics"
            )

            Result.success(
                HomeComicsData(
                    recComics = recComics.map { it.toSummary() },
                    hotComics = hotComics.map { it.toSummary() },
                    newComics = newComics.map { it.toSummary() },
                    finishComics = finishComics.map { it.toSummary() },
                    rankComics = rankComics.map { it.toSummary() },
                    topics = topics,
                    recNextOffset = recPage?.let { (it.offset + it.list.size).takeIf { next -> it.list.isNotEmpty() && next < it.total } },
                    newNextOffset = newComics.size.takeIf { it > 0 },
                    finishNextOffset = finishPage?.let { (it.offset + it.list.size).takeIf { next -> it.list.isNotEmpty() && next < it.total } }
                        ?: if (finishPage == null) home.finishComics?.let { (it.offset + it.list.size).takeIf { next -> it.list.isNotEmpty() && next < it.total } } else null,
                    rankNextOffset = home.rankDayComics?.let { (it.offset + it.list.size).takeIf { next -> it.list.isNotEmpty() && next < it.total } }
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.e("MangaRepository", "Exception loading comics: ${e.message}", e)
            Result.failure(e)
        }
    }

    override suspend fun getComicDetail(pathWord: String): Result<ComicDetail> {
        return try {
            val response = apiService.getComicDetail(pathWord)
            val body = response.body()

            val code = body?.code ?: response.code()
            if (code == 210) {
                val errorMsg = body?.message ?: response.errorBody()?.string()?.let {
                    try {
                        JsonParser.parseString(it).asJsonObject.get("message")?.asString
                    } catch (_: Exception) {
                        null
                    }
                } ?: "未知风控原因"
                return Result.failure(Exception("code 210: $errorMsg"))
            }

            if (response.isSuccessful && body?.code == 200) {
                val detail = body.results.toDetail()
                detail.comic.theme?.let { cacheThemes(pathWord, it) }
                Result.success(detail)
            } else {
                val errorCode = body?.code ?: response.code()
                val errorMessage = body?.message ?: response.message()
                android.util.Log.e(
                    "MangaRepository",
                    "获取详情失败: Code=$errorCode, Message=$errorMessage"
                )
                Result.failure(Exception(errorMessage))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.e("MangaRepository", "获取详情异常: ${e.message}", e)
            Result.failure(e)
        }
    }

    override suspend fun getChapters(
        pathWord: String,
        group: String,
        offset: Int,
        limit: Int
    ): Result<ChapterList> {
        return try {
            val response = apiService.getChapters(pathWord, group, limit = limit, offset = offset)
            if (response.isSuccessful && response.body()?.code == 200) {
//                android.util.Log.i("MangaRepositoryImpl", "Chapter[0]=" + response.body()!!.results.component1()[0].toString())
                Result.success(
                    (response.body()?.results ?: ChapterListResult(
                        emptyList(),
                        0,
                        20,
                        0
                    )).toList()
                )
            } else {
                Result.failure(Exception(response.body()?.message ?: "获取章节列表失败"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getChapterInfo(
        comicPathWord: String, chapterUuid: String, group: String
    ): Result<com.rchdliang.copymanga_neo.domain.model.ChapterInfo> {
        val catalogResult = getChapterCatalog(comicPathWord)
        (catalogResult.exceptionOrNull() as? RiskControlException)?.let { return Result.failure(it) }
        catalogResult.getOrNull()?.let { catalog ->
            val resolvedGroup = catalog.findGroup(chapterUuid) ?: group
            catalog.chaptersByGroup[resolvedGroup]?.firstOrNull { it.uuid == chapterUuid }?.let {
                return Result.success(it)
            }
        }
        var offset = 0
        val seen = mutableSetOf<String>()
        do {
            val result = getChapters(comicPathWord, group, offset = offset, limit = 100)
            val page = result.fold({ it }, { return Result.failure(it) })
            page.list.firstOrNull { it.uuid == chapterUuid }?.let {
                return Result.success(it)
            }
            // 使用实际返回数量翻页，并防止服务端重复页导致无限循环。
            val newCount = page.list.count { seen.add(it.uuid) }
            if (page.list.isEmpty() || newCount == 0) break
            offset += page.list.size
        } while (offset < page.total)
        return Result.failure(NoSuchElementException("章节不在当前分组中"))
    }

    override suspend fun trialChapterContent(
        comicPathWord: String,
        chapterUuid: String
    ): Result<TrialContent> {
        return try {
            val response = apiService.getTrialContent(comicPathWord, chapterUuid)
            val body = response.body()
            if (response.isSuccessful && body != null && body.code == 200) {
                Result.success(body.results.toContent())
            } else {
                android.util.Log.w(
                    "MangaRepository",
                    "试看请求失败: code=${response.code()}, msg=${response.body()?.message}"
                )
                Result.failure(
                    Exception(response.body()?.message ?: "试看请求失败")
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.w("MangaRepository", "试看请求异常: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun getChapterContent(
        comicPathWord: String,
        chapterUuid: String,
        group: String
    ): Result<ChapterContent> {
        return try {
            if (usesOfficialApp()) return officialContent.chapter(comicPathWord, chapterUuid)
            // 先从章节列表 API 获取上下章 UUID 及章节名称
            var prevUuid: String? = null
            var nextUuid: String? = null
            var chapterName: String? = null
            val metadata = getChapterInfo(comicPathWord, chapterUuid, group)
            (metadata.exceptionOrNull() as? RiskControlException)?.let { return Result.failure(it) }
            metadata.onSuccess { chapter ->
                prevUuid = chapter.prevChapter
                nextUuid = chapter.nextChapter
                chapterName = chapter.name
            }

            val response = wwwApiService.getChapterHtml(comicPathWord, chapterUuid)
            val code = response.code()
            if (code == 210) {
                val bodyString = response.errorBody()?.string() ?: ""
                val message = try {
                    val json = JsonParser.parseString(bodyString).asJsonObject
                    json.get("message")?.asString ?: "未知风控原因"
                } catch (_: Exception) {
                    bodyString.take(100)
                }
                return Result.failure(Exception("code 210: $message"))
            }

            if (response.isSuccessful) {
                val html = response.body()?.string() ?: ""
                val result = ChapterDecryptor.decryptChapterImages(html)
                result
                    .onSuccess { imageUrls ->
                        android.util.Log.i(
                            "MangaRepository",
                            "章节内容解析成功: ${imageUrls.size} 张图片, prev=$prevUuid, next=$nextUuid"
                        )
                    }
                    .onFailure {
                        android.util.Log.e(
                            "MangaRepository",
                            "解析章节内容失败(${it.message})，HTML 前 500 字符: ${html.take(500)}"
                        )
                    }
                result.map { imageUrls ->
                    ChapterContent(
                        imageUrls,
                        prevUuid,
                        nextUuid,
                        chapterName ?: "",
                        navigationResolved = metadata.isSuccess
                    )
                }
            } else {
                Result.failure(Exception("获取章节页面失败: ${response.code()}"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getChapterDownloadContent(
        comicPathWord: String, chapterUuid: String, group: String
    ): Result<ChapterContent> =
        if (usesOfficialApp()) officialContent.download(comicPathWord, chapterUuid)
        else getChapterContent(comicPathWord, chapterUuid, group)

    override suspend fun getRecComics(limit: Int, offset: Int): Result<List<ComicSummary>> {
        return try {
            val response = apiService.getRecComics(limit = limit, offset = offset, freeType = null)
            if (response.isSuccessful && response.body()?.code == 200) {
                Result.success(response.body()?.results?.list?.map { it.comic.toSummary() }
                    ?: emptyList())
            } else {
                Result.failure(Exception(response.body()?.message ?: "获取推荐失败"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getThemeComics(theme: String, offset: Int): Result<ComicPage> {
        return try {
            val response = apiService.getThemeComics(theme = theme, offset = offset)
            val body = response.body()
            if (response.isSuccessful && body?.code == 200) {
                val result = body.results
                // 抓包中 limit=21 但实际只有 20 项，不能用列表长度判断末页。
                val nextOffset = result.offset + result.limit
                Result.success(
                    ComicPage(
                        comics = result.list.map { it.toSummary() },
                        nextOffset = nextOffset.takeIf {
                            result.list.isNotEmpty() && it > offset && it < result.total
                        }
                    )
                )
            } else {
                Result.failure(Exception(body?.message ?: "获取题材漫画失败"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun searchComics(
        query: String,
        page: Int,
        queryType: QueryType?
    ): Result<List<ComicSummary>> {
        return searchComicPage(query, page, queryType).map { it.comics }
    }

    override suspend fun searchComicPage(
        query: String, page: Int, queryType: QueryType?, offset: Int?
    ): Result<ComicPage> {
        return try {
            require(page >= 1)
            val pageSize = if (usesOfficialApp()) 18 else 20
            val requestedOffset = offset ?: ((page - 1) * pageSize)
            require(requestedOffset >= 0)
            val response = apiService.search(
                query = query,
                limit = pageSize,
                offset = requestedOffset,
                queryType = queryType
            )
            val body = response.body()
            if (response.isSuccessful && body?.code == 200) {
                val result = body.results
                check(result.offset == requestedOffset && result.limit > 0 && result.total >= 0) { "搜索分页数据无效" }
                val next = result.offset.toLong() + result.limit
                check(next <= Int.MAX_VALUE) { "搜索分页位置无效" }
                Result.success(
                    ComicPage(
                        result.list.map { it.toSummary() },
                        next.toInt().takeIf { result.list.isNotEmpty() && it < result.total },
                        result.total
                    )
                )
            } else {
                Result.failure(Exception(body?.message ?: "搜索失败"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getFavoriteComics(): Result<List<FavoriteItem>> {
        return try {
            val favorites = linkedMapOf<String, FavoriteItem>()
            var offset = 0
            while (true) {
                val response = apiService.getFavoriteComics(
                    offset = offset, limit = if (usesOfficialApp()) 18 else 21
                )
                val body = response.body()
                if (!response.isSuccessful || body?.code != 200) {
                    val message = when (val code = response.code()) {
                        401 -> "401 未登录，请先登录账号"
                        210 -> "210 触发风控，请稍后再试"
                        502 -> "502 服务器错误，请稍后重试"
                        503 -> "503 服务不可用，请稍后重试"
                        504 -> "504 服务器响应超时"
                        else -> body?.message ?: "获取书架失败 (HTTP $code)"
                    }
                    return Result.failure(Exception(message))
                }
                val page = body.results
                if (page.list.isEmpty()) {
                    if (offset < page.total) {
                        return Result.failure(Exception("书架分页数据不完整，请重试"))
                    }
                    break
                }
                val previousSize = favorites.size
                page.list.forEach { item ->
                    val favorite = item.toFavoriteItem()
                    favorites[favorite.comic.pathWord] = favorite
                }
                // 服务端可能过滤条目，按返回的分页跨度推进，不能按 list.size 判断结束。
                val nextOffset = page.offset + page.limit
                if (page.offset != offset || nextOffset <= offset || favorites.size == previousSize) {
                    return Result.failure(Exception("书架分页未前进，请重试"))
                }
                if (nextOffset >= page.total) break
                offset = nextOffset
            }
            Result.success(favorites.values.toList())
        } catch (e: CancellationException) {
            throw e
        } catch (_: java.net.SocketTimeoutException) {
            Result.failure(Exception("请求超时，请检查网络后重试"))
        } catch (_: java.net.UnknownHostException) {
            Result.failure(Exception("无法连接服务器，请检查网络"))
        } catch (_: javax.net.ssl.SSLException) {
            Result.failure(Exception("网络连接异常 (SSL)，请检查网络"))
        } catch (e: Exception) {
            Result.failure(Exception("获取书架失败：${e.message}"))
        }
    }

    override suspend fun getBrowseHistory(offset: Int): Result<BrowseHistoryPage> {
        return try {
            val response = apiService.getBrowseHistory(
                offset = offset, limit = if (usesOfficialApp()) 18 else 20,
                freeType = if (usesOfficialApp()) null else 1
            )
            val body = response.body()
            val page = body?.results
            if (response.isSuccessful && body?.code == 200 && page != null) {
                val nextOffset = page.offset + page.limit
                Result.success(
                    BrowseHistoryPage(
                        items = page.list.map { it.toHistoryItem() },
                        nextOffset = nextOffset.takeIf {
                            page.list.isNotEmpty() && page.offset == offset &&
                                    it > offset && it < page.total
                        }
                    )
                )
            } else {
                Result.failure(
                    Exception(
                        body?.message ?: "获取浏览记录失败 (HTTP ${response.code()})"
                    )
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun clearBrowseHistory(): Result<Unit> {
        if (usesOfficialApp()) return appMemberWrite("清空浏览记录") {
            memberWrites.clearBrowseHistory(it)
        }
        return try {
            val response = apiService.clearBrowseHistory()
            val body = response.body()
            if (response.isSuccessful && body?.code == 200) {
                Result.success(Unit)
            } else {
                Result.failure(
                    Exception(
                        body?.message ?: "清空浏览记录失败 (HTTP ${response.code()})"
                    )
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun queryComicStatus(pathWord: String): Result<ComicStatus> {
        return try {
            val response = apiService.queryComicStatus(pathWord)
            if (response.isSuccessful && response.body()?.code == 200) {
                val results = response.body()?.results
                if (results != null) {
                    Result.success(results.toStatus())
                } else {
                    Result.failure(Exception("查询状态结果为空"))
                }
            } else {
                Result.failure(
                    Exception(response.body()?.message ?: "查询状态失败")
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun addFavorite(comicId: String): Result<Unit> {
        if (usesOfficialApp()) {
            if (comicId.isBlank()) return Result.failure(IllegalArgumentException("漫画标识无效"))
            return appMemberWrite("添加收藏") { memberWrites.addFavorite(comicId, it) }
        }
        return try {
            val response = apiService.addFavorite(comicId)
            if (response.isSuccessful && response.body()?.code == 200) {
                Result.success(Unit)
            } else {
                Result.failure(
                    Exception(response.body()?.message ?: "添加收藏失败")
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun removeFavorite(collectId: Int): Result<Unit> {
        if (usesOfficialApp()) return removeFavorites(listOf(collectId))
        return try {
            val response = apiService.removeFavorite(collectId)
            if (response.isSuccessful && response.body()?.code == 200) {
                Result.success(Unit)
            } else {
                Result.failure(
                    Exception(response.body()?.message ?: "移除收藏失败")
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun removeFavorites(collectIds: List<Int>): Result<Unit> {
        if (usesOfficialApp()) {
            if (collectIds.isEmpty()) return Result.success(Unit)
            if (collectIds.any { it <= 0 }) return Result.failure(IllegalArgumentException("收藏标识无效"))
            return appMemberWrite("批量移除收藏") {
                memberWrites.removeFavorites(collectIds.distinct(), it)
            }
        }
        return try {
            // 拷貝漫畫 API 期望 ids[0]=xxx&ids[1]=xxx 的索引数组形式
            val fieldMap = collectIds.mapIndexed { index, id -> "ids[$index]" to id }.toMap()
            val response = apiService.removeFavorites(fieldMap)
            if (response.isSuccessful && response.body()?.code == 200) {
                Result.success(Unit)
            } else {
                Result.failure(
                    Exception(response.body()?.message ?: "批量移除收藏失败")
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** 固定写入账号；成功空 results 合法，错误正文仍保留服务端业务提示。 */
    private suspend fun appMemberWrite(
        operation: String,
        request: suspend (String) -> retrofit2.Response<com.rchdliang.copymanga_neo.data.remote.dto.SimpleResponse>
    ): Result<Unit> {
        return try {
            val token = auth.getTokenSync()?.takeIf { it.isNotBlank() }
                ?: return Result.failure(IllegalStateException("请先登录后操作"))
            val authGeneration = auth.getAuthGeneration()
            val networkGeneration = networkSettings.generation
            val response = request("Token $token")
            val body = response.body()
            if (response.code() == 401 || body?.code == 401) {
                networkSettings.ifCurrent(networkGeneration) {
                    auth.clearAuthSyncIfTokenMatches(
                        token,
                        com.rchdliang.copymanga_neo.domain.model.RequestMode.OFFICIAL_APP,
                        authGeneration
                    )
                }
            }
            if (!response.isSuccessful || body?.code != 200) {
                val error = response.errorBody()?.use { it.string() }
                val message = body?.message ?: runCatching {
                    JsonParser.parseString(error).asJsonObject.get("message")
                        ?.takeUnless { it.isJsonNull }?.asString
                }.getOrNull()
                Result.failure(Exception(message ?: "${operation}失败（HTTP ${response.code()}）"))
            } else {
                check(
                    auth.getAuthGeneration() == authGeneration && auth.getTokenSync() == token &&
                            networkSettings.generation == networkGeneration
                ) {
                    "会话或网络设置已变化，请刷新确认操作结果"
                }
                Result.success(Unit)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
