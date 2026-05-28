package com.rchdliang.copymanga_neo.data.remote.dto

import androidx.compose.runtime.Immutable
import com.google.gson.annotations.SerializedName

/**
 * 章节列表响应
 */
data class ChapterListResponse(
    @SerializedName("code") val code: Int,
    @SerializedName("message") val message: String?,
    @SerializedName("results") val results: ChapterListResult
)

data class ChapterListResult(
    @SerializedName("list") val list: List<ChapterDto>,
    @SerializedName("total") val total: Int,
    @SerializedName("limit") val limit: Int,
    @SerializedName("offset") val offset: Int,
)

/**
 * 章节信息
 */
@Immutable
data class ChapterDto(
    @SerializedName("index") val index: Int,
    @SerializedName("uuid") val uuid: String,
    @SerializedName("count") val count: Int,
    @SerializedName("ordered") val ordered: Int,
    @SerializedName("size") val size: Int?,
    @SerializedName("name") val name: String,
    @SerializedName("comic_id") val comicId: String,
    @SerializedName("comic_path_word") val comicPathWord: String,
    @SerializedName("group_id") val groupId: String?,
    @SerializedName("group_path_word") val groupPathWord: String?,
    @SerializedName("type") val type: Int,
    @SerializedName("news") val news: String?,
    @SerializedName("datetime_created") val datetimeCreated: String?,
    @SerializedName("prev") val prevChapter: String?,
    @SerializedName("next") val nextChapter: String?
)