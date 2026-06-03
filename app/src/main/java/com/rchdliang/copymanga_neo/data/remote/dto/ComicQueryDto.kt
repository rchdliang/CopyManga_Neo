package com.rchdliang.copymanga_neo.data.remote.dto

import com.google.gson.annotations.SerializedName

data class ComicQueryResponse(
    @SerializedName("code") val code: Int,
    @SerializedName("message") val message: String?,
    @SerializedName("results") val results: ComicQueryResult?
)

data class ComicQueryResult(
    @SerializedName("browse") val browse: BrowseDto?,
    @SerializedName("collect") val collect: Int?,
    @SerializedName("is_lock") val isLock: Boolean,
    @SerializedName("is_login") val isLogin: Boolean,
    @SerializedName("is_mobile_bind") val isMobileBind: Boolean,
    @SerializedName("is_vip") val isVip: Boolean
)

data class SimpleResponse(
    @SerializedName("code") val code: Int,
    @SerializedName("message") val message: String?
)
