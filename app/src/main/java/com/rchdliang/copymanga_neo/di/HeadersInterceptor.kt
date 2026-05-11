package com.rchdliang.copymanga_neo.di

import okhttp3.Interceptor
import okhttp3.Response
import java.util.UUID

/**
 * 请求头拦截器
 * 模拟真实 iOS 浏览器请求，避免被网站反盗版机制封禁
 */
class HeadersInterceptor : Interceptor {

    // 模拟 iOS Safari/Edge 浏览器 User-Agent
    private val userAgent = "Mozilla/5.0 (iPhone; CPU iPhone OS 18_5 like Mac OS X) " +
            "AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.5 Mobile/15E148 " +
            "Safari/604.1 Edg/146.0.0.0"

    // 生成随机设备 ID，用于标识唯一设备
    private val deviceId = UUID.randomUUID().toString()

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()

        // 只对 www 域名添加 Referer 和 Origin（API 域名不需要）
        val isWwwDomain = original.url.host.contains("www.2026copy.com")

        val requestBuilder = original.newBuilder()
            // 基础请求头
            .header("User-Agent", userAgent)
            .header("Accept", "application/json, text/plain, */*")
            .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
//            .header("Accept-Encoding", "gzip, deflate, br, zstd")
            .header("X-Device-ID", deviceId)
            .header("X-Requested-With", "XMLHttpRequest")

        // 仅对 www 域名添加 Referer 和 Origin
        if (isWwwDomain) {
            requestBuilder
                .header("Origin", "https://www.2026copy.com")
                .header("Referer", "https://www.2026copy.com/h5")
        }

        return chain.proceed(requestBuilder.build())
    }
}
