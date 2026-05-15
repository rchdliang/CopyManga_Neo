package com.rchdliang.copymanga_neo.di

import okhttp3.Interceptor
import okhttp3.Response

/**
 * 请求头拦截器
 * 模拟已知可用的第三方客户端 Header，绕过反盗版 Code 210 限制
 */
class HeadersInterceptor : Interceptor {

    // 抓包自正常运行的第三方客户端的 User-Agent
    private val userAgent =
        "Mozilla/5.0 (Linux; Android 16; 24018RPACC Build/BP2A.250605.031.A3; wv) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/147.0.7727.137 Safari/537.36"

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()

        val requestBuilder = original.newBuilder()
            // 基础标识
            .header("User-Agent", userAgent)
            .header("Accept", "application/json")
            .header("Accept-Language", "zh-CN,zh;q=0.9,en-US;q=0.8,en;q=0.7")

            // 业务关键 Header
            .header("version", "2026.04.30")
            .header("platform", "1")
            .header("region", "1")
            .header("webp", "1")
            // 注意：第三方客户端请求中包含此 Header，可能是校验点
            .header("Content-Encoding", "gzip, compress, br")

            // 客户端环境模拟 (WebView)
            .header("sec-ch-ua-platform", "\"Android\"")
            .header(
                "sec-ch-ua",
                "\"Android WebView\";v=\"147\", \"Not.A/Brand\";v=\"8\", \"Chromium\";v=\"147\""
            )
            .header("sec-ch-ua-mobile", "?0")
            .header("X-Requested-With", "com.copymanga.app")

            // 跨域相关
            .header("Origin", "https://2026copy.com")
            .header("Sec-Fetch-Site", "cross-site")
            .header("Sec-Fetch-Mode", "cors")
            .header("Sec-Fetch-Dest", "empty")

        // 注意：Referer 也可以根据需要添加，通常指向详情页或首页
        if (original.url.encodedPath.contains("comic2")) {
            val pathWord = original.url.pathSegments.lastOrNull() ?: ""
            requestBuilder.header("Referer", "https://www.copy20.com/comic/$pathWord")
        } else {
            requestBuilder.header("Referer", "https://www.copy20.com/")
        }

        return chain.proceed(requestBuilder.build())
    }
}
