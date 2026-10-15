package com.rchdliang.copymanga_neo.data.remote.portable.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 漫画排行榜、最新更新、专题及筛选接口响应。 */
@Serializable
data class RankingResponse(
    @SerialName("code") val code: Int,
    @SerialName("message") val message: String? = null,
    @SerialName("results") val results: RankingList? = null
)

@Serializable
data class NewestResponse(
    @SerialName("code") val code: Int,
    @SerialName("message") val message: String? = null,
    @SerialName("results") val results: NewestResult? = null
)

@Serializable
data class NewestResult(
    @SerialName("list") val list: List<HotComicItem>,
    @SerialName("total") val total: Int,
    @SerialName("offset") val offset: Int,
    @SerialName("limit") val limit: Int? = null
)

@Serializable
data class TopicsResponse(
    @SerialName("code") val code: Int,
    @SerialName("message") val message: String? = null,
    @SerialName("results") val results: TopicList? = null
)

@Serializable
data class TopicDetailResponse(
    @SerialName("code") val code: Int,
    @SerialName("message") val message: String? = null,
    @SerialName("results") val results: TopicDetailDto? = null
)

@Serializable
data class TopicDetailDto(
    @SerialName("path_word") val pathWord: String,
    @SerialName("title") val title: String,
    @SerialName("cover") val cover: String? = null,
    @SerialName("brief") val brief: String? = null,
    @SerialName("intro") val intro: String? = null,
    @SerialName("type") val type: Int? = null
)

@Serializable
data class ComicFilterTagsResponse(
    @SerialName("code") val code: Int,
    @SerialName("message") val message: String? = null,
    @SerialName("results") val results: ComicFilterTagsResult? = null
)

@Serializable
data class ComicFilterTagsResult(
    @SerialName("theme") val theme: List<ComicFilterTagDto>? = null,
    @SerialName("top") val top: List<ComicFilterTagDto>? = null,
    @SerialName("ordering") val ordering: List<ComicFilterTagDto>? = null
)

@Serializable
data class ComicFilterTagDto(
    @SerialName("name") val name: String,
    @SerialName("path_word") val pathWord: String
)

/** 全部漫画题材及对应漫画数量；initials 不参与筛选。 */
@Serializable
data class ComicThemeCountsResponse(
    @SerialName("code") val code: Int,
    @SerialName("message") val message: String? = null,
    @SerialName("results") val results: ComicThemeCountsResult? = null
)

@Serializable
data class ComicThemeCountsResult(
    @SerialName("list") val list: List<ComicThemeCountDto>,
    @SerialName("total") val total: Int,
    @SerialName("offset") val offset: Int,
    @SerialName("limit") val limit: Int
)

@Serializable
data class ComicThemeCountDto(
    @SerialName("name") val name: String,
    @SerialName("path_word") val pathWord: String,
    @SerialName("count") val count: Int? = null
)
