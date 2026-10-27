@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.rchdliang.copymanga_neo.data.download.portable

import com.rchdliang.copymanga_neo.domain.repository.AppSettingsRepository
import io.ktor.client.HttpClient
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.JsFun

/** Visibility and online events revoke allowance; navigator.onLine alone never proves reachability. */
class BrowserDownloadNetworkMonitor(
    scope: CoroutineScope,
    client: () -> HttpClient,
    probeUrl: () -> String
) : DownloadNetworkMonitor {
    private val active = MutableStateFlow(false)
    private val delegate = ForegroundDownloadNetworkMonitor(scope, active, client, probeUrl, WIFI_UNAVAILABLE)
    override val connections = delegate.connections
    init {
        scope.launch {
            val token = observeBrowserDownloadAvailability { available -> active.value = available }
            try { awaitCancellation() }
            finally {
                active.value = false
                stopBrowserDownloadAvailability(token)
            }
        }
    }
    companion object {
        const val WIFI_UNAVAILABLE = "浏览器无法验证并绑定 Wi-Fi；如需下载，请关闭‘仅 Wi-Fi 下载’，并保持页面可见"
    }
}

data class BrowserDownloadServices(
    val runtime: ForegroundDownloadRuntime,
    val monitor: BrowserDownloadNetworkMonitor,
    val networkPolicy: DownloadNetworkPolicy,
    val clientProvider: UnboundDownloadClientProvider
)

/** The supplied HTTP client is unauthenticated and has no automatic retries. */
fun browserDownloadServices(
    applicationScope: CoroutineScope,
    settings: AppSettingsRepository,
    client: () -> HttpClient,
    probeUrl: () -> String
): BrowserDownloadServices {
    val monitor = BrowserDownloadNetworkMonitor(applicationScope, client, probeUrl)
    return BrowserDownloadServices(ForegroundDownloadRuntime(applicationScope), monitor,
        DownloadNetworkPolicy(settings, monitor, applicationScope),
        UnboundDownloadClientProvider(client, BrowserDownloadNetworkMonitor.WIFI_UNAVAILABLE))
}

@JsFun("""(changed) => {
 const state=globalThis.__copyMangaDownloadObservers || (globalThis.__copyMangaDownloadObservers=new Map());
 const token=crypto.randomUUID();
 const update=()=>changed(navigator.onLine && document.visibilityState==='visible');
 globalThis.addEventListener('online',update);globalThis.addEventListener('offline',update);
 document.addEventListener('visibilitychange',update);
 state.set(token,()=>{globalThis.removeEventListener('online',update);globalThis.removeEventListener('offline',update);document.removeEventListener('visibilitychange',update);});
 update();return token;
}""")
private external fun observeBrowserDownloadAvailability(changed: (Boolean) -> Unit): String
@JsFun("""(token) => {const state=globalThis.__copyMangaDownloadObservers;state?.get(token)?.();state?.delete(token);}""")
private external fun stopBrowserDownloadAvailability(token: String)
