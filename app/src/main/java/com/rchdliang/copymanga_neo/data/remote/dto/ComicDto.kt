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
    @SerializedName("list") val list: List<ComicSectionItem>,
    @SerializedName("total") val total: Int,
    @SerializedName("limit") val limit: Int,
    @SerializedName("offset") val offset: Int
)

data class ComicSectionItem(
    @SerializedName("type") val type: Int,
    @SerializedName("comic") val comic: ComicDto
)

/**
 * 漫画简要信息
 */
data class ComicDto(
    @SerializedName("name") val name: String,
    @SerializedName("females") val females: List<CharacterDto>?,
    @SerializedName("males") val males: List<CharacterDto>?,
    @SerializedName("theme") val theme: List<ThemeDto>?,
    @SerializedName("path_word") val pathWord: String,
    @SerializedName("author") val author: List<AuthorDto>?,
    @SerializedName("cover") val cover: String?,
    @SerializedName("popular") val popular: Int?,
    @SerializedName("author_alias") val authorAlias: String?,
    @SerializedName("datetime_updated") val lastUpdate: String? = null
)


data class CharacterDto(
    @SerializedName("name") val name: String,
    @SerializedName("path_word") val pathWord: String?
)

/**
 * 主题/题材信息
 */
data class ThemeDto(
    @SerializedName("name") val name: String,
    @SerializedName("path_word") val pathWord: String?
)

/**
 * 作者信息
 */
data class AuthorDto(
    @SerializedName("name") val name: String,
    @SerializedName("path_word") val pathWord: String?
)