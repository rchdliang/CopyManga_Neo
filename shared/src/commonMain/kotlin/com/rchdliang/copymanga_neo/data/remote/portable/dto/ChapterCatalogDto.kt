package com.rchdliang.copymanga_neo.data.remote.portable.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChapterCatalogResponse(
    @SerialName("code") val code: Int,
    @SerialName("message") val message: String? = null,
    @SerialName("results") val results: String? = null
)

@Serializable
data class ChapterCatalogDto(
    @SerialName("build") val build: ChapterCatalogBuildDto? = null,
    @SerialName("groups") val groups: Map<String, ChapterCatalogGroupDto>?
)

@Serializable
data class ChapterCatalogBuildDto(
    @SerialName("path_word") val pathWord: String? = null
)

@Serializable
data class ChapterCatalogGroupDto(
    @SerialName("path_word") val pathWord: String? = null,
    @SerialName("count") val count: Int? = null,
    @SerialName("name") val name: String? = null,
    @SerialName("chapters") val chapters: List<ChapterCatalogEntryDto>? = null,
    @SerialName("last_chapter") val lastChapter: ChapterDto? = null
)

@Serializable
data class ChapterCatalogEntryDto(
    @SerialName("id") val id: String? = null,
    @SerialName("name") val name: String? = null,
    @SerialName("type") val type: Int? = null
)
