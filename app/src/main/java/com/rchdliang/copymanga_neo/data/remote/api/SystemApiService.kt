package com.rchdliang.copymanga_neo.data.remote.api

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.HeaderMap
import retrofit2.http.Query

/**
 * 系统配置 API 服务
 * 获取网站最新 API 域名、CDN 配置等信息
 */
interface SystemApiService {

    companion object {
        /**
         * 标记该请求不需要 Authorization。
         */
        @JvmStatic
        fun noAuthHeaders(): Map<String, String> = mapOf("X-No-Auth" to "1")
    }

    /**
     * 获取系统网络配置
     * @param platform 平台标识 (1=Web/H5)
     */
    @GET("api/v3/system/network21")
    suspend fun getNetworkConfig(
        @Query("platform") platform: Int = 1,
        @HeaderMap headers: Map<String, String> = noAuthHeaders()
    ): Response<NetworkConfigResponse>
}

/**
 * WWW API 服务
 * 用于访问 www.2026copy.com 域名，获取 cookies
 */
interface WwwApiService {

    companion object {
        @JvmStatic
        fun noAuthHeaders(): Map<String, String> = mapOf("X-No-Auth" to "1")
    }

    /**
     * 获取 H5 页面（用于建立 cookies）
     */
    @GET("h5")
    suspend fun getH5Page(
        @HeaderMap headers: Map<String, String> = noAuthHeaders()
    ): Response<okhttp3.ResponseBody>
}

/**
 * 网络配置响应
 */
data class NetworkConfigResponse(
    @SerializedName("code") val code: Int,
    @SerializedName("message") val message: String?,
    @SerializedName("results") val results: NetworkConfig
)

/**
 * 网络配置详情
 */
data class NetworkConfig(
    @SerializedName("static") val staticDomain: String,           // 静态资源 CDN 域名
    @SerializedName("image") val imageDomain: String?,            // 图片 CDN 域名
    @SerializedName("api") val apiDomains: List<List<String>>,    // API 域名列表
    @SerializedName("share") val shareDomains: List<String>,      // 分享域名
    @SerializedName("day_downloads_total") val dailyDownloadLimit: Int,  // 每日下载限制
    @SerializedName("in_mainland") val inMainland: Boolean        // 是否在大陆
)