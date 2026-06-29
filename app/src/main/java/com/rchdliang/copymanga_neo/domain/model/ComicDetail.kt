package com.rchdliang.copymanga_neo.domain.model

/**
 * 漫画详情（包含详情信息和章节分组）
 */
data class ComicDetail(
    val comic: ComicInfo,
    val groups: Map<String, ChapterGroup>,
    val isBanned: Boolean,
    val isLock: Boolean,
    val isLogin: Boolean,
    val isMobileBind: Boolean,
    val isVip: Boolean
)

/**
 * 漫画详情信息
 */
data class ComicInfo(
    val uuid: String,
    val b404: Boolean,
    val bHidden: Boolean,
    val ban: Int,
    val name: String,
    val comicAlias: String?,
    val pathWord: String,
    val closeComment: Boolean,
    val closeRoast: Boolean,
    val freeType: DisplayValue,
    val restrict: DisplayValue,
    val reclass: DisplayValue,
    val females: List<String>?,
    val males: List<String>?,
    val clubs: List<String>?,
    val region: DisplayValue,
    val status: DisplayValue,
    val author: List<Author>?,
    val theme: List<Theme>?,
    val parodies: List<String>?,
    val brief: String?,
    val lastUpdate: String?,
    val cover: String?,
    val lastChapter: LastChapter?,
    val popular: Int?,
    val channel: Int?
)

/**
 * 章节分组
 */
data class ChapterGroup(
    val pathWord: String,
    val total: Int,
    val name: String
)

/**
 * 显示值（display + value）
 */
data class DisplayValue(
    val display: String,
    val value: Int
)

/**
 * 最后一章信息
 */
data class LastChapter(
    val id: String,
    val name: String
)
