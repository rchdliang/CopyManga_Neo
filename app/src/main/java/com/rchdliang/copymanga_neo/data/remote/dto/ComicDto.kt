package com.rchdliang.copymanga_neo.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * 漫画列表响应
 */
data class ComicListResponse(
    @SerializedName("code") val code: Int,
    @SerializedName("message") val message: String?,
    @SerializedName("results") val results: ComicListResult
)

data class ComicListResult(
    @SerializedName("list") val list: List<ComicDto>,
    @SerializedName("total") val total: Int,
    @SerializedName("limit") val limit: Int,
    @SerializedName("offset") val offset: Int
)

/**
 * 漫画简要信息
 */
data class ComicDto(
    @SerializedName("uuid") val uuid: String,
    @SerializedName("name") val name: String,
    @SerializedName("path_word") val pathWord: String,
    @SerializedName("cover") val cover: String?,
    @SerializedName("author") val author: List<AuthorDto>?,
    @SerializedName("styles") val styles: List<StyleDto>?,
    @SerializedName("status") val status: Int?,
    @SerializedName("datetime_updated") val datetimeUpdated: String?
)

/**
 * 作者信息
 */
data class AuthorDto(
    @SerializedName("uuid") val uuid: String,
    @SerializedName("name") val name: String,
    @SerializedName("path_word") val pathWord: String?
)

/**
 * 风格/类型
 */
data class StyleDto(
    @SerializedName("name") val name: String
)