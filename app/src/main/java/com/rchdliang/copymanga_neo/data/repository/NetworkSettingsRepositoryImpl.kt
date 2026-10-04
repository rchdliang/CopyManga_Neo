package com.rchdliang.copymanga_neo.data.repository

import com.google.gson.JsonParser
import com.rchdliang.copymanga_neo.data.local.NetworkSettingsStore
import com.rchdliang.copymanga_neo.data.remote.CopyMangaNetworkConfig
import com.rchdliang.copymanga_neo.data.remote.NetworkSettingsCodec
import com.rchdliang.copymanga_neo.data.remote.RiskControlGate
import com.rchdliang.copymanga_neo.data.remote.SettingsProxySelector
import com.rchdliang.copymanga_neo.domain.model.NetworkProbe
import com.rchdliang.copymanga_neo.domain.model.NetworkSelection
import com.rchdliang.copymanga_neo.domain.model.NetworkSettings
import com.rchdliang.copymanga_neo.domain.repository.NetworkSettingsRepository
import com.rchdliang.copymanga_neo.domain.repository.RiskControlException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resumeWithException

@Singleton
class NetworkSettingsRepositoryImpl @Inject constructor(
    private val store: NetworkSettingsStore,
    private val gate: RiskControlGate,
    private val identity: com.rchdliang.copymanga_neo.data.local.OfficialAppIdentity,
    private val auth: com.rchdliang.copymanga_neo.data.local.AuthDataStore,
    private val cookies: com.rchdliang.copymanga_neo.di.PersistentCookieJar,
    private val diagnostics: com.rchdliang.copymanga_neo.data.remote.OfficialAppDiagnostics,
    private val localCalls: com.rchdliang.copymanga_neo.data.remote.LocalNetworkCallInterceptor,
    private val localExchanges: com.rchdliang.copymanga_neo.data.remote.LocalNetworkLogInterceptor,
    private val users: com.rchdliang.copymanga_neo.domain.repository.UserRepository
) : NetworkSettingsRepository {
    override val officialAppDefaults get() = identity.defaults
    override val settings = store.settings
    private val mutation = Mutex()

    override suspend fun diagnoseOfficialApp(): Result<String> = result { diagnostics.report() }

    override suspend fun save(settings: NetworkSettings): Result<Unit> = result {
        mutation.withLock {
            val value = NetworkSettingsCodec.normalize(settings)
            val old = store.settings.value
            // 更换发现入口后，旧线路不能继续覆盖新 API 域名。
            saveConfiguration(
                if (value.apiHost != old.apiHost || value.wwwHost != old.wwwHost) {
                    value.copy(
                        routes = emptyList(),
                        shareHosts = emptyList(),
                        discoveredAt = 0,
                        selection = NetworkSelection.DEFAULT,
                        fixedHost = "",
                        routeIndex = 0
                    )
                } else value
            )
        }
    }

    override suspend fun reset(): Result<Unit> =
        result { mutation.withLock { saveConfiguration(NetworkSettings()) } }

    override suspend fun rollback(): Result<Unit> =
        result { mutation.withLock { saveConfiguration(store.previousSettings()) } }

    private suspend fun saveConfiguration(value: NetworkSettings) {
        val old = store.settings.value
        val modeChanged = old.requestMode != value.requestMode
        val wasLoggedIn = auth.isLoggedInSync()
        val credentials = if (modeChanged && wasLoggedIn) auth.getRequestModeLogin() else null
        val identityChanged = old.requestMode != value.requestMode ||
                (old.officialApp != value.officialApp && old.requestMode == com.rchdliang.copymanga_neo.domain.model.RequestMode.OFFICIAL_APP)
        if (modeChanged) auth.setRequestModeSwitching(true)
        try {
            if (identityChanged) {
                // 模式切换保留自动登录偏好，但阻止过期监听重复发起登录。
                auth.clearAuth(manualLogout = !modeChanged)
                cookies.clearAll()
            }
            val authGeneration = auth.getAuthGeneration()
            store.save(value)
            if (identityChanged) gate.resumeByUser()
            if (modeChanged && wasLoggedIn) {
                if (credentials == null) throw IOException("网络设置已保存，当前账号没有可用密码，请手动登录")
                check(auth.getAuthGeneration() == authGeneration && !auth.isLoggedInSync()) {
                    "网络设置已保存，登录状态已变化，未自动登录"
                }
                users.login(credentials.first, credentials.second).fold(
                    onSuccess = {},
                    onFailure = {
                        throw IOException(
                            "网络设置已保存，自动登录失败：${it.message ?: "请手动登录"}",
                            it
                        )
                    }
                )
            }
        } finally {
            if (modeChanged) auth.setRequestModeSwitching(false)
        }
    }

    override suspend fun discover(): Result<Unit> = result {
        mutation.withLock {
            val start = store.settings.value
            require(start.requestMode == com.rchdliang.copymanga_neo.domain.model.RequestMode.WEB) {
                "App 模式请直接设置 API 和登录域名；网页线路发现仅用于网页模式"
            }
            val client = diagnosticClient(start)
            var lastError: Exception? = null
            try {
                // 仅查询拷贝当前配置的 API / WWW，没有预置任何其他内容源。
                for (host in listOf(start.apiHost, start.wwwHost).distinct()) {
                    currentCoroutineContext().ensureActive()
                    val builder =
                        Request.Builder().url("https://$host/api/v3/system/network21?platform=1")
                    CopyMangaNetworkConfig.apiHeaders()
                        .forEach { (key, value) -> builder.header(key, value) }
                    val call = client.newCall(
                        builder.header("version", start.version)
                            .header(
                                "User-Agent",
                                "${System.getProperty("http.agent") ?: "Dalvik/2.1.0 (Linux; Android)"} com.copymanga.app"
                            )
                            .header("Origin", "https://${start.wwwHost}").build()
                    )
                    gate.register(call)
                    try {
                        var shareHosts = start.shareHosts
                        val routes = call.awaitResponse().use { response ->
                            val body = response.peekBody(1024 * 1024 + 1).string()
                            val code = try {
                                JsonParser.parseString(body).asJsonObject.get("code")?.asInt
                            } catch (_: Exception) {
                                null
                            }
                            if (response.code == 210 || code == 210) {
                                gate.block("网络配置接口触发访问限制，请稍后再试", call)
                                throw RiskControlException("网络配置接口触发访问限制，请稍后再试")
                            }
                            if (!response.isSuccessful) throw IOException("网络配置请求失败：HTTP ${response.code}")
                            shareHosts = NetworkSettingsCodec.parseShareHosts(body)
                            NetworkSettingsCodec.parseRoutes(body)
                        }
                        val oldRoute = start.routes.getOrNull(start.routeIndex)
                        val matchingRoute = routes.indexOf(oldRoute)
                        val selection = when {
                            start.selection == NetworkSelection.FIXED && start.fixedHost !in routes.flatten() -> NetworkSelection.DEFAULT
                            start.selection == NetworkSelection.ROUTE && matchingRoute < 0 -> NetworkSelection.DEFAULT
                            else -> start.selection
                        }
                        store.save(
                            start.copy(
                                routes = routes,
                                shareHosts = shareHosts,
                                discoveredAt = System.currentTimeMillis(),
                                selection = selection, routeIndex = matchingRoute.coerceAtLeast(0)
                            ), rememberPrevious = false
                        )
                        return@withLock
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: RiskControlException) {
                        throw e
                    } catch (e: Exception) {
                        lastError = e
                    } finally {
                        gate.unregister(call)
                    }
                }
                throw lastError ?: IOException("没有取得线路配置")
            } finally {
                client.connectionPool.evictAll()
            }
        }
    }

    /** 测速仅访问根路径，不带认证或 Cookie；显示请求耗时，不宣称业务接口可用。 */
    override suspend fun test(settings: NetworkSettings): Result<List<NetworkProbe>> = result {
        val value = NetworkSettingsCodec.normalize(settings)
        val client = diagnosticClient(value)
        val hosts =
            (if (value.requestMode == com.rchdliang.copymanga_neo.domain.model.RequestMode.OFFICIAL_APP)
                listOf(value.officialApp.apiHost, value.officialApp.loginHost)
            else listOf(value.apiHost, value.wwwHost, value.loginHost) + value.routes.flatten())
                .filter { it.isNotBlank() }.distinct().take(60)
        val limit = Semaphore(3)
        try {
            supervisorScope {
                hosts.map { host ->
                    async {
                        limit.withPermit {
                            val started = System.nanoTime()
                            try {
                                client.newCall(
                                    Request.Builder().url("https://$host/").head().build()
                                ).awaitResponse().use {
                                    NetworkProbe(host, (System.nanoTime() - started) / 1_000_000)
                                }
                            } catch (e: CancellationException) {
                                throw e
                            } catch (_: Exception) {
                                NetworkProbe(host, null, "连接失败或超时")
                            }
                        }
                    }
                }.map { it.await() }
            }
        } finally {
            client.connectionPool.evictAll()
        }
    }

    override suspend fun refreshIfNeeded() {
        val value = settings.value
        if (value.requestMode == com.rchdliang.copymanga_neo.domain.model.RequestMode.WEB && value.autoDiscover && System.currentTimeMillis() - value.discoveredAt >= TimeUnit.DAYS.toMillis(
                1
            )
        ) discover()
    }

    private fun diagnosticClient(value: NetworkSettings) = OkHttpClient.Builder()
        .addInterceptor(localCalls).addNetworkInterceptor(localExchanges)
        .proxySelector(SettingsProxySelector({ value }))
        .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false)
        .connectTimeout(5, TimeUnit.SECONDS).readTimeout(5, TimeUnit.SECONDS)
        .callTimeout(8, TimeUnit.SECONDS).build()

    private suspend fun <T> result(block: suspend () -> T): Result<T> =
        withContext(Dispatchers.IO) {
            try {
                Result.success(block())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
}

/** 取消页面或协程时同时取消连接，不在后台遗留探测请求。 */
private suspend fun Call.awaitResponse(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (!continuation.isCancelled) continuation.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            continuation.resume(response) { _, value, _ -> value.close() }
        }
    })
}
