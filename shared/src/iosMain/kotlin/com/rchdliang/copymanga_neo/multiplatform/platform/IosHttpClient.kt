package com.rchdliang.copymanga_neo.multiplatform.platform

import com.rchdliang.copymanga_neo.data.remote.portable.PortableProxyPolicy
import com.rchdliang.copymanga_neo.domain.model.NetworkSettings
import com.rchdliang.copymanga_neo.domain.model.ProxyMode
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.HttpTimeout

/** Darwin cannot safely honor HTTPS-over-manual-HTTP-proxy; reject instead of leaking a direct request. */
val iosProxyPolicy = PortableProxyPolicy { settings ->
    require(settings.proxyMode != ProxyMode.MANUAL) { "iOS网络引擎暂不支持应用内HTTPS手动代理，请在系统设置中配置代理" }
}
fun createIosHttpClient(settings: NetworkSettings = NetworkSettings(), timeoutSeconds: Int = 30): HttpClient {
    iosProxyPolicy.verify(settings)
    val timeout = timeoutSeconds.coerceIn(1, 300) * 1000L
    return HttpClient(Darwin) {
        followRedirects = false
        engine {
            configureSession {
                HTTPShouldSetCookies = false
                HTTPCookieStorage = null
                URLCredentialStorage = null
                if (settings.proxyMode == ProxyMode.DIRECT) connectionProxyDictionary = emptyMap<Any?, Any>()
                timeoutIntervalForRequest = timeout / 1000.0
                timeoutIntervalForResource = timeout / 1000.0
            }
        }
        install(HttpTimeout) { requestTimeoutMillis = timeout; socketTimeoutMillis = timeout }
    }
}
