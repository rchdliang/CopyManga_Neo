package com.rchdliang.copymanga_neo.data.remote.portable

import android.net.Network
import com.rchdliang.copymanga_neo.domain.model.NetworkSettings
import com.rchdliang.copymanga_neo.domain.model.ProxyMode
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import java.net.*
import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.ConnectionPool

/** Android 仅提供传输；协议、凭据和重试决策全部由共享客户端负责。 */
val androidProxyPolicy = PortableProxyPolicy { settings ->
    if (settings.proxyMode == ProxyMode.MANUAL) androidManualProxy(settings.proxyAddress)
}

fun createAndroidHttpClient(
    settings: NetworkSettings,
    timeoutSeconds: Int = 30,
    network: Network? = null
): HttpClient {
    val system = ProxySelector.getDefault()
    return HttpClient(OkHttp) {
        followRedirects = false
        expectSuccess = false
        engine {
            config {
                retryOnConnectionFailure(false)
                followRedirects(false)
                followSslRedirects(false)
                connectTimeout(timeoutSeconds.toLong(), TimeUnit.SECONDS)
                readTimeout(timeoutSeconds.toLong(), TimeUnit.SECONDS)
                writeTimeout(timeoutSeconds.toLong(), TimeUnit.SECONDS)
                proxySelector(object : ProxySelector() {
                    override fun select(uri: URI): List<Proxy> = when (settings.proxyMode) {
                        ProxyMode.DIRECT -> listOf(Proxy.NO_PROXY)
                        ProxyMode.MANUAL -> listOf(androidManualProxy(settings.proxyAddress))
                        ProxyMode.SYSTEM -> system?.select(uri)?.takeIf { it.isNotEmpty() }
                            ?: listOf(Proxy.NO_PROXY)
                    }

                    override fun connectFailed(
                        uri: URI,
                        address: SocketAddress,
                        error: IOException
                    ) {
                        if (settings.proxyMode == ProxyMode.SYSTEM) system?.connectFailed(
                            uri,
                            address,
                            error
                        )
                    }
                })
                if (network != null) {
                    socketFactory(network.socketFactory)
                    dns { network.getAllByName(it).toList() }
                    connectionPool(ConnectionPool())
                }
            }
        }
    }
}

private fun androidManualProxy(address: String): Proxy {
    val value = address.trim()
    val uri = URI(if ("://" in value) value else "http://$value")
    require(
        uri.scheme == "http" && !uri.host.isNullOrBlank() && uri.userInfo == null &&
                uri.path.orEmpty() in listOf(
            "",
            "/"
        ) && uri.query == null && uri.fragment == null &&
                uri.port in -1..65535 && uri.port != 0
    ) { "请填写不带凭据的 HTTP 代理地址" }
    return Proxy(
        Proxy.Type.HTTP,
        InetSocketAddress.createUnresolved(uri.host, if (uri.port == -1) 80 else uri.port)
    )
}
