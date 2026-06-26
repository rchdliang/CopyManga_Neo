package com.rchdliang.copymanga_neo.domain.model

/**
 * 收藏项
 */
data class FavoriteItem(
    val uuid: Int,
    val name: String?,
    val bFolder: Boolean?,
    val folderId: Int?,
    val comic: FavoriteComic
)

/**
 * 收藏的漫画信息
 */
data class FavoriteComic(
    val uuid: String,
    val bDisplay: Boolean?,
    val name: String,
    val pathWord: String,
    val author: List<Author>?,
    val theme: List<Theme>?,
    val cover: String?,
    val status: Int,
    val popular: Int?,
    val datetimeUpdated: String?,
    val lastChapterId: String,
    val lastChapterName: String,
    val browse: BrowseInfo?
)
