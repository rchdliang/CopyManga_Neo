package com.rchdliang.copymanga_neo.data.remote.portable.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class BrowseHistoryResponse(
    @SerialName("code") val code: Int,
    @SerialName("message") val message: String? = null,
    @SerialName("results") val results: BrowseHistoryResult? = null
)

@Serializable
data class BrowseHistoryResult(
    @SerialName("list") val list: List<BrowseHistoryDto>,
    @SerialName("total") val total: Int,
    @SerialName("limit") val limit: Int,
    @SerialName("offset") val offset: Int
)

@Serializable
data class BrowseHistoryDto(
    @SerialName("id") val id: Long,
    // 外层章节字段是用户上次阅读的位置，不是最新章节。
    @SerialName("last_chapter_id") val lastReadChapterId: String? = null,
    @SerialName("last_chapter_name") val lastReadChapterName: String? = null,
    @SerialName("comic") val comic: BrowseHistoryComicDto
)

@Serializable
data class BrowseHistoryComicDto(
    @SerialName("name") val name: String,
    @SerialName("path_word") val pathWord: String,
    @SerialName("cover") val cover: String? = null,
    @SerialName("author") val authors: List<AuthorDto>? = null,
    @SerialName("last_chapter_id") val latestChapterId: String? = null,
    @SerialName("last_chapter_name") val latestChapterName: String? = null
)
