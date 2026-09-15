package com.rchdliang.copymanga_neo.data.remote.dto

import com.google.gson.annotations.SerializedName

data class BrowseHistoryResponse(
    @SerializedName("code") val code: Int,
    @SerializedName("message") val message: String?,
    @SerializedName("results") val results: BrowseHistoryResult?
)

data class BrowseHistoryResult(
    @SerializedName("list") val list: List<BrowseHistoryDto>,
    @SerializedName("total") val total: Int,
    @SerializedName("limit") val limit: Int,
    @SerializedName("offset") val offset: Int
)

data class BrowseHistoryDto(
    @SerializedName("id") val id: Long,
    // 外层章节字段是用户上次阅读的位置，不是最新章节。
    @SerializedName("last_chapter_id") val lastReadChapterId: String?,
    @SerializedName("last_chapter_name") val lastReadChapterName: String?,
    @SerializedName("comic") val comic: BrowseHistoryComicDto
)

data class BrowseHistoryComicDto(
    @SerializedName("name") val name: String,
    @SerializedName("path_word") val pathWord: String,
    @SerializedName("cover") val cover: String?,
    @SerializedName("author") val authors: List<AuthorDto>?,
    @SerializedName("last_chapter_id") val latestChapterId: String?,
    @SerializedName("last_chapter_name") val latestChapterName: String?
)
