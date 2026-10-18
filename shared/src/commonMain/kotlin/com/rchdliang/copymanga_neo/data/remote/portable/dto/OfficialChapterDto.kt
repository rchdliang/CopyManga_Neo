package com.rchdliang.copymanga_neo.data.remote.portable.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** App chapter2 返回完整图片 URL；字段可空以识别空结果而非当作成功。 */
@Serializable
data class OfficialChapterResponse(
    @SerialName("code") val code: Int,
    @SerialName("message") val message: String? = null,
    @SerialName("results") val results: OfficialChapterResult? = null
)

@Serializable
data class OfficialChapterResult(
    @SerialName("is_lock") val isLock: Boolean,
    // 下载授权响应可能省略此字段，保持旧 Gson 的缺省 false 行为。
    @SerialName("is_banned") val isBanned: Boolean = false,
    @SerialName("chapter") val chapter: OfficialChapterDto? = null,
    @SerialName("is_login") val isLogin: Boolean? = null,
    @SerialName("day_downloads") val dayDownloads: Int? = null
)

@Serializable
data class OfficialChapterDto(
    @SerialName("uuid") val uuid: String? = null,
    @SerialName("name") val name: String? = null,
    @SerialName("prev") val prev: String? = null,
    @SerialName("next") val next: String? = null,
    @SerialName("size") val size: Int? = null,
    @SerialName("contents") val contents: List<OfficialChapterImageDto>? = null,
    @SerialName("words") val words: List<Int>? = null
)

@Serializable
data class OfficialChapterImageDto(@SerialName("url") val url: String? = null)
