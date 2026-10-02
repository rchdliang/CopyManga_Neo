package com.rchdliang.copymanga_neo.di

import com.rchdliang.copymanga_neo.data.remote.isCopyApi
import com.rchdliang.copymanga_neo.data.remote.isCopyWww
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
        val path = original.url.encodedPath

        // 识别请求类型
        val isApi = original.isCopyApi()
        // 漫画详情和章节 HTML 页面共用网页导航请求头。
        val isChapterHtml = original.isCopyWww() && path.startsWith("/comic/")

        val requestBuilder = original.newBuilder()
        when {
            isApi -> {
                // API 请求：仅自动设置 User-Agent，
                // 其余头（Accept / version / region / platform / webp）由 @HeaderMap 管理
                requestBuilder.header("User-Agent", userAgent)
            }

            isChapterHtml -> {
                // HTML 请求头对照 Lite 抓包；这些标识不代表实际执行 WebView。
                requestBuilder.header(
                    "User-Agent",
                    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/86.0.4240.75 Safari/537.36 Edg/86.0.622.38"
                )
                requestBuilder.header(
                    "Accept",
                    "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7"
                )
                requestBuilder.header("Accept-Language", "zh-CN,zh;q=0.9,en-US;q=0.8,en;q=0.7")
                requestBuilder.header(
                    "sec-ch-ua",
                    "\"Chromium\";v=\"154\", \"Android WebView\";v=\"154\", \"Not A(Brand\";v=\"99\""
                )
                requestBuilder.header("sec-ch-ua-mobile", "?0")
                requestBuilder.header("sec-ch-ua-platform", "\"Android\"")
                requestBuilder.header("Sec-Fetch-Site", "none")
                requestBuilder.header("Sec-Fetch-Mode", "navigate")
                requestBuilder.header("Sec-Fetch-User", "?1")
                requestBuilder.header("Sec-Fetch-Dest", "document")
                requestBuilder.header("Priority", "u=0, i")
                requestBuilder.header("Upgrade-Insecure-Requests", "1")
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

        return chain.proceed(requestBuilder.build())
    }
}
