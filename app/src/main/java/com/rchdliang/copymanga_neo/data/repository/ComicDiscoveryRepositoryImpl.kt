package com.rchdliang.copymanga_neo.data.repository

import com.rchdliang.copymanga_neo.data.local.NetworkSettingsStore
import com.rchdliang.copymanga_neo.data.remote.api.MangaApiService
import com.rchdliang.copymanga_neo.domain.model.ComicFilterOption
import com.rchdliang.copymanga_neo.domain.model.ComicFilterOptions
import com.rchdliang.copymanga_neo.domain.model.ComicPage
import com.rchdliang.copymanga_neo.domain.model.ComicTopic
import com.rchdliang.copymanga_neo.domain.model.ComicTopicPage
import com.rchdliang.copymanga_neo.domain.model.RequestMode
import com.rchdliang.copymanga_neo.domain.repository.ComicDiscoveryRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

/** 将 HAR 已确认的漫画发现接口转换为领域模型。 */
@Singleton
class ComicDiscoveryRepositoryImpl @Inject constructor(
    private val api: MangaApiService,
    private val networkSettings: NetworkSettingsStore,
    private val themeCatalog: com.rchdliang.copymanga_neo.data.local.ComicThemeCatalogStore
) : ComicDiscoveryRepository {
    private val filterMutation = Mutex()

    @Volatile
    private var extraOptions = com.rchdliang.copymanga_neo.domain.model.ComicFilterDefaults.options
    override suspend fun getRankings(
        period: String,
        audience: String,
        offset: Int,
        limit: Int
    ): Result<ComicPage> = result {
        require(
            offset >= 0 && limit in 1..21 && period in setOf("day", "week", "month", "total") &&
                    audience in setOf("male", "female")
        ) {
            "榜单参数无效"
        }
        val response = api.getComicRankings(
            period = period,
            audience = audience,
            offset = offset,
            limit = pageSize(limit)
        )
        val body = response.body()
        val page = checked(response, body?.code, body?.message, body?.results)
        ComicPage(
            page.list.map { it.comic.toSummary() },
            nextOffset(offset, page.offset, page.list.size, page.total, page.limit)
        )
    }

    override suspend fun getNewest(offset: Int, limit: Int): Result<ComicPage> = result {
        require(offset >= 0 && limit in 1..21)
        val response = api.getNewestComics(
            offset = offset,
            limit = pageSize(limit),
            update = true.takeIf { offset == 0 && !usesOfficialApp() },
            date = "".takeIf { usesOfficialApp() })
        val body = response.body()
        val page = checked(response, body?.code, body?.message, body?.results)
        ComicPage(
            page.list.map { it.comic.toSummary() },
            nextOffset(
                offset,
                page.offset,
                page.list.size,
                page.total,
                page.limit ?: page.list.size
            )
        )
    }

    override suspend fun getRecommendations(offset: Int, limit: Int): Result<ComicPage> = result {
        require(offset >= 0 && limit in 1..21)
        val response = api.getRecComics(limit = pageSize(limit), offset = offset, freeType = null)
        val body = response.body()
        val page = checked(response, body?.code, body?.message, body?.results)
        ComicPage(
            page.list.map { it.comic.toSummary() },
            nextOffset(offset, page.offset, page.list.size, page.total, page.limit)
        )
    }

    override suspend fun getFinished(offset: Int, limit: Int): Result<ComicPage> = result {
        require(offset >= 0 && limit in 1..21)
        val response = api.getFinishedComics(offset = offset, limit = limit)
        val body = response.body()
        val page = checked(response, body?.code, body?.message, body?.results)
        ComicPage(
            page.list.map { it.toSummary() },
            nextOffset(offset, page.offset, page.list.size, page.total, page.limit)
        )
    }

    override suspend fun getTopics(offset: Int): Result<ComicTopicPage> = result {
        require(offset >= 0)
        val appMode = usesOfficialApp()
        val generation = networkSettings.generation
        var requestedOffset = offset
        var topics: List<ComicTopic>
        var next: Int?
        do {
            check(networkSettings.generation == generation) { "网络设置已更改，请刷新重试" }
            val response = api.getComicTopics(
                type = if (appMode) 11 else 1,
                limit = if (appMode) 18 else 20,
                offset = requestedOffset
            )
            val body = response.body()
            val page = checked(response, body?.code, body?.message, body?.results)
            check(networkSettings.generation == generation) { "网络设置已更改，请刷新重试" }
            // 先按原始分页窗口推进；全是写真的一页继续读取，避免空首屏停止加载。
            next = nextOffset(requestedOffset, page.offset, page.list.size, page.total, page.limit)
            topics = page.list.filter { it.type == 1 }
                .map { ComicTopic(it.pathWord, it.title, it.cover, it.brief) }
            next?.let { requestedOffset = it }
        } while (topics.isEmpty() && next != null)
        ComicTopicPage(topics, next)
    }

    override suspend fun getTopic(pathWord: String): Result<ComicTopic> = result {
        require(pathWord.isNotBlank())
        val response = api.getComicTopic(
            pathWord, limit = if (usesOfficialApp()) null else "",
            offset = if (usesOfficialApp()) null else ""
        )
        val body = response.body()
        val topic = checked(response, body?.code, body?.message, body?.results)
        check(topic.type == null || topic.type == 1) { "该专题不是漫画专题" }
        ComicTopic(topic.pathWord, topic.title, topic.cover, topic.brief ?: topic.intro)
    }

    override suspend fun getTopicComics(pathWord: String, offset: Int): Result<ComicPage> = result {
        require(pathWord.isNotBlank() && offset >= 0)
        val response = api.getComicTopicContents(
            pathWord, offset = offset, type = 1.takeIf { usesOfficialApp() }
        )
        val body = response.body()
        val page = checked(response, body?.code, body?.message, body?.results)
        ComicPage(
            page.list.map { it.toSummary() },
            nextOffset(offset, page.offset, page.list.size, page.total, page.limit)
        )
    }

    override fun cachedFilterOptions(): ComicFilterOptions =
        extraOptions.copy(themes = themeCatalog.themes)

    override suspend fun getFilterOptions(): Result<ComicFilterOptions> = filterMutation.withLock {
        result {
            supervisorScope {
                val filtersRequest = async {
                    result {
                        val response = api.getComicFilterTags()
                        val body = response.body()
                        val options = checked(response, body?.code, body?.message, body?.results)
                        extraOptions.copy(
                            tops = options.top.orEmpty()
                                .map { ComicFilterOption(it.name, it.pathWord) }
                                .ifEmpty { extraOptions.tops },
                            orderings = options.ordering.orEmpty()
                                .filter { it.pathWord in setOf("datetime_updated", "popular") }
                                .map { ComicFilterOption(it.name, "-${it.pathWord}") }
                                .ifEmpty { extraOptions.orderings }
                        )
                    }
                }
                val themes = mutableListOf<ComicFilterOption>()
                var offset = 0
                do {
                    val response = api.getComicThemeCounts(offset = offset)
                    val body = response.body()
                    val page = checked(response, body?.code, body?.message, body?.results)
                    check(page.list.isNotEmpty() && page.total > 0) { "标签接口没有返回完整列表" }
                    themes += page.list.map { tag ->
                        check(
                            tag.name.isNotBlank() && tag.pathWord.isNotBlank() &&
                                    tag.count != null && tag.count >= 0
                        ) { "标签数据无效" }
                        ComicFilterOption(tag.name, tag.pathWord, tag.count)
                    }
                    val next =
                        nextOffset(offset, page.offset, page.list.size, page.total, page.limit)
                    if (next == null) break
                    offset = next
                } while (true)
                filtersRequest.await().fold(
                    onSuccess = { extraOptions = it },
                    onFailure = {
                        if (it is com.rchdliang.copymanga_neo.domain.repository.RiskControlException) throw it
                        android.util.Log.w("ComicThemes", "类型和排序暂不可用，保留已有筛选项", it)
                    }
                )
                themeCatalog.save(themes.distinctBy { it.pathWord })
                cachedFilterOptions()
            }
        }
    }

    override suspend fun filterComics(
        theme: String?,
        top: String?,
        ordering: String,
        offset: Int
    ): Result<ComicPage> = result {
        require(
            offset >= 0 && (top == null || top in setOf("japan", "korea", "west", "finish")) &&
                    ordering in setOf(
                "-datetime_updated",
                "-popular",
                "datetime_updated",
                "popular"
            )
        ) {
            "该筛选组合没有抓包证据"
        }
        val response =
            api.browseComics(
                theme = theme ?: "".takeIf { usesOfficialApp() },
                top = top ?: "".takeIf { usesOfficialApp() },
                ordering = ordering, offset = offset, limit = pageSize(21)
            )
        val body = response.body()
        val page = checked(response, body?.code, body?.message, body?.results)
        ComicPage(
            page.list.map { it.toSummary() },
            nextOffset(offset, page.offset, page.list.size, page.total, page.limit),
            page.total
        )
    }

    override suspend fun getOriginalWorkComics(
        pathWord: String, ordering: String, offset: Int
    ): Result<ComicPage> = result {
        require(pathWord.isNotBlank() && offset >= 0 &&
            ordering in setOf("-datetime_updated", "datetime_updated", "-popular", "popular")) {
            "原作筛选参数无效"
        }
        // 此入口两种模式均为 21 项；App 的 platform 与身份头由现有网络层添加。
        val response = api.getOriginalWorkComics(pathWord, ordering, offset)
        val body = response.body()
        val page = checked(response, body?.code, body?.message, body?.results)
        ComicPage(page.list.map { it.toSummary() },
            nextOffset(offset, page.offset, page.list.size, page.total, page.limit), page.total)
    }

    private fun usesOfficialApp() =
        networkSettings.settings.value.requestMode == RequestMode.OFFICIAL_APP

    /** App 列表每页 18 项；完结和专题内容沿用各自的分页窗口。 */
    private fun pageSize(requested: Int) =
        if (usesOfficialApp()) requested.coerceAtMost(18) else requested

    private suspend fun <T> result(block: suspend () -> T): Result<T> =
        withContext(Dispatchers.IO) {
            try {
                val generation = networkSettings.generation
                val value = block()
                check(networkSettings.generation == generation) { "网络设置已更改，请刷新重试" }
                Result.success(value)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private fun <T> checked(response: Response<*>, code: Int?, message: String?, value: T?): T {
        if (!response.isSuccessful || code != 200) {
            throw IllegalStateException(message?.takeIf { it.isNotBlank() }
                ?: "请求失败（HTTP ${response.code()}，code=$code）")
        }
        return value ?: throw IllegalStateException("接口没有返回列表数据")
    }

    /** 有 limit 的响应按服务端分页窗口推进，避免过滤后的短页重复请求。 */
    private fun nextOffset(
        requestedOffset: Int,
        offset: Int,
        count: Int,
        total: Int,
        windowSize: Int = count
    ): Int? {
        check(offset == requestedOffset) { "分页位置与请求不一致，请重试" }
        if (count == 0) return null
        check(windowSize > 0) { "分页跨度无效，请重试" }
        val next = offset.toLong() + windowSize
        check(next > requestedOffset && next <= Int.MAX_VALUE) { "分页位置无效，请重试" }
        return next.toInt().takeIf { it < total }
    }
}
