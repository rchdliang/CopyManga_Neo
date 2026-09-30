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
        // 识别章节 HTML 页面请求 (加载图片前置请求)
        val isChapterHtml = original.isCopyWww() && path.startsWith("/comic/")

        val requestBuilder = original.newBuilder()
        when {
            isApi -> {
                // API 请求：仅自动设置 User-Agent，
                // 其余头（Accept / version / region / platform / webp）由 @HeaderMap 管理
                requestBuilder.header("User-Agent", userAgent)
            }

            isChapterHtml -> {
                // 章节 HTML 请求：模拟PC浏览器 ua 绕过风控
                requestBuilder.header(
                    "User-Agent",
                    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/86.0.4240.75 Safari/537.36 Edg/86.0.622.38"
                )
                requestBuilder.header(
                    "Accept",
                    "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7"
                )
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
