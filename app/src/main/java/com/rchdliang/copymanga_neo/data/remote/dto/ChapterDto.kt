package com.rchdliang.copymanga_neo.data.remote.dto

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
    @SerializedName("total") val total: Int
)

/**
 * 章节信息
 */
data class ChapterDto(
    @SerializedName("uuid") val uuid: String,
    @SerializedName("name") val name: String,
    @SerializedName("index") val index: Int,
    @SerializedName("size") val size: Int?,
    @SerializedName("datetime_created") val datetimeCreated: String?
)