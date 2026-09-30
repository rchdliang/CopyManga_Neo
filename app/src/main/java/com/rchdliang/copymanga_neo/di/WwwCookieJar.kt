package com.rchdliang.copymanga_neo.di

import com.rchdliang.copymanga_neo.data.local.NetworkSettingsStore
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/** 仅 WWW 站点使用持久化 Cookie；API 和外部资源不参与 CookieJar。 */
internal class WwwCookieJar(
    private val delegate: PersistentCookieJar,
    private val settings: NetworkSettingsStore
) : CookieJar {
    private fun isWww(url: HttpUrl): Boolean = url.isHttps &&
            url.host.equals(settings.settings.value.wwwHost, ignoreCase = true) &&
            !url.encodedPath.startsWith("/api/")

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        if (!isWww(url)) return emptyList()
        val webp = Cookie.Builder().name("webp").value("1")
            .hostOnlyDomain(url.host).path("/").build()
        return delegate.loadForRequest(url).filterNot { it.name == "webp" } + webp
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (isWww(url)) delegate.saveFromResponse(url, cookies)
    }
}
