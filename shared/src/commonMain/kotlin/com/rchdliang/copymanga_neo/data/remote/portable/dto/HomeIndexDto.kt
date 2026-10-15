package com.rchdliang.copymanga_neo.data.remote.portable.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 首页响应数据
 */
@Serializable
data class HomeIndexResponse(
    @SerialName("code") val code: Int,
    @SerialName("message") val message: String? = null,
    @SerialName("results") val results: HomeIndexResult
)

/**
 * 首页结果
 */
@Serializable
data class HomeIndexResult(
    @SerialName("topics") val topics: TopicList? = null,
    //某一期的推荐
    //https://api.copy4000.com/api/v3/topic/remenmanhua201904/contents?limit=20&offset=0
    @SerialName("topicsList") val topicsList: TopicList? = null,
    //返回一个列表，专题系列，内容为第xx期必看推荐
    //https://api.copy4000.com/api/v3/topics?type=1&limit=20&offset=0&_update=true
    //不清楚“_update=true”的含义
    @SerialName("recComics") val recComics: ComicListResult? = null,
    //https://api.copy4000.com/api/v3/recs?pos=3200102&limit=10&offset=0
    //total 1743
    //pos代表的是什么？会改变吗？位置信息不能为空
    @SerialName("rankDayComics") val rankDayComics: RankingList? = null,
    //date_type = [day,week,month,total]，其中 day 是”上升最快“
    //https://api.copy4000.com/api/v3/ranks?type=1&date_type=day&audience_type=male&offset=0&limit=21&_update=true
    //https://api.copy4000.com/api/v3/ranks?type=5&date_type=day&offset=0&limit=21&_update=true
    //type = 5 指的是轻小说？
    //https://api.copy4000.com/api/v3/ranks?type=1&date_type=day&audience_type=female&offset=0&limit=21&_update=true
    @SerialName("rankWeekComics") val rankWeekComics: RankingList? = null,
    @SerialName("rankMonthComics") val rankMonthComics: RankingList? = null,
    @SerialName("hotComics") val hotComics: List<HotComicItem>? = null,
    //热门更新，12个
    @SerialName("newComics") val newComics: List<HotComicItem>? = null,
    //全新上架 https://api.copy4000.com/api/v3/update/newest?limit=21&offset=21
    @SerialName("finishComics") val finishComics: FinishList? = null,
    //已完结
    //https://api.copy4000.com/api/v3/comics?free_type=1&top=finish&offset=0&limit=21&ordering=popular
    //https://api.copy4000.com/api/v3/comics?free_type=1&top=finish&offset=0&limit=21&ordering=-datetime_updated
    //free_type = 0 时无结果,free_type 是什么？
    @SerialName("banners") val banners: List<Banner>? = null
    //有广告有推荐，暂不处理
    //
    //发现页
    //https://api.copy4000.com/api/v3/comics?free_type=1&limit=21&offset=0&ordering=-datetime_updated&_update=true
    //https://api.copy4000.com/api/v3/comics?free_type=1&limit=21&offset=0&theme=aiqing&ordering=-datetime_updated&_update=true
    //https://api.copy4000.com/api/v3/comics?free_type=1&limit=21&offset=0&top=japan&theme=aiqing&ordering=-datetime_updated&_update=true
    // theme 题材
    // https://api.copy4000.com/api/v3/theme/comic/count?free_type=1&limit=500&offset=0&_update=true
    // top topic? 分类 [japan,korea,west,finish] 日漫，韩漫，美漫，完结
    //
)

/**
 * 专题列表
 */
@Serializable
data class TopicList(
    @SerialName("list") val list: List<TopicItem>,
    @SerialName("total") val total: Int,
    @SerialName("limit") val limit: Int,
    @SerialName("offset") val offset: Int
)

/**
 * 专题项
 */
@Serializable
data class TopicItem(
    @SerialName("title") val title: String,
    @SerialName("series") val series: String? = null,
    @SerialName("journal") val journal: String? = null,
    @SerialName("cover") val cover: String? = null,
    @SerialName("period") val period: String? = null,
    @SerialName("type") val type: Int,
    @SerialName("brief") val brief: String? = null,
    @SerialName("path_word") val pathWord: String,
    @SerialName("datetime_created") val datetimeCreated: String? = null
)


/**
 * 排行榜列表
 */
@Serializable
data class RankingList(
    @SerialName("list") val list: List<RankingItem>,
    @SerialName("total") val total: Int,
    @SerialName("limit") val limit: Int,
    @SerialName("offset") val offset: Int
)

/**
 * 排行榜项
 */
@Serializable
data class RankingItem(
    @SerialName("sort") val sort: Int,
    @SerialName("sort_last") val sortLast: Int,
    @SerialName("rise_sort") val riseSort: Int,
    @SerialName("rise_num") val riseNum: Int,
    @SerialName("date_type") val dateType: Int,
    @SerialName("popular") val popular: Int,
    @SerialName("comic") val comic: ComicDto
)

/**
 * 热门/新漫画项
 */
@Serializable
data class HotComicItem(
    @SerialName("name") val name: String,
    @SerialName("datetime_created") val datetimeCreated: String? = null,
    @SerialName("comic") val comic: ComicDto
)

/**
 * 完结漫画列表
 */
@Serializable
data class FinishList(
    @SerialName("list") val list: List<ComicDto>,
    @SerialName("total") val total: Int,
    @SerialName("limit") val limit: Int,
    @SerialName("offset") val offset: Int,
    @SerialName("path_word") val pathWord: String,
    @SerialName("name") val name: String,
    @SerialName("type") val type: String
)

/**
 * 横幅广告
 */
@Serializable
data class Banner(
    @SerialName("type") val type: Int,
    @SerialName("cover") val cover: String? = null,
    @SerialName("brief") val brief: String? = null,
    @SerialName("out_uuid") val outUuid: String? = null,
    @SerialName("comic") val comic: ComicDto? = null
)
