package com.rchdliang.copymanga_neo.data.remote.dto

import com.google.gson.annotations.SerializedName

/** 漫画排行榜、最新更新、专题及筛选接口响应。 */
data class RankingResponse(
    @SerializedName("code") val code: Int,
    @SerializedName("message") val message: String?,
    @SerializedName("results") val results: RankingList?
)

data class NewestResponse(
    @SerializedName("code") val code: Int,
    @SerializedName("message") val message: String?,
    @SerializedName("results") val results: NewestResult?
)

data class NewestResult(
    @SerializedName("list") val list: List<HotComicItem>,
    @SerializedName("total") val total: Int,
    @SerializedName("offset") val offset: Int
)

data class TopicsResponse(
    @SerializedName("code") val code: Int,
    @SerializedName("message") val message: String?,
    @SerializedName("results") val results: TopicList?
)

data class TopicDetailResponse(
    @SerializedName("code") val code: Int,
    @SerializedName("message") val message: String?,
    @SerializedName("results") val results: TopicDetailDto?
)

data class TopicDetailDto(
    @SerializedName("path_word") val pathWord: String,
    @SerializedName("title") val title: String,
    @SerializedName("cover") val cover: String?,
    @SerializedName("brief") val brief: String?,
    @SerializedName("intro") val intro: String?
)

data class ComicFilterTagsResponse(
    @SerializedName("code") val code: Int,
    @SerializedName("message") val message: String?,
    @SerializedName("results") val results: ComicFilterTagsResult?
)

data class ComicFilterTagsResult(
    @SerializedName("theme") val theme: List<ComicFilterTagDto>?,
    @SerializedName("top") val top: List<ComicFilterTagDto>?,
    @SerializedName("ordering") val ordering: List<ComicFilterTagDto>?
)

data class ComicFilterTagDto(
    @SerializedName("name") val name: String,
    @SerializedName("path_word") val pathWord: String
)

/** 全部漫画题材及对应漫画数量；initials 不参与筛选。 */
data class ComicThemeCountsResponse(
    @SerializedName("code") val code: Int,
    @SerializedName("message") val message: String?,
    @SerializedName("results") val results: ComicThemeCountsResult?
)

data class ComicThemeCountsResult(
    @SerializedName("list") val list: List<ComicThemeCountDto>,
    @SerializedName("total") val total: Int,
    @SerializedName("offset") val offset: Int,
    @SerializedName("limit") val limit: Int
)

data class ComicThemeCountDto(
    @SerializedName("name") val name: String,
    @SerializedName("path_word") val pathWord: String,
    @SerializedName("count") val count: Int?
)
