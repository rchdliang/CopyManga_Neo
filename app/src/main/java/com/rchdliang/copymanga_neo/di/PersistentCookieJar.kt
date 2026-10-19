package com.rchdliang.copymanga_neo.di

import android.content.Context
import com.google.gson.JsonArray
import com.google.gson.JsonParser
import com.rchdliang.copymanga_neo.data.local.security.EncryptedCookieFile
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import java.util.concurrent.ConcurrentHashMap

/**
 * 持久化 Cookie 管理器
 * 使用系统密钥加密并原子保存 Cookie，密文排除在系统备份之外。
 * 同时支持登录后 set-cookie 返回的 token cookie
 */
class PersistentCookieJar(context: Context) : CookieJar {

    private val persistence = EncryptedCookieFile(context)
    private var persistenceError: Exception? = null

    // 内存缓存，按域名分组，避免频繁读取 SharedPreferences
    private val cookieStore = ConcurrentHashMap<String, List<Cookie>>()

    init {
        try {
            loadAllCookies()
            // 未发布的明文 Cookie 不迁入正式凭据库。
            context.deleteSharedPreferences("copymanga_cookies")
            context.deleteSharedPreferences("copymanga_web_cookies_v2")
        } catch (error: Exception) {
            persistenceError = error
            android.util.Log.w("CookieJar", "安全 Cookie 无法恢复，已保留原文件")
        }
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
        check(persistenceError == null) { "安全 Cookie 不可用，请解锁设备并重新启动应用" }
        val encoded = JsonArray().apply { merged.forEach { add(serializeCookie(it)) } }.toString()
        persistence.write(encoded)
        cookieStore.clear()
        cookieStore.putAll(merged.groupBy { it.domain })
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> = cookieStore.values.flatten()
        .filter { it.expiresAt > System.currentTimeMillis() && it.matches(url) }
        .distinctBy { Triple(it.name, it.domain, it.path) }
        .sortedByDescending { it.path.length }

    fun requireReady() {
        check(persistenceError == null) { "安全 Cookie 不可用，原文件已保留" }
    }

    /** 清除所有 Cookie（登出时调用） */
    @Synchronized
    fun clearAll() {
        check(persistenceError == null) { "安全 Cookie 不可用，原文件已保留" }
        persistence.write("[]")
        cookieStore.clear()
    }

    private fun loadAllCookies() {
        val raw = persistence.read() ?: return
        val cookies = JsonParser.parseString(raw).asJsonArray.map {
            require(it.isJsonPrimitive && it.asJsonPrimitive.isString)
            requireNotNull(deserializeCookie(it.asString)) { "Cookie 记录无效" }
        }.filter { it.expiresAt > System.currentTimeMillis() }
        cookieStore.putAll(cookies.groupBy { it.domain })
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
            return null
        }
    }
}