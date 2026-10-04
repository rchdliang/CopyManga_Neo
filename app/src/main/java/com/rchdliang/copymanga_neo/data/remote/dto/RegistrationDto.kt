package com.rchdliang.copymanga_neo.data.remote.dto

import com.google.gson.annotations.SerializedName

/** 注册成功不一定带有效 Token，随后必须调用登录接口。 */
data class RegistrationResponse(
    @SerializedName("code") val code: Int,
    @SerializedName("message") val message: String?,
    @SerializedName("results") val results: RegistrationResult?
)

data class RegistrationResult(
    @SerializedName("user_id") val userId: String?,
    @SerializedName("uuid") val uuid: String?
)
