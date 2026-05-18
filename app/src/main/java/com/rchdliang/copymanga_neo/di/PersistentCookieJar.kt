package com.rchdliang.copymanga_neo.di

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import java.util.concurrent.ConcurrentHashMap

/**
 * 持久化 Cookie 管理器
 * 使用 SharedPreferences 存储 Cookie，确保重启后不丢失
 * 同时支持登录后 set-cookie 返回的 token cookie
 */
class PersistentCookieJar(context: Context) : CookieJar {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("copymanga_cookies", Context.MODE_PRIVATE)

    // 内存缓存，按域名分组，避免频繁读取 SharedPreferences
    private val cookieStore = ConcurrentHashMap<String, List<Cookie>>()

    init {
        // 启动时从 SharedPreferences 恢复 Cookie
        loadAllCookiesFromPrefs()
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        // 合并新旧 Cookie：新 Cookie 优先，旧 Cookie 中不在新列表里的也保留
        val existing = cookieStore[url.host] ?: emptyList()
        val merged =
            (existing.filter { old -> cookies.none { new -> new.name == old.name } } + cookies)
                .filter { it.expiresAt >= System.currentTimeMillis() || it.expiresAt == -1L }

        cookieStore[url.host] = merged
        saveCookiesToPrefs(url.host, merged)

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

    /** 清除所有 Cookie（登出时调用） */
    fun clearAll() {
        cookieStore.clear()
        prefs.edit { clear() }
    }

    private fun saveCookiesToPrefs(host: String, cookies: List<Cookie>) {
        val serialized = cookies.joinToString("|") { serializeCookie(it) }
        prefs.edit { putString(host, serialized) }
    }

    private fun loadAllCookiesFromPrefs() {
        val allEntries = prefs.all
        for ((host, value) in allEntries) {
            if (value is String && value.isNotBlank()) {
                val cookies = value.split("|").mapNotNull { deserializeCookie(it) }
                if (cookies.isNotEmpty()) {
                    cookieStore[host] = cookies
                }
            }
        }
    }

    private fun serializeCookie(cookie: Cookie): String {
        // 格式：name;value;domain;path;expiresAt;secure;httpOnly
        return "${cookie.name}\t${cookie.value}\t${cookie.domain}\t${cookie.path}\t${cookie.expiresAt}\t${cookie.secure}\t${cookie.httpOnly}"
    }

    private fun deserializeCookie(raw: String): Cookie? {
        val parts = raw.split("\t")
        if (parts.size < 7) return null
        try {
            val builder = Cookie.Builder()
                .name(parts[0])
                .value(parts[1])
                .path(parts[3])
                .expiresAt(parts[4].toLong())

            if (parts[2].isNotBlank()) builder.domain(parts[2])
            if (parts[5] == "true") builder.secure()
            if (parts[6] == "true") builder.httpOnly()

            return builder.build()
        } catch (e: Exception) {
            android.util.Log.w("CookieJar", "Failed to deserialize cookie: $raw", e)
            return null
        }
    }
}