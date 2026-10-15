package com.rchdliang.copymanga_neo.data.remote.portable.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 注册成功不一定带有效 Token，随后必须调用登录接口。 */
@Serializable
data class RegistrationResponse(
    @SerialName("code") val code: Int,
    @SerialName("message") val message: String? = null,
    @SerialName("results") val results: RegistrationResult? = null
)

@Serializable
data class RegistrationResult(
    @SerialName("user_id") val userId: String? = null,
    @SerialName("uuid") val uuid: String? = null
)
