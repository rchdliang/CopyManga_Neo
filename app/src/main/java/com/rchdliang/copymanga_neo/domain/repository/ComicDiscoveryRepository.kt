package com.rchdliang.copymanga_neo.domain.repository

import com.rchdliang.copymanga_neo.domain.model.ComicFilterOptions
import com.rchdliang.copymanga_neo.domain.model.ComicPage
import com.rchdliang.copymanga_neo.domain.model.ComicTopic
import com.rchdliang.copymanga_neo.domain.model.ComicTopicPage

/** 抓包确认的漫画只读入口，支持网页和 App 请求模式。 */
interface ComicDiscoveryRepository {
    fun cachedFilterOptions(): ComicFilterOptions =
        com.rchdliang.copymanga_neo.domain.model.ComicFilterDefaults.options

    suspend fun getRankings(
        period: String,
        audience: String,
        offset: Int,
        limit: Int = 21
    ): Result<ComicPage>

    suspend fun getNewest(offset: Int, limit: Int = 21): Result<ComicPage>
    suspend fun getRecommendations(offset: Int, limit: Int = 21): Result<ComicPage>
    suspend fun getFinished(offset: Int, limit: Int = 21): Result<ComicPage>
    suspend fun getTopics(offset: Int): Result<ComicTopicPage>
    suspend fun getTopic(pathWord: String): Result<ComicTopic>
    suspend fun getTopicComics(pathWord: String, offset: Int): Result<ComicPage>
    suspend fun getFilterOptions(): Result<ComicFilterOptions>
    suspend fun filterComics(
        theme: String?,
        top: String?,
        ordering: String,
        offset: Int
    ): Result<ComicPage>
}
