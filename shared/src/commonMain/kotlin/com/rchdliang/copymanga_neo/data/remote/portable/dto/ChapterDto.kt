package com.rchdliang.copymanga_neo.data.remote.portable.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 章节列表响应
 */
@Serializable
data class ChapterListResponse(
    @SerialName("code") val code: Int,
    @SerialName("message") val message: String? = null,
    @SerialName("results") val results: ChapterListResult
)

@Serializable
data class ChapterListResult(
    @SerialName("list") val list: List<ChapterDto>,
    @SerialName("total") val total: Int,
    @SerialName("limit") val limit: Int,
    @SerialName("offset") val offset: Int,
)

/**
 * 章节信息
 */
@Serializable
data class ChapterDto(
    @SerialName("index") val index: Int,
    @SerialName("uuid") val uuid: String,
    @SerialName("count") val count: Int,
    @SerialName("ordered") val ordered: Int,
    @SerialName("size") val size: Int? = null,
    @SerialName("name") val name: String,
    @SerialName("comic_id") val comicId: String,
    @SerialName("comic_path_word") val comicPathWord: String,
    @SerialName("group_id") val groupId: String? = null,
    @SerialName("group_path_word") val groupPathWord: String? = null,
    @SerialName("type") val type: Int,
    @SerialName("news") val news: String? = null,
    @SerialName("datetime_created") val datetimeCreated: String? = null,
    @SerialName("prev") val prevChapter: String? = null,
    @SerialName("next") val nextChapter: String? = null
)

@Serializable
data class TrialContentResponse(
    @SerialName("code") val code: Int,
    @SerialName("message") val message: String? = null,
    @SerialName("results") val results: TrialContentResult
)

@Serializable
data class TrialContentResult(
    @SerialName("show_app") val showApp: Boolean = true,//不清楚意义，猜测是用于引导用户下载App
    @SerialName("is_lock") val isLock: Boolean,
    @SerialName("is_login") val isLogin: Boolean,
    @SerialName("is_mobile_bind") val isMobileBind: Boolean,
    @SerialName("is_vip") val isVip: Boolean,
    @SerialName("comic") val comic: TrialComicInfo,
    @SerialName("chapter") val chapter: TrialContentDto,
    @SerialName("is_banned") val isBanned: Boolean,
)

@Serializable
data class TrialComicInfo(
    @SerialName("name") val comicName: String,
    @SerialName("uuid") val comicUuid: String,
    @SerialName("path_word") val comicPathWord: String,
    @SerialName("restrict") val restrict: DisplayAndValue,//"restrict":{"value":0,"display":"一般向(免費)"}
)

@Serializable
data class TrialContentDto(
    @SerialName("index") val chapterIndex: Int,
    @SerialName("uuid") val chapterUuid: String,
    @SerialName("count") val total: Int,//所有章节的数量，和Index差1
    @SerialName("ordered") val ordered: Int,//用于排序
    @SerialName("size") val size: Int,//本章图片数量
    @SerialName("name") val chapterName: String,
    @SerialName("comic_id") val comicUuid: String,
    @SerialName("comic_path_word") val comicPathWord: String,
    @SerialName("group_id") val groupId: String? = null,
    @SerialName("group_path_word") val groupPathWord: String = "default",
    @SerialName("type") val type: Int? = null,
    @SerialName("news") val news: String? = null,//"success"，不知道含义
    @SerialName("datetime_created") val datetimeCreated: String? = null,//2023-03-05
    @SerialName("prev") val prevChapter: String? = null,
    @SerialName("next") val nextChapter: String? = null,
    @SerialName("contents") val urls: List<Map<String, String>>,
    @SerialName("words") val words: List<Int>? = null,//[0,2,1]，不知道是什么
    @SerialName("is_long") val isLong: Boolean? = null, //false 不知道是什么
)