package com.rchdliang.copymanga_neo.data.remote.portable.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ComicQueryResponse(
    @SerialName("code") val code: Int,
    @SerialName("message") val message: String? = null,
    @SerialName("results") val results: ComicQueryResult? = null
)

@Serializable
data class ComicQueryResult(
    @SerialName("browse") val browse: BrowseDto? = null,
    @SerialName("collect") val collect: Int? = null,
    @SerialName("is_lock") val isLock: Boolean,
    @SerialName("is_login") val isLogin: Boolean,
    @SerialName("is_mobile_bind") val isMobileBind: Boolean,
    @SerialName("is_vip") val isVip: Boolean
)

@Serializable
data class SimpleResponse(
    @SerialName("code") val code: Int,
    @SerialName("message") val message: String? = null
)
