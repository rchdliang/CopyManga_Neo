package com.rchdliang.copymanga_neo.data.download.portable

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log
import com.rchdliang.copymanga_neo.data.remote.portable.createAndroidHttpClient
import com.rchdliang.copymanga_neo.domain.model.NetworkSettings
import com.rchdliang.copymanga_neo.domain.repository.AppSettingsRepository
import com.rchdliang.copymanga_neo.multiplatform.app.AppDownloadServices
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 只信任系统验证的默认网络；混合蜂窝 VPN 不算纯 Wi-Fi。 */
class AndroidDownloadNetworkMonitor(context: Context) : DownloadNetworkMonitor, AutoCloseable {
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val lock = Any()
    private var current: Network? = connectivity.activeNetwork
    private val mutable =
        MutableStateFlow(describe(current, connectivity.getNetworkCapabilities(current)))
    override val connections = mutable.asStateFlow()
    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = synchronized(lock) {
            current = network; mutable.value = describe(network, null)
        }

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) =
            synchronized(lock) {
                if (current == network) mutable.value = describe(network, capabilities)
            }

        override fun onLost(network: Network) = synchronized(lock) {
            if (current == network) {
                current = null; mutable.value = describe(null, null)
            }
        }
    }

    init {
        connectivity.registerDefaultNetworkCallback(callback)
    }

    private fun describe(network: Network?, capabilities: NetworkCapabilities?) =
        DownloadConnection(
            connected = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true,
            wifi = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true &&
                    !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR),
            bindingId = network?.networkHandle?.toString()
        )

    fun boundNetwork(policy: DownloadNetworkState): Network = synchronized(lock) {
        check(
            policy.allowed && policy.wifiOnly && connections.value.connected && connections.value.wifi &&
                    connections.value.bindingId == policy.bindingId
        ) { "Wi-Fi 网络已经变化，请重新检查下载策略" }
        requireNotNull(current)
    }

    override fun close() {
        connectivity.unregisterNetworkCallback(callback)
    }
}

private class AndroidDownloadClientProvider(
    private val monitor: AndroidDownloadNetworkMonitor,
    private val settings: () -> NetworkSettings,
    private val unbound: () -> HttpClient
) : DownloadClientProvider, AutoCloseable {
    private var selected: Triple<Network, NetworkSettings, HttpClient>? = null
    @Synchronized
    override fun client(policy: DownloadNetworkState): HttpClient {
        check(policy.allowed) { "当前网络不允许下载" }
        if (!policy.wifiOnly) return unbound()
        val network = monitor.boundNetwork(policy)
        val configuration = settings()
        selected?.takeIf { it.first == network && it.second == configuration }
            ?.let { return it.third }
        selected?.third?.close()
        return createAndroidHttpClient(configuration, network = network).also {
            Log.d("DownloadNetwork", "Wi-Fi client bound: network=$network handle=${network.networkHandle}; socket and DNS binding enabled")
            selected = Triple(network, configuration, it)
        }
    }

    @Synchronized
    override fun close() {
        selected?.third?.close(); selected = null
    }
}

fun androidDownloadServices(
    context: Context, scope: CoroutineScope, settings: AppSettingsRepository,
    runtime: DownloadRuntime, networkSettings: () -> NetworkSettings, client: () -> HttpClient
): AppDownloadServices {
    val monitor = AndroidDownloadNetworkMonitor(context)
    val provider = AndroidDownloadClientProvider(monitor, networkSettings, client)
    return AppDownloadServices(runtime, DownloadNetworkPolicy(settings, monitor, scope), provider) {
        provider.close(); monitor.close()
    }
}
