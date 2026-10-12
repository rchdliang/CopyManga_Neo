package com.rchdliang.copymanga_neo.data.download

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.rchdliang.copymanga_neo.domain.repository.AppSettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Singleton

internal data class DownloadNetworkState(
    val allowed: Boolean = false,
    val wifiOnly: Boolean = true,
    val network: Network? = null
)

internal fun downloadNetworkAllowed(wifiOnly: Boolean, connected: Boolean, wifi: Boolean): Boolean =
    connected && (!wifiOnly || wifi)

private data class DownloadConnection(
    val network: Network?,
    val connected: Boolean,
    val wifi: Boolean
)

/** 监控实际默认网络；同时具有蜂窝传输的 VPN 不视为纯 Wi-Fi。 */
@Singleton
class DownloadNetworkPolicy @Inject constructor(
    @ApplicationContext context: Context,
    settings: AppSettingsRepository
) {
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val mutableState = MutableStateFlow(DownloadNetworkState())
    internal val state = mutableState.asStateFlow()
    internal val ready = CompletableDeferred<Unit>()
    private var wifiClient: Triple<Network, OkHttpClient, OkHttpClient>? = null

    /** 漫画图片与小说正文共用网络约束，Wi-Fi 模式禁止复用蜂窝连接。 */
    @Synchronized
    internal fun client(base: OkHttpClient): OkHttpClient {
        val policy = state.value
        if (!policy.allowed) throw java.io.IOException("当前网络不允许下载")
        if (!policy.wifiOnly) return base
        val network = policy.network ?: throw java.io.IOException("Wi-Fi 网络尚未就绪")
        wifiClient?.takeIf { it.first == network && it.second === base }?.let { return it.third }
        wifiClient?.third?.connectionPool?.evictAll()
        return base.newBuilder().socketFactory(network.socketFactory)
            .dns { host -> network.getAllByName(host).toList() }
            .connectionPool(ConnectionPool()).build()
            .also { wifiClient = Triple(network, base, it) }
    }

    private val scope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, error ->
            android.util.Log.e("DownloadNetworkPolicy", "读取下载网络策略失败", error)
            // 读取失败时保持禁止传输，避免意外使用移动数据。
            mutableState.value = DownloadNetworkState()
            ready.complete(Unit)
        })

    init {
        scope.launch {
            combine(settings.settings, networks()) { preferences, connection ->
                DownloadNetworkState(
                    allowed = downloadNetworkAllowed(
                        preferences.wifiOnlyDownloads,
                        connection.connected,
                        connection.wifi
                    ),
                    wifiOnly = preferences.wifiOnlyDownloads,
                    network = connection.network
                )
            }.distinctUntilChanged().collect {
                mutableState.value = it
                ready.complete(Unit)
            }
        }
    }

    private fun networks() = callbackFlow<DownloadConnection> {
        val callback = object : ConnectivityManager.NetworkCallback() {
            private var current: Network? = null
            override fun onAvailable(network: Network) {
                current = network
                trySend(DownloadConnection(network, false, false))
            }

            override fun onCapabilitiesChanged(
                network: Network,
                capabilities: NetworkCapabilities
            ) {
                if (current == network) trySend(
                    DownloadConnection(
                        network,
                        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
                        capabilities.isWifiOnly()
                    )
                )
            }

            override fun onLost(network: Network) {
                if (current == network) {
                    current = null
                    trySend(DownloadConnection(null, false, false))
                }
            }
        }
        // 先提供初值，再由注册后的能力回调覆盖；不在 onAvailable 中同步查询。
        val initial = connectivity.activeNetwork
        val capabilities = connectivity.getNetworkCapabilities(initial)
        trySend(
            DownloadConnection(
                initial,
                capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true,
                capabilities?.isWifiOnly() == true
            )
        )
        connectivity.registerDefaultNetworkCallback(callback)
        awaitClose { connectivity.unregisterNetworkCallback(callback) }
    }

    private fun NetworkCapabilities.isWifiOnly(): Boolean =
        hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
                !hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) &&
                hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}
