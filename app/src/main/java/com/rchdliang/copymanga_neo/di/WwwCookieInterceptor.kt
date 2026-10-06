package com.rchdliang.copymanga_neo.di

import com.rchdliang.copymanga_neo.data.local.AuthDataStore
import com.rchdliang.copymanga_neo.data.local.NetworkSettingsStore
import com.rchdliang.copymanga_neo.data.remote.NetworkRequestContext
import com.rchdliang.copymanga_neo.domain.model.RequestMode
import okhttp3.Cookie
import okhttp3.Interceptor
import okhttp3.Response

/** Web Cookie 单独保留；切换、退出或重新登录后的旧响应不能回写。 */
internal class WwwCookieInterceptor(
    private val delegate: PersistentCookieJar,
    private val settings: NetworkSettingsStore,
    private val auth: AuthDataStore
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val context = request.tag(NetworkRequestContext::class.java)
        val web = context?.settings?.requestMode == RequestMode.WEB && context.www &&
                request.url.isHttps && !request.url.encodedPath.startsWith("/api/")
        if (!web) return chain.proceed(request.newBuilder().removeHeader("Cookie").build())

        val generation = auth.getAuthGeneration()
        val cookies = delegate.loadForRequest(request.url).filterNot { it.name == "webp" }
        val header = (cookies.map { "${it.name}=${it.value}" } + "webp=1").joinToString("; ")
        val response = chain.proceed(request.newBuilder().header("Cookie", header).build())
        settings.ifCurrent(context.generation) {
            auth.ifCurrent(generation) {
                delegate.saveFromResponse(
                    request.url,
                    Cookie.parseAll(request.url, response.headers)
                )
            }
        }
        return response
    }
}
