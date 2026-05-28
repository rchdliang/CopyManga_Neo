package com.rchdliang.copymanga_neo.di

import okhttp3.Interceptor
import okhttp3.Response

/**
 * 请求头拦截器
 * 仅负责通用头和风控相关的浏览器模拟，业务头（version / region / platform / webp）
 * 交由 Retrofit 的 @Header / @HeaderMap 注解管理。
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
        val url = original.url.toString()

        // 识别请求类型
        val isApi = url.contains("api.2026copy.com")
        // 识别章节 HTML 页面请求 (加载图片前置请求)
        val isChapterHtml = url.contains("www.2026copy.com/comic/") && url.contains("/chapter/")

        val requestBuilder = original.newBuilder()

        when {
            isApi -> {
                // API 请求：仅自动设置 User-Agent，
                // 其余头（Accept / version / region / platform / webp）由 @HeaderMap 管理
                requestBuilder.header("User-Agent", userAgent)
            }

            isChapterHtml -> {
                // 章节 HTML 请求：严格模拟浏览器 headers 绕过风控
                requestBuilder.header(
                    "User-Agent",
                    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/86.0.4240.75 Safari/537.36 Edg/86.0.622.38"
                )
                requestBuilder.header(
                    "Accept",
                    "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7"
                )
                requestBuilder.header("Accept-Language", "zh-CN,zh;q=0.9,en-US;q=0.8,en;q=0.7")
                requestBuilder.header("Upgrade-Insecure-Requests", "1")

                // 添加客户端提示头 (sec-ch-ua)
                requestBuilder.header(
                    "sec-ch-ua",
                    "\"Android WebView\";v=\"147\", \"Not.A/Brand\";v=\"8\", \"Chromium\";v=\"147\""
                )
                requestBuilder.header("sec-ch-ua-mobile", "?0")
                requestBuilder.header("sec-ch-ua-platform", "\"Android\"")

                // Fetch Metadata
                requestBuilder.header("Sec-Fetch-Site", "none")
                requestBuilder.header("Sec-Fetch-Mode", "navigate")
                requestBuilder.header("Sec-Fetch-User", "?1")
                requestBuilder.header("Sec-Fetch-Dest", "document")
            }

            else -> {
                // 其他 HTML 或资源请求
                requestBuilder.header(
                    "User-Agent",
                    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/86.0.4240.75 Safari/537.36 Edg/86.0.622.38"
                )
                requestBuilder.header("Accept-Language", "zh-CN,zh;q=0.9,en-US;q=0.8,en;q=0.7")
            }
        }

        // Cookie 处理：非 API 域名（www 等）的 Cookie 中确保 webp=1
        if (!isApi && url.contains("2026copy.com")) {
            val existingCookie = original.header("Cookie") ?: ""
            val newCookie = when {
                existingCookie.isBlank() -> "webp=1"
                !existingCookie.contains("webp=1") -> "$existingCookie; webp=1"
                else -> existingCookie
            }
            requestBuilder.header("Cookie", newCookie)
        }

        return chain.proceed(requestBuilder.build())
    }
}