package com.rchdliang.copymanga_neo.domain.model

/**
 * 首页漫画数据
 */
data class HomeComicsData(
    val recComics: List<ComicSummary> = emptyList(),
    val hotComics: List<ComicSummary> = emptyList(),
    val newComics: List<ComicSummary> = emptyList(),
    val finishComics: List<ComicSummary> = emptyList()
)