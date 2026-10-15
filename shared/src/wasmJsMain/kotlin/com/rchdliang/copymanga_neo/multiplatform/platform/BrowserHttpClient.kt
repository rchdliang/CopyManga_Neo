@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
package com.rchdliang.copymanga_neo.multiplatform.platform

import com.rchdliang.copymanga_neo.data.remote.portable.PortableProxyPolicy
import com.rchdliang.copymanga_neo.domain.model.NetworkSettings
import com.rchdliang.copymanga_neo.domain.model.ProxyMode
import io.ktor.client.HttpClient
import io.ktor.client.engine.js.Js
import io.ktor.client.plugins.HttpTimeout
import kotlin.JsFun
import kotlin.js.JsAny

val browserProxyPolicy = PortableProxyPolicy { settings ->
    require(settings.proxyMode == ProxyMode.SYSTEM) { "浏览器管理网络代理，不能强制直连或设置手动代理" }
}
fun createBrowserHttpClient(settings: NetworkSettings = NetworkSettings(), timeoutSeconds: Int = 30): HttpClient {
    browserProxyPolicy.verify(settings)
    require(timeoutSeconds in 1..3600)
    return HttpClient(Js) {
        followRedirects = false
        expectSuccess = false
        engine { configureRequest { restrictBrowserRequest(this) } }
        install(HttpTimeout) { requestTimeoutMillis = timeoutSeconds * 1000L }
    }
}

// Browser ambient cookies/HTTP-auth and automatic redirect replay are not session credentials.
@JsFun("request=>{request.credentials='omit';request.redirect='manual';}")
private external fun restrictBrowserRequest(request: JsAny)
