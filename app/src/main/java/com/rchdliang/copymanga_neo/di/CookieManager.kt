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

    // 需要持久化的 Cookie 名称
    private val essentialCookies = setOf("csrftoken", "webp")
    private val analyticsCookies = setOf("_ga", "_gid", "_ga_SS4LL7K6CL")

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        // 只保存必要的 Cookie
        val filtered = cookies.filter {
            it.name in essentialCookies || it.name in analyticsCookies
        }

        if (filtered.isNotEmpty()) {
            cookieStore[url.host] = filtered
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        // 返回该域名下存储的 Cookie
        return cookieStore[url.host] ?: emptyList()
    }
}
