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
    //某一期的推荐
    //https://api.copy4000.com/api/v3/topic/remenmanhua201904/contents?limit=20&offset=0
    @SerializedName("topicsList") val topicsList: TopicList?,
    //返回一个列表，专题系列，内容为第xx期必看推荐
    //https://api.copy4000.com/api/v3/topics?type=1&limit=20&offset=0&_update=true
    //不清楚“_update=true”的含义
    @SerializedName("recComics") val recComics: ComicListResult?,
    //https://api.copy4000.com/api/v3/recs?pos=3200102&limit=10&offset=0
    //total 1743
    //pos代表的是什么？会改变吗？位置信息不能为空
    @SerializedName("rankDayComics") val rankDayComics: RankingList?,
    //date_type = [day,week,month,total]，其中 day 是”上升最快“
    //https://api.copy4000.com/api/v3/ranks?type=1&date_type=day&audience_type=male&offset=0&limit=21&_update=true
    //https://api.copy4000.com/api/v3/ranks?type=5&date_type=day&offset=0&limit=21&_update=true
    //type = 5 指的是轻小说？
    //https://api.copy4000.com/api/v3/ranks?type=1&date_type=day&audience_type=female&offset=0&limit=21&_update=true
    @SerializedName("rankWeekComics") val rankWeekComics: RankingList?,
    @SerializedName("rankMonthComics") val rankMonthComics: RankingList?,
    @SerializedName("hotComics") val hotComics: List<HotComicItem>?,
    //热门更新，12个
    @SerializedName("newComics") val newComics: List<HotComicItem>?,
    //全新上架 https://api.copy4000.com/api/v3/update/newest?limit=21&offset=21
    @SerializedName("finishComics") val finishComics: FinishList?,
    //已完结
    //https://api.copy4000.com/api/v3/comics?free_type=1&top=finish&offset=0&limit=21&ordering=popular
    //https://api.copy4000.com/api/v3/comics?free_type=1&top=finish&offset=0&limit=21&ordering=-datetime_updated
    //free_type = 0 时无结果,free_type 是什么？
    @SerializedName("banners") val banners: List<Banner>?
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
