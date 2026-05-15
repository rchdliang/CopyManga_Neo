package com.rchdliang.copymanga_neo.di

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import java.util.concurrent.ConcurrentHashMap

/**
 * 匿名访问 Cookie 管理器
 * 持久化存储必要的 Cookie，避免每次启动都重新获取
 */
class AnonymousCookieJar : CookieJar {

    // Cookie 存储，按域名分组
    private val cookieStore = ConcurrentHashMap<String, List<Cookie>>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        // 保存所有返回的 Cookie，帮助排查反盗版校验问题
        cookieStore[url.host] = cookies
        cookies.forEach {
            android.util.Log.v("CookieJar", "Save from ${url.host}: ${it.name}=${it.value}")
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val cookies = cookieStore[url.host] ?: emptyList()
        if (cookies.isNotEmpty()) {
            android.util.Log.v(
                "CookieJar",
                "Load for ${url.host}: ${cookies.joinToString("; ") { "${it.name}=${it.value}" }}"
            )
        }
        return cookies
    }
}
