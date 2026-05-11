package com.rchdliang.copymanga_neo.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * 首页响应数据
 */
data class HomeIndexResponse(
    @SerializedName("code") val code: Int,
    @SerializedName("message") val message: String?,
    @SerializedName("results") val results: HomeIndexResult
)

/**
 * 首页结果
 */
data class HomeIndexResult(
    @SerializedName("topics") val topics: TopicList?,
    @SerializedName("topicsList") val topicsList: TopicList?,
    @SerializedName("recComics") val recComics: ComicListSection?,
    @SerializedName("rankDayComics") val rankDayComics: RankingList?,
    @SerializedName("rankWeekComics") val rankWeekComics: RankingList?,
    @SerializedName("rankMonthComics") val rankMonthComics: RankingList?,
    @SerializedName("hotComics") val hotComics: List<HotComicItem>?,
    @SerializedName("newComics") val newComics: List<HotComicItem>?,
    @SerializedName("finishComics") val finishComics: FinishList?,
    @SerializedName("banners") val banners: List<Banner>?
)

/**
 * 专题列表
 */
data class TopicList(
    @SerializedName("list") val list: List<TopicItem>,
    @SerializedName("total") val total: Int,
    @SerializedName("limit") val limit: Int,
    @SerializedName("offset") val offset: Int
)

/**
 * 专题项
 */
data class TopicItem(
    @SerializedName("title") val title: String,
    @SerializedName("series") val series: String?,
    @SerializedName("journal") val journal: String?,
    @SerializedName("cover") val cover: String?,
    @SerializedName("period") val period: String?,
    @SerializedName("type") val type: Int,
    @SerializedName("brief") val brief: String?,
    @SerializedName("path_word") val pathWord: String,
    @SerializedName("datetime_created") val datetimeCreated: String?
)

/**
 * 推荐漫画区块
 */
data class ComicListSection(
    @SerializedName("list") val list: List<ComicSectionItem>,
    @SerializedName("total") val total: Int,
    @SerializedName("limit") val limit: Int,
    @SerializedName("offset") val offset: Int
)

/**
 * 漫画区块项（包含 type 和 comic 嵌套结构）
 */
data class ComicSectionItem(
    @SerializedName("type") val type: Int,
    @SerializedName("comic") val comic: ComicDto
)

/**
 * 排行榜列表
 */
data class RankingList(
    @SerializedName("list") val list: List<RankingItem>,
    @SerializedName("total") val total: Int,
    @SerializedName("limit") val limit: Int,
    @SerializedName("offset") val offset: Int
)

/**
 * 排行榜项
 */
data class RankingItem(
    @SerializedName("sort") val sort: Int,
    @SerializedName("sort_last") val sortLast: Int,
    @SerializedName("rise_sort") val riseSort: Int,
    @SerializedName("rise_num") val riseNum: Int,
    @SerializedName("date_type") val dateType: Int,
    @SerializedName("popular") val popular: Int,
    @SerializedName("comic") val comic: ComicDto
)

/**
 * 热门/新漫画项
 */
data class HotComicItem(
    @SerializedName("name") val name: String,
    @SerializedName("datetime_created") val datetimeCreated: String?,
    @SerializedName("comic") val comic: ComicDto
)

/**
 * 完结漫画列表
 */
data class FinishList(
    @SerializedName("list") val list: List<ComicDto>,
    @SerializedName("total") val total: Int,
    @SerializedName("limit") val limit: Int,
    @SerializedName("offset") val offset: Int,
    @SerializedName("path_word") val pathWord: String,
    @SerializedName("name") val name: String,
    @SerializedName("type") val type: String
)

/**
 * 横幅广告
 */
data class Banner(
    @SerializedName("type") val type: Int,
    @SerializedName("cover") val cover: String?,
    @SerializedName("brief") val brief: String?,
    @SerializedName("out_uuid") val outUuid: String?,
    @SerializedName("comic") val comic: ComicDto?
)
