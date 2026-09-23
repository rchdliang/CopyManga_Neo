package com.rchdliang.copymanga_neo.data.remote.dto

import com.google.gson.annotations.SerializedName

data class ChapterCatalogResponse(
    @SerializedName("code") val code: Int,
    @SerializedName("message") val message: String?,
    @SerializedName("results") val results: String?
)

data class ChapterCatalogDto(
    @SerializedName("build") val build: ChapterCatalogBuildDto?,
    @SerializedName("groups") val groups: Map<String, ChapterCatalogGroupDto>?
)

data class ChapterCatalogBuildDto(
    @SerializedName("path_word") val pathWord: String?
)

data class ChapterCatalogGroupDto(
    @SerializedName("path_word") val pathWord: String?,
    @SerializedName("count") val count: Int?,
    @SerializedName("name") val name: String?,
    @SerializedName("chapters") val chapters: List<ChapterCatalogEntryDto>?,
    @SerializedName("last_chapter") val lastChapter: ChapterDto?
)

data class ChapterCatalogEntryDto(
    @SerializedName("id") val id: String?,
    @SerializedName("name") val name: String?,
    @SerializedName("type") val type: Int?
)
