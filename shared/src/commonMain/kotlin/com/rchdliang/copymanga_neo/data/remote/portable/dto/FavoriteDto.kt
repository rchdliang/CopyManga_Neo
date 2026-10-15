package com.rchdliang.copymanga_neo.data.remote.portable.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 章节列表响应
 */
@Serializable
data class FavoriteResponse(
    @SerialName("code") val code: Int,
    @SerialName("message") val message: String? = null,
    @SerialName("results") val results: FavoriteResult
)

@Serializable
data class FavoriteResult(
    @SerialName("limit") val limit: Int,
    @SerialName("list") val list: List<FavoritesDto>,
    @SerialName("offset") val offset: Int,
    @SerialName("total") val total: Int,
)

@Serializable
data class FavoritesDto(
    @SerialName("uuid") val uuid: Int,//整形的编号，用于在书架内部标识
    @SerialName("name") val name: String? = null,
    @SerialName("b_folder") val bFolder: Boolean? = null,
    @SerialName("folder_id") val folderId: Int? = null,
//    @SerialName("last_browse") val
    @SerialName("comic") val favComic: FavComicDto,

    )

@Serializable
data class FavComicDto(
    @SerialName("uuid") val uuid: String,
    @SerialName("b_display") val bDisplay: Boolean? = null,//Always True?
    @SerialName("name") val name: String,
    @SerialName("path_word") val pathWord: String,
//    @SerialName("males") val males: List<>? = null,
//    @SerialName("females") val females: List<>? = null,
    @SerialName("author") val author: List<AuthorDto>? = null,
    @SerialName("theme") val theme: List<ThemeDto>? = null,
    @SerialName("cover") val cover: String? = null,
    @SerialName("status") val status: Int,//连载=0，完结=1？
    @SerialName("popular") val popular: Int? = null,
    @SerialName("datetime_updated") val datetimeUpdated: String? = null,
    @SerialName("last_chapter_id") val lastChapterId: String,
    @SerialName("last_chapter_name") val lastChapterName: String,
    @SerialName("browse") val browse: BrowseDto? = null,
)

@Serializable
data class BrowseDto(
    @SerialName("comic_uuid") val comicUuid: String? = null,
    @SerialName("path_word") val pathWord: String? = null,
    @SerialName("chapter_uuid") val chapterUuid: String? = null,
    @SerialName("chapter_name") val chapterName: String? = null,
)