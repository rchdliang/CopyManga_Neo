package com.rchdliang.copymanga_neo.data.remote.portable.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 搜索响应
 */
@Serializable
data class SearchResponse(
    @SerialName("code") val code: Int,
    @SerialName("message") val message: String? = null,
    @SerialName("results") val results: SearchResult
)

@Serializable
data class SearchResult(
    @SerialName("list") val list: List<ComicDto>,
    @SerialName("total") val total: Int,
    @SerialName("limit") val limit: Int,
    @SerialName("offset") val offset: Int
)