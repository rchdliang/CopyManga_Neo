package com.rchdliang.copymanga_neo.data.local.portable

import com.rchdliang.copymanga_neo.data.remote.portable.*
import com.rchdliang.copymanga_neo.domain.model.*
import com.rchdliang.copymanga_neo.domain.repository.*
import io.ktor.http.Url
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import kotlin.time.Clock
import kotlin.uuid.Uuid

interface NetworkDiagnostics {
    /** No credentials/cookies. Must obey selected proxy and risk-control policy. */
    suspend fun probe(settings: NetworkSettings): List<NetworkProbe>
    suspend fun discovery(settings: NetworkSettings): String
    fun officialReport(settings: NetworkSettings): String
}

object PortableNetworkValidation {
    fun host(value: String): String {
        val input = value.trim()
        require(input.isNotBlank()) { "域名不能为空" }
        val url = Url(if ("://" in input) input else "https://$input")
        require(url.protocol.name == "https" && url.port == 443 && url.user.isNullOrEmpty() && url.password.isNullOrEmpty() &&
            url.encodedPath in setOf("", "/") && url.parameters.isEmpty() && url.fragment.isEmpty()) { "请填写 HTTPS 域名，不要包含路径、账号或端口" }
        return url.host
    }
    fun normalize(value: NetworkSettings): NetworkSettings {
        ThemeLoadingPolicy.validate(value)
        val routes = value.routes.take(20).map { row -> row.take(30).map(::host).distinct() }.filter { it.isNotEmpty() }.distinct()
        val fixed = value.fixedHost.takeIf { it.isNotBlank() }?.let(::host).orEmpty()
        require(value.version.trim().matches(Regex("[A-Za-z0-9._-]{1,64}"))) { "请求版本格式无效" }
        if (value.proxyMode == ProxyMode.MANUAL) {
            val raw = value.proxyAddress.trim()
            val proxy = Url(if ("://" in raw) raw else "http://$raw")
            require(proxy.protocol.name == "http" && proxy.user.isNullOrEmpty() && proxy.password.isNullOrEmpty() &&
                proxy.encodedPath in setOf("", "/") && proxy.parameters.isEmpty() && proxy.fragment.isEmpty()) { "代理需为 HTTP 地址，例如 127.0.0.1:7890" }
        }
        require(value.selection != NetworkSelection.ROUTE || value.routeIndex in routes.indices) { "请先获取并选择线路" }
        require(value.selection != NetworkSelection.FIXED || fixed in routes.flatten()) { "请选择已发现的节点" }
        val app = value.officialApp
        require(app.version.matches(Regex("[A-Za-z0-9._-]{1,64}"))) { "App 请求版本格式无效" }
        require((app.versionCode.toIntOrNull() ?: 0) > 0) { "版本号须为大于 0 的整数" }
        listOf(app.pseudoId, app.umString, app.device, app.deviceInfo, app.buildTime).forEach { field ->
            require(field.length <= 256 && field.all { it.code in 32..126 }) { "此字段最多 256 个字符，仅支持英文字母、数字、空格和半角符号。" }
        }
        return value.copy(apiHost = host(value.apiHost), wwwHost = host(value.wwwHost),
            loginHost = value.loginHost.takeIf { it.isNotBlank() }?.let(::host).orEmpty(),
            routes = routes, shareHosts = value.shareHosts.take(20).map(::host).distinct(), fixedHost = fixed,
            version = value.version.trim(), proxyAddress = value.proxyAddress.trim(),
            officialApp = app.copy(apiHost = host(app.apiHost), loginHost = host(app.loginHost)))
    }
}

@Serializable
private data class NetworkDocument(val current: NetworkSettings = NetworkSettings(), val previous: NetworkSettings? = null)

class PortableNetworkSettings(
    storage: DocumentStorage,
    private val network: PortableNetworkState,
    private val identity: PortableOfficialIdentity,
    private val diagnostics: NetworkDiagnostics,
    private val risk: RiskControlRepository,
    private val clearAppSession: suspend (expectedGeneration: Long) -> Unit
) : NetworkSettingsRepository {
    override val supportedRequestModes get() = network.supportedRequestModes
    private fun normalize(value: NetworkSettings) = PortableNetworkValidation.normalize(network.normalizeSettings(value))
    private val document = DocumentStore(storage, "network-settings.json", NetworkDocument.serializer(), NetworkDocument())
    override val settings: StateFlow<NetworkSettings> = object : StateFlow<NetworkSettings> {
        override val value get() = network.snapshot.settings
        override val replayCache get() = listOf(value)
        override suspend fun collect(collector: FlowCollector<NetworkSettings>): Nothing =
            network.snapshots.collect { collector.emit(it.settings) }
    }
    override val officialAppDefaults get() = identity.resolve(OfficialAppSettings())
    private val mutex = Mutex()
    override fun generatePseudoId() = Uuid.random().toHexString().take(16)
    override fun generateUmString() = Uuid.random().toHexString()
    suspend fun initialize(): Result<Unit> = document.load().map {
        val loaded = normalize(document.state.value.current)
        val resolved = loaded.copy(officialApp = identity.resolve(loaded.officialApp))
        document.update { it.copy(current = resolved) }.getOrThrow()
        apply(resolved)
    }
    private fun apply(value: NetworkSettings) {
        val old = network.snapshot.settings
        if (old != value) network.update { snapshot -> snapshot.copy(settings = value,
            appSession = if (old.officialApp != value.officialApp) PortableSession() else snapshot.appSession) }
        if (old.requestMode != value.requestMode || old.officialApp != value.officialApp) risk.resumeByUser()
    }
    private suspend fun persist(value: NetworkSettings, remember: Boolean = true): Result<Unit> {
        val resolved = normalize(value.copy(officialApp = identity.resolve(value.officialApp)))
        val current = network.snapshot
        if (current.settings.officialApp != resolved.officialApp) clearAppSession(current.generation)
        network.requireCurrent(current.generation)
        val previous = document.state.value
        return attempt {
            document.update { old -> NetworkDocument(resolved, if (remember) old.current else old.previous) }.getOrThrow()
            try {
                val loadingOnly = current.settings.copy(themeFetchConcurrency = resolved.themeFetchConcurrency,
                    themeFetchIntervalMillis = resolved.themeFetchIntervalMillis) == resolved
                val applied = if (loadingOnly) network.updateThemeLoading(current.generation, resolved)
                else network.updateIfCurrent(current.generation) { snapshot -> snapshot.copy(settings = resolved,
                    appSession = if (current.settings.officialApp != resolved.officialApp) PortableSession() else snapshot.appSession) }
                check(applied) {
                    "保存期间账号或网络配置已改变，请重试"
                }
            } catch (failure: Exception) {
                withContext(NonCancellable) { document.update { previous }.getOrThrow() }
                throw failure
            }
            if (current.settings.requestMode != resolved.requestMode || current.settings.officialApp != resolved.officialApp) risk.resumeByUser()
        }
    }
    /** Import transaction writes settings before publishing the matching session; no auth side effects. */
    suspend fun persistImportedConfiguration(value: NetworkSettings, expectedGeneration: Long) = mutex.withLock {
        network.requireCurrent(expectedGeneration)
        val normalized = normalize(value)
        val previous = document.state.value
        document.update { old -> old.copy(current = normalized) }.getOrThrow()
        try { network.requireCurrent(expectedGeneration) }
        catch (failure: Exception) {
            withContext(NonCancellable) { document.update { previous }.getOrThrow() }
            throw failure
        }
    }
    /** Persist successful WWW share discovery without changing connection generation or rollback history. */
    suspend fun persistShareHosts(hosts: List<String>, expectedGeneration: Long) = mutex.withLock {
        network.requireCurrent(expectedGeneration)
        val normalized = hosts.take(20).map(PortableNetworkValidation::host).distinct()
        if (normalized.isEmpty() || normalized == network.snapshot.settings.shareHosts) return@withLock
        val previous = document.state.value
        document.update { it.copy(current = it.current.copy(shareHosts = normalized)) }.getOrThrow()
        try { network.updateShareHosts(expectedGeneration, normalized) }
        catch (failure: Exception) {
            withContext(NonCancellable) { document.update { previous }.getOrThrow() }
            throw failure
        }
    }
    override suspend fun save(settings: NetworkSettings): Result<Unit> = attempt {
        mutex.withLock {
            val value = normalize(settings)
            val old = network.snapshot.settings
            persist(if (value.apiHost != old.apiHost || value.wwwHost != old.wwwHost) value.copy(
                routes = emptyList(), shareHosts = emptyList(), discoveredAt = 0, selection = NetworkSelection.DEFAULT, fixedHost = "", routeIndex = 0) else value).getOrThrow()
        }
    }
    override suspend fun reset() = attempt { mutex.withLock { persist(NetworkSettings()).getOrThrow() } }
    override suspend fun previousSettings(): Result<NetworkSettings> = runCatching { document.state.value.previous ?: error("没有可载入的上次配置") }
    override suspend fun rollback() = attempt { mutex.withLock { persist(document.state.value.previous ?: error("没有可恢复的上次配置")).getOrThrow() } }
    override suspend fun diagnoseOfficialApp() = try { Result.success(diagnostics.officialReport(network.snapshot.settings)) } catch (e: Exception) { Result.failure(e) }
    override suspend fun discover() = attempt {
        mutex.withLock {
            val start = network.snapshot.settings
            require(start.requestMode == RequestMode.WEB) { "App 模式请直接设置 API 和登录域名；网页线路发现仅用于网页模式" }
            val generation = network.snapshot.generation
            val raw = diagnostics.discovery(start)
            network.requireCurrent(generation)
            require(raw.length <= 1024 * 1024) { "网络配置响应过大" }
            val json = storageJson.parseToJsonElement(raw).jsonObject
            require(json["code"]?.jsonPrimitive?.intOrNull == 200) { "网络配置获取失败" }
            val result = json["results"]?.jsonObject ?: error("响应中没有 API 线路")
            fun validHost(item: JsonElement) = runCatching { PortableNetworkValidation.host(item.jsonPrimitive.content) }.getOrNull()
            val routes = (result["api"] as? JsonArray).orEmpty().take(20).mapNotNull { row ->
                (row as? JsonArray)?.take(30)?.mapNotNull(::validHost)?.distinct()?.takeIf { it.isNotEmpty() }
            }.distinct()
            require(routes.isNotEmpty()) { "响应中没有可用的 HTTPS 节点" }
            val shares = (result["share"] as? JsonArray).orEmpty().take(20).mapNotNull(::validHost).distinct()
            val index = routes.indexOf(start.routes.getOrNull(start.routeIndex))
            val selection = when {
                start.selection == NetworkSelection.ROUTE && index < 0 -> NetworkSelection.DEFAULT
                start.selection == NetworkSelection.FIXED && start.fixedHost !in routes.flatten() -> NetworkSelection.DEFAULT
                else -> start.selection
            }
            persist(start.copy(routes = routes, shareHosts = shares, routeIndex = index.coerceAtLeast(0), selection = selection,
                discoveredAt = Clock.System.now().toEpochMilliseconds()), remember = false).getOrThrow()
        }
    }
    override suspend fun test(settings: NetworkSettings): Result<List<NetworkProbe>> = try {
        Result.success(diagnostics.probe(normalize(settings)))
    } catch (cancel: CancellationException) { throw cancel } catch (e: Exception) { Result.failure(e) }
    override suspend fun refreshIfNeeded() {
        val value = network.snapshot.settings
        if (value.autoDiscover && value.requestMode == RequestMode.WEB && Clock.System.now().toEpochMilliseconds() - value.discoveredAt >= 24 * 60 * 60 * 1000L) discover()
    }
    private suspend fun attempt(block: suspend () -> Unit): Result<Unit> = try { block(); Result.success(Unit) }
        catch (cancel: CancellationException) { throw cancel } catch (e: Exception) { Result.failure(e) }
}
