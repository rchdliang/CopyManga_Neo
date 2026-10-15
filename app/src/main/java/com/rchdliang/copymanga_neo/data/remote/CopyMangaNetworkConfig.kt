package com.rchdliang.copymanga_neo.data.remote

import com.rchdliang.copymanga_neo.domain.model.NetworkDefaults

/**
 * 拷貝漫畫当前网络端点与网页端请求标识。
 *
 * 域名和 platform 集中维护，避免 Retrofit 与请求头判断各自保留旧值。
 */
internal object CopyMangaNetworkConfig {
    const val WWW_HOST = NetworkDefaults.WWW_HOST
    const val WWW_BASE_URL = "https://$WWW_HOST/"
    const val WWW_ORIGIN = "https://$WWW_HOST"

    const val API_HOST = NetworkDefaults.API_HOST
    const val API_BASE_URL = "https://$API_HOST/"

    const val WEB_PLATFORM = 1
    const val WEB_PLATFORM_HEADER = "1"
    const val WEB_VERSION = NetworkDefaults.WEB_VERSION

    private const val REGION = "1"
    private const val WEBP = "1"
    // Keep the existing wire identity, matching the portable client. Installation
    // variants must not silently change API/catalog request headers.
    private const val WEB_REQUESTED_WITH = "com.rchdliang.copymanga_neo"

    /** 新版网页端抓包中所有 API 请求共有的业务头。 */
    fun apiHeaders(): Map<String, String> = mapOf(
        "Accept" to "application/json",
        "Accept-Language" to "zh-CN,zh;q=0.9,en-US;q=0.8,en;q=0.7",
        "version" to WEB_VERSION,
        "platform" to WEB_PLATFORM_HEADER,
        "region" to REGION,
        "webp" to WEBP,
        "Origin" to WWW_ORIGIN,
        "X-Requested-With" to WEB_REQUESTED_WITH
    )

    /** API 业务头加仅供本地认证拦截器识别的免认证标记。 */
    fun noAuthApiHeaders(): Map<String, String> = apiHeaders() + ("X-No-Auth" to "1")

    /** 章节目录沿用网页请求头；不向 WWW 站点附加 API Token。 */
    fun chapterCatalogHeaders(pathWord: String, dnts: String): Map<String, String> = mapOf(
        "Accept" to "*/*",
        "Content-Type" to "application/x-www-form-urlencoded;charset=UTF-8",
        "X-Requested-With" to WEB_REQUESTED_WITH,
        "dnts" to dnts,
        "Referer" to "$WWW_ORIGIN/comic/$pathWord",
        "X-No-Auth" to "1"
    )
}
