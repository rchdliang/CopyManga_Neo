package com.rchdliang.copymanga_neo.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * 漫画详情响应
 */
data class ComicDetailResponse(
    @SerializedName("code") val code: Int,
    @SerializedName("message") val message: String?,
    @SerializedName("results") val results: ComicDetailDto
)

/**
 * 漫画详情
 */
data class ComicDetailDto(
    @SerializedName("uuid") val uuid: String,
    @SerializedName("name") val name: String,
    @SerializedName("path_word") val pathWord: String,
    @SerializedName("cover") val cover: String?,
    @SerializedName("author") val author: List<AuthorDto>?,
    @SerializedName("styles") val styles: List<StyleDto>?,
    @SerializedName("status") val status: ComicStatusDto?,
    @SerializedName("brief") val brief: String?,
    @SerializedName("description") val description: String?,
    @SerializedName("last_update") val lastUpdate: String?,
    @SerializedName("popular") val popular: Int?
)

/**
 * 漫画状态
 */
data class ComicStatusDto(
    @SerializedName("value") val value: Int,
    @SerializedName("display") val display: String
)