@file:OptIn(io.ktor.utils.io.InternalAPI::class)

package com.rchdliang.copymanga_neo.multiplatform.platform

import com.rchdliang.copymanga_neo.data.remote.portable.PortableProxyPolicy
import com.rchdliang.copymanga_neo.domain.model.NetworkSettings
import com.rchdliang.copymanga_neo.domain.model.ProxyMode
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.HttpClientEngineBase
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.URI
import java.util.concurrent.ConcurrentHashMap

/** SYSTEM uses JVM ProxySelector per URL, retaining nonProxyHosts/PAC decisions where configured.
 * Launch the JVM with -Djava.net.useSystemProxies=true to enable supported OS discovery.
 * HTTP proxies and DIRECT work; SOCKS/authenticated proxies fail explicitly rather than bypass them.
 */
val desktopProxyPolicy = PortableProxyPolicy { settings ->
    if (settings.proxyMode == ProxyMode.MANUAL) desktopManualProxy(settings.proxyAddress)
}

fun createDesktopHttpClient(settings: NetworkSettings = NetworkSettings(), timeoutSeconds: Int = 30): HttpClient =
    createDesktopHttpClient(settings = { settings }, timeoutSeconds = timeoutSeconds)

/** Supply current settings for the application client; use the snapshot overload for diagnostics. */
fun createDesktopHttpClient(settings: () -> NetworkSettings, timeoutSeconds: Int = 30): HttpClient {
    require(timeoutSeconds in 1..3600) { "超时时间必须在1至3600秒之间" }
    return HttpClient(object : HttpClientEngineFactory<HttpClientEngineConfig> {
        override fun create(block: HttpClientEngineConfig.() -> Unit): HttpClientEngine =
            RoutedCioEngine(settings, timeoutSeconds * 1000L)
    }) {
        followRedirects = false
        expectSuccess = false
        // No retry, credential, cookie, auth, or default-header plugins are installed here.
    }
}

private class RoutedCioEngine(
    private val settings: () -> NetworkSettings,
    private val timeoutMillis: Long,
) : HttpClientEngineBase("copymanga-routed-cio") {
    private val engines = ConcurrentHashMap<Proxy, HttpClientEngine>()
    private val direct = engineFor(Proxy.NO_PROXY)
    override val config = direct.config
    override val supportedCapabilities = direct.supportedCapabilities

    override suspend fun execute(data: HttpRequestData): HttpResponseData {
        val current = settings()
        val proxy = when (current.proxyMode) {
            ProxyMode.DIRECT -> Proxy.NO_PROXY
            ProxyMode.MANUAL -> desktopManualProxy(current.proxyAddress)
            ProxyMode.SYSTEM -> ProxySelector.getDefault()?.select(URI(data.url.toString()))?.firstOrNull() ?: Proxy.NO_PROXY
        }
        require(proxy.type() == Proxy.Type.DIRECT || proxy.type() == Proxy.Type.HTTP) {
            "CIO不支持系统选择的SOCKS代理，请使用HTTP代理；不会绕过代理直连"
        }
        return engineFor(proxy).execute(data)
    }

    private fun engineFor(selected: Proxy): HttpClientEngine = engines.computeIfAbsent(selected) {
        CIO.create {
            proxy = selected.takeUnless { it.type() == Proxy.Type.DIRECT }
            requestTimeout = timeoutMillis
            endpoint.connectTimeout = timeoutMillis
            endpoint.socketTimeout = timeoutMillis
            endpoint.connectAttempts = 1
        }
    }
    override fun close() {
        engines.values.forEach { it.close() }
        engines.clear()
        super.close()
    }
}

internal fun desktopManualProxy(address: String): Proxy {
    val input = address.trim()
    require(input.isNotBlank()) { "代理地址不能为空" }
    val uri = URI(if ("://" in input) input else "http://$input")
    require(uri.scheme.equals("http", true) && !uri.host.isNullOrBlank() && uri.userInfo == null &&
        uri.path.orEmpty() in listOf("", "/") && uri.query == null && uri.fragment == null && uri.port in -1..65535 && uri.port != 0
    ) { "代理需为不带凭据的HTTP地址，例如127.0.0.1:7890" }
    return Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved(uri.host, if (uri.port == -1) 80 else uri.port))
}
