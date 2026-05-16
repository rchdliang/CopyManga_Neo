package com.rchdliang.copymanga_neo.di

import okhttp3.Interceptor
import okhttp3.Response

/**
 * 请求头拦截器
 * 保留 CopyManga API 校验所需的业务头，移除浏览器伪装头
 */
class HeadersInterceptor : Interceptor {

    // 包名使用官方客户端标识，其余使用真实设备信息
    private val userAgent = buildUserAgent()

    private fun buildUserAgent(): String {
        val defaultAgent = System.getProperty("http.agent") ?: ""
        // 在真实设备 UA 末尾追加官方包名标识
        return if (defaultAgent.isNotBlank()) {
            "$defaultAgent com.copymanga.app"
        } else {
            // fallback：如果系统未提供 UA，构造一个基本的
            "Dalvik/2.1.0 (Linux; U; Android) com.copymanga.app"
        }
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()

        val requestBuilder = original.newBuilder()
            // 通用 HTTP 头
            .header("User-Agent", userAgent)
            .header("Accept", "application/json")
            .header("Accept-Language", "zh-CN,zh;q=0.9,en-US;q=0.8,en;q=0.7")

            // CopyManga 业务头
            .header("version", "2026.04.30") // TODO: 后续改为从 JS 文件动态读取
            .header("platform", "1")
            .header("region", "1")
            .header("webp", "1")
            .header("X-Requested-With", "com.copymanga.app")

        // 已移除的浏览器伪装头：
        // - Content-Encoding（响应头，不应出现在请求中）
        // - sec-ch-ua-platform / sec-ch-ua / sec-ch-ua-mobile（Chrome Client Hints）
        // - Sec-Fetch-Site / Sec-Fetch-Mode / Sec-Fetch-Dest（Fetch Metadata）
        // - Origin（CORS 请求头）
        // - Referer（浏览器导航上下文）

        return chain.proceed(requestBuilder.build())
    }
}