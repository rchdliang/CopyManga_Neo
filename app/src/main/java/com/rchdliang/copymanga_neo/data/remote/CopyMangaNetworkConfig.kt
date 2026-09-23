package com.rchdliang.copymanga_neo.data.remote

/**
 * 拷貝漫畫当前网络端点与网页端请求标识。
 *
 * 域名和 platform 集中维护，避免 Retrofit 与请求头判断各自保留旧值。
 */
internal object CopyMangaNetworkConfig {
    const val WWW_HOST = "www.copy3000.com"
    const val WWW_BASE_URL = "https://www.copy3000.com/"
    const val WWW_ORIGIN = "https://www.copy3000.com"

    const val API_HOST = "api.copy4000.com"
    const val API_BASE_URL = "https://api.copy4000.com/"

    const val WEB_PLATFORM = 1
    const val WEB_PLATFORM_HEADER = "1"
    const val WEB_VERSION = "2026.08.24"

    private const val REGION = "1"
    private const val WEBP = "1"
    private const val WEB_REQUESTED_WITH = "top.fumiama.copymangaweb"

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
    fun chapterCatalogHeaders(pathWord: String): Map<String, String> = mapOf(
        "Accept" to "*/*",
        "Content-Type" to "application/x-www-form-urlencoded;charset=UTF-8",
        "X-Requested-With" to WEB_REQUESTED_WITH,
        "dnts" to "3",
        "Referer" to "$WWW_ORIGIN/comic/$pathWord",
        "X-No-Auth" to "1"
    )
}
