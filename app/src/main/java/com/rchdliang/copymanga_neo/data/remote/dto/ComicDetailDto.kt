package com.rchdliang.copymanga_neo.data.remote.dto

import androidx.compose.runtime.Immutable
import com.google.gson.annotations.SerializedName

/**
 * 漫画详情响应
 */
data class ComicDetailResponse(
    @SerializedName("code") val code: Int,
    @SerializedName("message") val message: String?,
    @SerializedName("results") val results: ComicDetailResult
)

data class ComicDetailResult(
    @SerializedName("is_banned") val isBanned: Boolean,
    @SerializedName("is_lock") val isLock: Boolean,
    @SerializedName("is_login") val isLogin: Boolean,
    @SerializedName("is_mobile_bind") val isMobileBind: Boolean,
    @SerializedName("is_vip") val isVip: Boolean,
    @SerializedName("comic") val comicDetail: ComicDetailDto,
    @SerializedName("popular") val popular: Int,
    @SerializedName("groups") val groups: Map<String, GroupsDto>,
)
/**
 * 漫画详情
 */
@Immutable
data class ComicDetailDto(
    @SerializedName("uuid") val uuid: String,
    @SerializedName("b_404") val b404: Boolean,
    @SerializedName("b_hidden") val bHidden: Boolean,
    @SerializedName("ban") val ban: Int,
    @SerializedName("name") val name: String,
    @SerializedName("alias") val comicAlias: String,
    @SerializedName("path_word") val pathWord: String,
    @SerializedName("close_comment") val closeComment: Boolean,
    @SerializedName("close_roast") val closeRoast: Boolean,
    @SerializedName("free_type") val freeType: DisplayAndValue,
    @SerializedName("restrict") val restrict: DisplayAndValue,
    @SerializedName("reclass") val reclass: DisplayAndValue,
    @SerializedName("females") val females: List<String>?,
    @SerializedName("males") val males: List<String>?,
    @SerializedName("clubs") val clubs: List<String>?,
    @SerializedName("region") val region: DisplayAndValue,
    @SerializedName("status") val status: DisplayAndValue,
    @SerializedName("author") val author: List<AuthorDto>?,
    @SerializedName("theme") val theme: List<ThemeDto>?,
    @SerializedName("parodies") val parodies: List<String>?,
    @SerializedName("brief") val brief: String?,
    @SerializedName("datetime_updated") val lastUpdate: String?,
    @SerializedName("cover") val cover: String?,
    @SerializedName("last_chapter") val lastChapter: LastChapterDto?,
    @SerializedName("popular") val popular: Int?,
    @SerializedName("channel") val channel: Int?,//Unknown Type
)

@Immutable
data class LastChapterDto(
    @SerializedName("uuid") val lastChapterId: String,
    @SerializedName("name") val lastChapterName: String,
)

data class DisplayAndValue(
    @SerializedName("display") val display: String,
    @SerializedName("value") val value: Int
)

@Immutable
data class GroupsDto(
    @SerializedName("path_word") val pathWord: String,
    @SerializedName("count") val total: Int,
    @SerializedName("name") val name: String,
)