package com.rchdliang.copymanga_neo.data.remote.portable

import com.rchdliang.copymanga_neo.domain.model.NetworkDefaults

object CopyMangaNetworkConfig {
    const val WWW_HOST = NetworkDefaults.WWW_HOST
    const val WWW_BASE_URL = "https://$WWW_HOST/"
    const val WWW_ORIGIN = "https://$WWW_HOST"
    const val API_HOST = NetworkDefaults.API_HOST
    const val API_BASE_URL = "https://$API_HOST/"
    const val WEB_PLATFORM = 1
    const val WEB_PLATFORM_HEADER = "1"
    const val WEB_VERSION = NetworkDefaults.WEB_VERSION
    // Portable equivalent of HeadersInterceptor's API identifier; never invent Android device data.
    const val WEB_API_USER_AGENT = "CopyManga_Neo com.copymanga.app"
    internal fun transportHeaders(www: Boolean, path: String): Map<String, String> {
        if (!www) return mapOf("User-Agent" to WEB_API_USER_AGENT)
        val browser = mapOf("User-Agent" to RESOURCE_USER_AGENT, "Accept-Language" to RESOURCE_LANGUAGE)
        if (!path.startsWith("/comic/")) return browser
        // Mirrors original HeadersInterceptor: detail and chapter HTML are browser navigations.
        return browser + mapOf(
            "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7",
            "sec-ch-ua" to "\"Chromium\";v=\"154\", \"Android WebView\";v=\"154\", \"Not A(Brand\";v=\"99\"",
            "sec-ch-ua-mobile" to "?0", "sec-ch-ua-platform" to "\"Android\"",
            "Sec-Fetch-Site" to "none", "Sec-Fetch-Mode" to "navigate", "Sec-Fetch-User" to "?1",
            "Sec-Fetch-Dest" to "document", "Priority" to "u=0, i", "Upgrade-Insecure-Requests" to "1"
        )
    }
    fun apiHeaders(): Map<String, String> = mapOf(
        "Accept" to "application/json", "Accept-Language" to "zh-CN,zh;q=0.9,en-US;q=0.8,en;q=0.7",
        "version" to WEB_VERSION, "platform" to "1", "region" to "1", "webp" to "1",
        "Origin" to WWW_ORIGIN, "X-Requested-With" to "com.rchdliang.copymanga_neo"
    )
    fun noAuthApiHeaders() = apiHeaders() + ("X-No-Auth" to "1")
    fun chapterCatalogHeaders(pathWord: String, dnts: String) = mapOf(
        "Accept" to "*/*", "Content-Type" to "application/x-www-form-urlencoded;charset=UTF-8",
        "X-Requested-With" to "com.rchdliang.copymanga_neo", "dnts" to dnts,
        "Referer" to "$WWW_ORIGIN/comic/$pathWord", "X-No-Auth" to "1"
    )
}
