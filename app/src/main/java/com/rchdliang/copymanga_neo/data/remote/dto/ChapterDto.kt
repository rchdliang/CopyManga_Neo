package com.rchdliang.copymanga_neo.data.remote.dto

import androidx.compose.runtime.Immutable
import com.google.gson.annotations.SerializedName

/**
 * 章节列表响应
 */
data class ChapterListResponse(
    @SerializedName("code") val code: Int,
    @SerializedName("message") val message: String?,
    @SerializedName("results") val results: ChapterListResult
)

data class ChapterListResult(
    @SerializedName("list") val list: List<ChapterDto>,
    @SerializedName("total") val total: Int,
    @SerializedName("limit") val limit: Int,
    @SerializedName("offset") val offset: Int,
)

/**
 * 章节信息
 */
@Immutable
data class ChapterDto(
    @SerializedName("index") val index: Int,
    @SerializedName("uuid") val uuid: String,
    @SerializedName("count") val count: Int,
    @SerializedName("ordered") val ordered: Int,
    @SerializedName("size") val size: Int?,
    @SerializedName("name") val name: String,
    @SerializedName("comic_id") val comicId: String,
    @SerializedName("comic_path_word") val comicPathWord: String,
    @SerializedName("group_id") val groupId: String?,
    @SerializedName("group_path_word") val groupPathWord: String?,
    @SerializedName("type") val type: Int,
    @SerializedName("news") val news: String?,
    @SerializedName("datetime_created") val datetimeCreated: String?,
    @SerializedName("prev") val prevChapter: String?,
    @SerializedName("next") val nextChapter: String?
)

data class TrialContentResponse(
    @SerializedName("code") val code: Int,
    @SerializedName("message") val message: String?,
    @SerializedName("results") val results: TrialContentResult
)

data class TrialContentResult(
    @SerializedName("show_app") val showApp: Boolean = true,//不清楚意义，猜测是用于引导用户下载App
    @SerializedName("is_lock") val isLock: Boolean,
    @SerializedName("is_login") val isLogin: Boolean,
    @SerializedName("is_mobile_bind") val isMobileBind: Boolean,
    @SerializedName("is_vip") val isVip: Boolean,
    @SerializedName("comic") val comic: TrialComicInfo,
    @SerializedName("chapter") val chapter: TrialContentDto,
    @SerializedName("is_banned") val isBanned: Boolean,
)

data class TrialComicInfo(
    @SerializedName("name") val comicName: String,
    @SerializedName("uuid") val comicUuid: String,
    @SerializedName("path_word") val comicPathWord: String,
    @SerializedName("restrict") val restrict: DisplayAndValue,//"restrict":{"value":0,"display":"一般向(免費)"}
)

data class TrialContentDto(
    @SerializedName("index") val chapterIndex: Int,
    @SerializedName("uuid") val chapterUuid: String,
    @SerializedName("count") val total: Int,//所有章节的数量，和Index差1
    @SerializedName("ordered") val ordered: Int,//用于排序
    @SerializedName("size") val size: Int,//本章图片数量
    @SerializedName("name") val chapterName: String,
    @SerializedName("comic_id") val comicUuid: String,
    @SerializedName("comic_path_word") val comicPathWord: String,
    @SerializedName("group_id") val groupId: String?,
    @SerializedName("group_path_word") val groupPathWord: String = "default",
    @SerializedName("type") val type: Int?,
    @SerializedName("news") val news: String?,//"success"，不知道含义
    @SerializedName("datetime_created") val datetimeCreated: String?,//2023-03-05
    @SerializedName("prev") val prevChapter: String?,
    @SerializedName("next") val nextChapter: String?,
    @SerializedName("contents") val urls: List<Map<String, String>>,
    @SerializedName("words") val words: List<Int>?,//[0,2,1]，不知道是什么
    @SerializedName("is_long") val isLong: Boolean?, //false 不知道是什么
)