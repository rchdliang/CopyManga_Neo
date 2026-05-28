package com.rchdliang.copymanga_neo.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * 章节列表响应
 */
data class FavoriteResponse(
    @SerializedName("code") val code: Int,
    @SerializedName("message") val message: String?,
    @SerializedName("results") val results: FavoriteResult
)

data class FavoriteResult(
    @SerializedName("limit") val limit: Int,
    @SerializedName("list") val list: List<FavoritesDto>,
    @SerializedName("offset") val offset: Int,
    @SerializedName("total") val total: Int,
)

data class FavoritesDto(
    @SerializedName("uuid") val uuid: Int,//整形的漫画编号?似乎没什么意义
    @SerializedName("name") val name: String?,
    @SerializedName("b_folder") val bFolder: Boolean?,
    @SerializedName("folder_id") val folderId: Int?,
//    @SerializedName("last_browse") val
    @SerializedName("comic") val favComic: FavComicDto,

    )

data class FavComicDto(
    @SerializedName("uuid") val uuid: String,
    @SerializedName("b_display") val bDisplay: Boolean?,//Always True?
    @SerializedName("name") val name: String,
    @SerializedName("path_word") val pathWord: String,
//    @SerializedName("males") val males: List<>?,
//    @SerializedName("females") val females: List<>?,
    @SerializedName("author") val author: List<AuthorDto>?,
    @SerializedName("theme") val theme: List<ThemeDto>?,
    @SerializedName("cover") val cover: String?,
    @SerializedName("status") val status: Int,//连载=0，完结=1？
    @SerializedName("popular") val popular: Int?,
    @SerializedName("datetime_updated") val datetimeUpdated: String?,
    @SerializedName("last_chapter_id") val lastChapterId: String,
    @SerializedName("last_chapter_name") val lastChapterName: String,
    @SerializedName("browse") val browse: BrowseDto,
)

data class BrowseDto(
    @SerializedName("comic_uuid") val comicUuid: String?,
    @SerializedName("path_word") val pathWord: String?,
    @SerializedName("chapter_uuid") val chapterUuid: String?,
    @SerializedName("chapter_name") val chapterName: String?,
)