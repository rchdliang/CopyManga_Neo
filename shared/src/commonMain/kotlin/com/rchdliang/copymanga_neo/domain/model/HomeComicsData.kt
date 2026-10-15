package com.rchdliang.copymanga_neo.domain.model

/**
 * 首页漫画数据
 */
@kotlinx.serialization.Serializable
data class HomeComicsData(
    val recComics: List<ComicSummary> = emptyList(),
    val hotComics: List<ComicSummary> = emptyList(),
    val newComics: List<ComicSummary> = emptyList(),
    val finishComics: List<ComicSummary> = emptyList(),
    val rankComics: List<ComicSummary> = emptyList(),
    val topics: List<ComicTopic> = emptyList(),
    val recNextOffset: Int? = null,
    val newNextOffset: Int? = null,
    val finishNextOffset: Int? = null,
    val rankNextOffset: Int? = null
)
