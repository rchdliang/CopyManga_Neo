package com.rchdliang.copymanga_neo.data.remote.dto

import com.google.gson.annotations.SerializedName

/** App chapter2 返回完整图片 URL；字段可空以识别空结果而非当作成功。 */
data class OfficialChapterResponse(
    @SerializedName("code") val code: Int,
    @SerializedName("message") val message: String?,
    @SerializedName("results") val results: OfficialChapterResult?
)

data class OfficialChapterResult(
    @SerializedName("is_lock") val isLock: Boolean,
    @SerializedName("is_banned") val isBanned: Boolean,
    @SerializedName("chapter") val chapter: OfficialChapterDto?
)

data class OfficialChapterDto(
    @SerializedName("uuid") val uuid: String?,
    @SerializedName("name") val name: String?,
    @SerializedName("prev") val prev: String?,
    @SerializedName("next") val next: String?,
    @SerializedName("size") val size: Int?,
    @SerializedName("contents") val contents: List<OfficialChapterImageDto>?,
    @SerializedName("words") val words: List<Int>?
)

data class OfficialChapterImageDto(@SerializedName("url") val url: String?)
