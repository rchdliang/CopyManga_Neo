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
            limit = limit
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
            limit = limit,
            update = true.takeIf { offset == 0 })
        val body = response.body()
        val page = checked(response, body?.code, body?.message, body?.results)
        ComicPage(
            page.list.map { it.comic.toSummary() },
            nextOffset(offset, page.offset, page.list.size, page.total)
        )
    }

    override suspend fun getRecommendations(offset: Int, limit: Int): Result<ComicPage> = result {
        require(offset >= 0 && limit in 1..21)
        val response = api.getRecComics(limit = limit, offset = offset, freeType = null)
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
        val response = api.getComicTopics(offset = offset)
        val body = response.body()
        val page = checked(response, body?.code, body?.message, body?.results)
        ComicTopicPage(
            page.list.map { ComicTopic(it.pathWord, it.title, it.cover, it.brief) },
            nextOffset(offset, page.offset, page.list.size, page.total, page.limit)
        )
    }

    override suspend fun getTopic(pathWord: String): Result<ComicTopic> = result {
        require(pathWord.isNotBlank())
        val response = api.getComicTopic(pathWord)
        val body = response.body()
        val topic = checked(response, body?.code, body?.message, body?.results)
        ComicTopic(topic.pathWord, topic.title, topic.cover, topic.brief ?: topic.intro)
    }

    override suspend fun getTopicComics(pathWord: String, offset: Int): Result<ComicPage> = result {
        require(pathWord.isNotBlank() && offset >= 0)
        val response = api.getComicTopicContents(pathWord, offset = offset)
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
            api.browseComics(theme = theme, top = top, ordering = ordering, offset = offset)
        val body = response.body()
        val page = checked(response, body?.code, body?.message, body?.results)
        ComicPage(
            page.list.map { it.toSummary() },
            nextOffset(offset, page.offset, page.list.size, page.total, page.limit),
            page.total
        )
    }

    private fun requireWebMode() {
        check(networkSettings.settings.value.requestMode == RequestMode.WEB) {
            "探索漫画目前仅支持网页请求模式"
        }
    }

    private suspend fun <T> result(block: suspend () -> T): Result<T> =
        withContext(Dispatchers.IO) {
            try {
                requireWebMode()
                Result.success(block())
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
