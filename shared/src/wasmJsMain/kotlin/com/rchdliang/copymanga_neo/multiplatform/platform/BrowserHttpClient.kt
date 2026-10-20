@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
package com.rchdliang.copymanga_neo.multiplatform.platform

import com.rchdliang.copymanga_neo.data.remote.portable.PortableProxyPolicy
import com.rchdliang.copymanga_neo.data.remote.portable.PortableTransportException
import com.rchdliang.copymanga_neo.domain.model.NetworkSettings
import com.rchdliang.copymanga_neo.domain.model.ProxyMode
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.js.Js
import io.ktor.client.engine.js.JsClientEngineConfig
import io.ktor.client.engine.js.JsError
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.Headers
import io.ktor.http.HttpMethod
import io.ktor.http.Url
import io.ktor.utils.io.InternalAPI
import kotlinx.coroutines.CancellationException
import kotlin.JsFun
import kotlin.js.JsAny

val browserProxyPolicy = PortableProxyPolicy { settings ->
    require(settings.proxyMode == ProxyMode.SYSTEM) { "浏览器管理网络代理，不能强制直连或设置手动代理" }
}
fun createBrowserHttpClient(settings: NetworkSettings = NetworkSettings(), timeoutSeconds: Int = 30): HttpClient {
    browserProxyPolicy.verify(settings)
    require(timeoutSeconds in 1..3600)
    return HttpClient(BrowserEngine) {
        followRedirects = false
        expectSuccess = false
        engine { configureRequest { restrictBrowserRequest(this) } }
        install(HttpTimeout) { requestTimeoutMillis = timeoutSeconds * 1000L }
    }
}

/** 浏览器独立适配传输，不改变公共客户端的原始 URL、缓存键和会话边界。 */
@OptIn(InternalAPI::class)
private object BrowserEngine : HttpClientEngineFactory<JsClientEngineConfig> {
    override fun create(block: JsClientEngineConfig.() -> Unit): HttpClientEngine =
        browserTransportEngine(Js.create(block))
}

@OptIn(InternalAPI::class)
internal fun browserTransportEngine(delegate: HttpClientEngine): HttpClientEngine =
    object : HttpClientEngine by delegate {
        // 使用包装器的 execute；默认委托 install 会绕过这里的浏览器适配。
        override fun install(client: HttpClient) = super<HttpClientEngine>.install(client)

        override suspend fun execute(data: HttpRequestData): HttpResponseData {
            val proxyAddress = if (data.method == HttpMethod.Get) browserImageProxyAddress(data.url.toString()) else ""
            val headers = Headers.build {
                // 图片转发仅传递公开资源地址，绝不转发账号或站点请求头。
                if (proxyAddress.isEmpty()) data.headers.forEach { name, values ->
                    if (browserAllowsHeader(name)) appendAll(name, values)
                }
            }
            val request = HttpRequestData(
                if (proxyAddress.isEmpty()) data.url else Url(proxyAddress),
                data.method, headers, data.body, data.executionContext, data.attributes
            )
            try {
                return delegate.execute(request)
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (failure: Throwable) {
                // Ktor Wasm 将 fetch 拒绝包装成 Error，普通仓库的 catch(Exception) 无法接住。
                if (failure is JsError || failure.cause is JsError) {
                    throw PortableTransportException("浏览器网络请求失败，请检查网络连接或站点跨域设置")
                }
                throw failure
            }
        }
    }

/** 原生客户端模拟导航的请求头不适用于浏览器 fetch，交给浏览器自行管理。 */
internal fun browserAllowsHeader(name: String): Boolean {
    val key = name.lowercase()
    return !key.startsWith("sec-") && key !in setOf(
        "upgrade-insecure-requests", "priority", "cookie", "cookie2", "origin", "referer",
        "user-agent", "host", "connection", "content-length", "accept-encoding"
    )
}

@JsFun("""address => {
    if (globalThis.copyMangaNeoConfig?.imageProxy !== true) return '';
    const url = new URL(address);
    if (url.protocol !== 'https:' || url.username || url.password || url.port ||
        !/^s[a-z0-9]\.mangafunb\.fun$/.test(url.hostname)) return '';
    return location.origin + '/__copymanga_image?url=' + encodeURIComponent(url.href);
}""")
private external fun browserImageProxyAddress(address: String): String

// Browser ambient cookies/HTTP-auth and automatic redirect replay are not session credentials.
@JsFun("request=>{request.credentials='omit';request.redirect='manual';}")
private external fun restrictBrowserRequest(request: JsAny)
