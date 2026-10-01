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
import kotlinx.coroutines.withContext
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

/** 将 HAR 已确认的漫画发现接口转换为领域模型。 */
@Singleton
class ComicDiscoveryRepositoryImpl @Inject constructor(
    private val api: MangaApiService,
    private val networkSettings: NetworkSettingsStore
) : ComicDiscoveryRepository {
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
            nextOffset(page.offset, page.list.size, page.total)
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
            nextOffset(page.offset, page.list.size, page.total)
        )
    }

    override suspend fun getRecommendations(offset: Int, limit: Int): Result<ComicPage> = result {
        require(offset >= 0 && limit in 1..21)
        val response = api.getRecComics(limit = limit, offset = offset, freeType = null)
        val body = response.body()
        val page = checked(response, body?.code, body?.message, body?.results)
        ComicPage(
            page.list.map { it.comic.toSummary() },
            nextOffset(page.offset, page.list.size, page.total)
        )
    }

    override suspend fun getFinished(offset: Int, limit: Int): Result<ComicPage> = result {
        require(offset >= 0 && limit in 1..21)
        val response = api.getFinishedComics(offset = offset, limit = limit)
        val body = response.body()
        val page = checked(response, body?.code, body?.message, body?.results)
        ComicPage(
            page.list.map { it.toSummary() },
            nextOffset(page.offset, page.list.size, page.total)
        )
    }

    override suspend fun getTopics(offset: Int): Result<ComicTopicPage> = result {
        require(offset >= 0)
        val response = api.getComicTopics(offset = offset)
        val body = response.body()
        val page = checked(response, body?.code, body?.message, body?.results)
        ComicTopicPage(
            page.list.map { ComicTopic(it.pathWord, it.title, it.cover, it.brief) },
            nextOffset(page.offset, page.list.size, page.total)
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
            nextOffset(page.offset, page.list.size, page.total)
        )
    }

    override suspend fun getFilterOptions(): Result<ComicFilterOptions> = result {
        val response = api.getComicFilterTags()
        val body = response.body()
        val options = checked(response, body?.code, body?.message, body?.results)
        ComicFilterOptions(
            themes = options.theme.orEmpty().map { ComicFilterOption(it.name, it.pathWord) },
            tops = options.top.orEmpty().map { ComicFilterOption(it.name, it.pathWord) },
            orderings = options.ordering.orEmpty()
                .filter { it.pathWord in setOf("datetime_updated", "popular") }
                .map { ComicFilterOption(it.name, "-${it.pathWord}") }
        )
    }

    override suspend fun filterComics(
        theme: String?,
        top: String?,
        ordering: String,
        offset: Int
    ): Result<ComicPage> = result {
        require(
            offset >= 0 && (top == null || top in setOf("japan", "korea", "west", "finish")) &&
                    ordering in setOf("-datetime_updated", "-popular", "datetime_updated")
        ) {
            "该筛选组合没有抓包证据"
        }
        val response =
            api.browseComics(theme = theme, top = top, ordering = ordering, offset = offset)
        val body = response.body()
        val page = checked(response, body?.code, body?.message, body?.results)
        ComicPage(
            page.list.map { it.toSummary() },
            nextOffset(page.offset, page.list.size, page.total)
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

    private fun nextOffset(offset: Int, count: Int, total: Int): Int? =
        (offset + count).takeIf { count > 0 && it < total }
}
