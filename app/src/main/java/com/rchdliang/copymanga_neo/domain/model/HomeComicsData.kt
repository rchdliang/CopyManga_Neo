package com.rchdliang.copymanga_neo.domain.model

import com.rchdliang.copymanga_neo.data.remote.dto.ComicDto

/**
 * 首页漫画数据
 */
data class HomeComicsData(
    val recComics: List<ComicDto> = emptyList(),
    val hotComics: List<ComicDto> = emptyList(),
    val newComics: List<ComicDto> = emptyList(),
    val finishComics: List<ComicDto> = emptyList()
)