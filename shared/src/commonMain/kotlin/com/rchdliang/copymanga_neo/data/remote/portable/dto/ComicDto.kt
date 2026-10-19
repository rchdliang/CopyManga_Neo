package com.rchdliang.copymanga_neo.data.remote.portable.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 漫画列表响应
 */
@Serializable
data class ComicListResponse(
    @SerialName("code") val code: Int,
    @SerialName("message") val message: String? = null,
    @SerialName("results") val results: ComicListResult
)

@Serializable
data class ComicListResult(
    @SerialName("list") val list: List<ComicSectionItem>,
    @SerialName("total") val total: Int,
    @SerialName("limit") val limit: Int,
    @SerialName("offset") val offset: Int
)

@Serializable
data class ComicSectionItem(
    @SerialName("type") val type: Int,
    @SerialName("comic") val comic: ComicDto
)

/**
 * 漫画简要信息
 */
@Serializable
data class ComicDto(
    @SerialName("name") val name: String,
    @SerialName("females") val females: List<CharacterDto>? = null,
    @SerialName("males") val males: List<CharacterDto>? = null,
    @SerialName("theme") val theme: List<ThemeDto>? = null,
    @SerialName("path_word") val pathWord: String,
    @SerialName("author") val author: List<AuthorDto>? = null,
    @SerialName("cover") val cover: String? = null,
    @SerialName("popular") val popular: Int? = null,
    @SerialName("author_alias") val authorAlias: String? = null,
    @SerialName("datetime_updated") val lastUpdate: String? = null
)


@Serializable
data class CharacterDto(
    @SerialName("name") val name: String,
    @SerialName("path_word") val pathWord: String? = null
)

/**
 * 主题/题材信息
 */
@Serializable
data class ThemeDto(
    @SerialName("name") val name: String,
    @SerialName("path_word") val pathWord: String? = null
)

/**
 * 作者信息
 */
@Serializable
data class AuthorDto(
    @SerialName("name") val name: String,
    @SerialName("path_word") val pathWord: String? = null
)