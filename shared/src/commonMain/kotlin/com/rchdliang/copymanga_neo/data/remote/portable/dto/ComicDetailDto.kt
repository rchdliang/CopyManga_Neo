package com.rchdliang.copymanga_neo.data.remote.portable.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 漫画详情响应
 */
@Serializable
data class ComicDetailResponse(
    @SerialName("code") val code: Int,
    @SerialName("message") val message: String? = null,
    @SerialName("results") val results: ComicDetailResult
)

@Serializable
data class ComicDetailResult(
    @SerialName("is_banned") val isBanned: Boolean,
    @SerialName("is_lock") val isLock: Boolean,
    @SerialName("is_login") val isLogin: Boolean,
    @SerialName("is_mobile_bind") val isMobileBind: Boolean,
    @SerialName("is_vip") val isVip: Boolean,
    @SerialName("comic") val comicDetail: ComicDetailDto,
    @SerialName("popular") val popular: Int,
    @SerialName("groups") val groups: Map<String, GroupsDto>,
)
/**
 * 漫画详情
 */
@Serializable
data class ComicDetailDto(
    @SerialName("uuid") val uuid: String,
    @SerialName("b_404") val b404: Boolean,
    @SerialName("b_hidden") val bHidden: Boolean,
    @SerialName("ban") val ban: Int,
    @SerialName("name") val name: String,
    @SerialName("alias") val comicAlias: String,
    @SerialName("path_word") val pathWord: String,
    @SerialName("close_comment") val closeComment: Boolean,
    @SerialName("close_roast") val closeRoast: Boolean,
    @SerialName("free_type") val freeType: DisplayAndValue,
    @SerialName("restrict") val restrict: DisplayAndValue,
    @SerialName("reclass") val reclass: DisplayAndValue,
    @SerialName("females") val females: List<String>? = null,
    @SerialName("males") val males: List<String>? = null,
    @SerialName("clubs") val clubs: List<String>? = null,
    @SerialName("region") val region: DisplayAndValue,
    @SerialName("status") val status: DisplayAndValue,
    @SerialName("author") val author: List<AuthorDto>? = null,
    @SerialName("theme") val theme: List<ThemeDto>? = null,
    @SerialName("parodies") val parodies: List<String>? = null,
    @SerialName("brief") val brief: String? = null,
    @SerialName("datetime_updated") val lastUpdate: String? = null,
    @SerialName("cover") val cover: String? = null,
    @SerialName("last_chapter") val lastChapter: LastChapterDto? = null,
    @SerialName("popular") val popular: Int? = null,
    @SerialName("channel") val channel: Int? = null,//Unknown Type
)

@Serializable
data class LastChapterDto(
    @SerialName("uuid") val lastChapterId: String,
    @SerialName("name") val lastChapterName: String,
)

@Serializable
data class DisplayAndValue(
    @SerialName("display") val display: String,
    @SerialName("value") val value: Int
)

@Serializable
data class GroupsDto(
    @SerialName("path_word") val pathWord: String,
    @SerialName("count") val total: Int,
    @SerialName("name") val name: String,
)