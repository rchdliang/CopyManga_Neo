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
        context.getSharedPreferences("copymanga_web_cookies_v2", Context.MODE_PRIVATE)

    // 内存缓存，按域名分组，避免频繁读取 SharedPreferences
    private val cookieStore = ConcurrentHashMap<String, List<Cookie>>()

    init {
        // 旧会话格式直接清除，防止旧账号 Cookie 混入新的按模式认证存储。
        context.getSharedPreferences("copymanga_cookies", Context.MODE_PRIVATE).edit { clear() }
        // 启动时从 SharedPreferences 恢复 Cookie
        loadAllCookiesFromPrefs()
    }

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        // Cookie 身份包含 name/domain/path；服务端过期值会删除对应旧值。
        val incoming = cookies.associateBy { Triple(it.name, it.domain, it.path) }
        val now = System.currentTimeMillis()
        val merged = (cookieStore.values.flatten().filter {
            Triple(it.name, it.domain, it.path) !in incoming
        } + cookies).filter { it.expiresAt > now }
            .distinctBy { Triple(it.name, it.domain, it.path) }
        cookieStore.clear()
        cookieStore.putAll(merged.groupBy { it.domain })
        prefs.edit {
            clear()
            cookieStore.forEach { (domain, items) ->
                putString(
                    domain,
                    items.joinToString("|") { serializeCookie(it) })
            }
        }
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> = cookieStore.values.flatten()
        .filter { it.expiresAt > System.currentTimeMillis() && it.matches(url) }
        .distinctBy { Triple(it.name, it.domain, it.path) }
        .sortedByDescending { it.path.length }

    /** 清除所有 Cookie（登出时调用） */
    @Synchronized
    fun clearAll() {
        cookieStore.clear()
        prefs.edit { clear() }
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
        // 字段以制表符分隔，末尾保存 hostOnly；旧格式按仅当前主机恢复。
        return "${cookie.name}\t${cookie.value}\t${cookie.domain}\t${cookie.path}\t${cookie.expiresAt}\t${cookie.secure}\t${cookie.httpOnly}\t${cookie.hostOnly}"
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

            if (parts[2].isNotBlank()) {
                if (parts.getOrNull(7)?.toBooleanStrictOrNull() != false) builder.hostOnlyDomain(
                    parts[2]
                )
                else builder.domain(parts[2])
            }
            if (parts[5] == "true") builder.secure()
            if (parts[6] == "true") builder.httpOnly()

            return builder.build()
        } catch (e: Exception) {
            android.util.Log.w("CookieJar", "Failed to deserialize stored cookie", e)
            return null
        }
    }
}