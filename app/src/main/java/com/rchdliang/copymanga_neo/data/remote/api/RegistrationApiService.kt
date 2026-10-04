package com.rchdliang.copymanga_neo.data.remote.api

import com.rchdliang.copymanga_neo.data.remote.CopyMangaNetworkConfig
import com.rchdliang.copymanga_neo.data.remote.dto.RegistrationResponse
import retrofit2.Response
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.HeaderMap
import retrofit2.http.POST

/** 注册会创建账号，使用不自动重试或重定向的客户端。 */
interface RegistrationApiService {
    /** 网页注册只包含这五个字段，原始密码不使用登录的盐或 Base64。 */
    @FormUrlEncoded
    @POST("api/v3/register")
    suspend fun registerWeb(
        @Field("username") username: String,
        @Field("password") rawPassword: String,
        @Field("source") source: String = "freeSite",
        @Field("version") version: String = CopyMangaNetworkConfig.WEB_VERSION,
        @Field("platform") platform: Int = CopyMangaNetworkConfig.WEB_PLATFORM,
        @HeaderMap headers: Map<String, String> = CopyMangaNetworkConfig.noAuthApiHeaders()
    ): Response<RegistrationResponse>

    /** App 的空验证字段及设备公共字段与网页注册分开构造。 */
    @FormUrlEncoded
    @POST("api/v3/register")
    suspend fun register(
        @Field("username") username: String,
        @Field("password") rawPassword: String,
        @Field("mobile") mobile: String = "",
        @Field("code") code: String = "",
        @Field("invite_code") inviteCode: String = "",
        @Field("question") question: String = "",
        @Field("answer") answer: String = "",
        @HeaderMap headers: Map<String, String> = CopyMangaNetworkConfig.noAuthApiHeaders()
    ): Response<RegistrationResponse>
}
