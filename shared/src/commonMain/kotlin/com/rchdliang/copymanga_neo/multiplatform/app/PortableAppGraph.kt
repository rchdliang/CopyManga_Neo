package com.rchdliang.copymanga_neo.multiplatform.app

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.disk.DiskCache
import com.rchdliang.copymanga_neo.data.local.portable.*
import com.rchdliang.copymanga_neo.data.remote.portable.*
import com.rchdliang.copymanga_neo.data.repository.portable.*
import com.rchdliang.copymanga_neo.data.novel.portable.*
import com.rchdliang.copymanga_neo.data.novel.portable.cache.VerifiedPortableNovelCache
import com.rchdliang.copymanga_neo.data.download.portable.*
import com.rchdliang.copymanga_neo.data.export.portable.*
import com.rchdliang.copymanga_neo.data.image.portable.*
import com.rchdliang.copymanga_neo.data.storage.CacheStorage
import com.rchdliang.copymanga_neo.domain.model.*
import com.rchdliang.copymanga_neo.domain.repository.AppSettingsRepository
import com.rchdliang.copymanga_neo.domain.repository.StorageRepository
import io.ktor.client.HttpClient
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/** Only platform capabilities vary. Repositories, state, download/export ownership and restoration do not. */
data class AppDownloadServices(
    val runtime: DownloadRuntime,
    val networkPolicy: DownloadNetworkPolicy,
    val clientProvider: DownloadClientProvider
)
data class PortableAppServices(
    val documents: DocumentStorage,
    val cache: CacheStorage,
    val secureCredentials: SecureCredentialStorage,
    val deviceName: String,
    val deviceModel: String,
    val createHttpClient: (NetworkSettings, Int) -> HttpClient,
    val proxyPolicy: PortableProxyPolicy,
    val downloadServices: (CoroutineScope, AppSettingsRepository, () -> HttpClient, () -> String) -> AppDownloadServices,
    val comicExportIo: ComicExportIo,
    val readerImageIo: ReaderImageIo,
    val imageContext: PlatformContext,
    val imageDiskCache: DiskCache? = null,
    val tapString: String = "",
    val startupWarnings: List<String> = emptyList(),
    val debugBuild: Boolean = false
)

/** Thread-safe closeable resource pool, also available on Native and Wasm without JVM collections. */
private class AppResourcePool<K, V : Any>(private val dispose: (V) -> Unit) {
    private val values = MutableStateFlow<Map<K, V>?>(emptyMap())
    fun get(key: K, create: () -> V): V {
        var candidate: V? = null
        while (true) {
            val snapshot = values.value ?: run { candidate?.let(dispose); error("Application is closed") }
            snapshot[key]?.let { existing -> candidate?.let(dispose); return existing }
            val next = candidate ?: create().also { candidate = it }
            if (values.compareAndSet(snapshot, snapshot + (key to next))) return next
        }
    }
    fun retire(predicate: (K) -> Boolean) {
        while (true) {
            val snapshot = values.value ?: return
            val removed = snapshot.filterKeys(predicate)
            if (removed.isEmpty()) return
            if (values.compareAndSet(snapshot, snapshot - removed.keys)) {
                removed.values.forEach(dispose); return
            }
        }
    }
    fun forEach(action: (V) -> Unit) { values.value?.values?.forEach(action) }
    fun close() { values.getAndUpdate { null }?.values?.forEach(dispose) }
}

class PortableAppGraph private constructor(
    val repositories: AppRepositories,
    val imageLoader: (Int) -> ImageLoader,
    val startupWarnings: List<String>,
    private val closeResources: () -> Unit,
    private val shutdownResources: suspend () -> Unit
) : AutoCloseable {
    override fun close() = closeResources()
    /** Await transfer cancellation and export rollback before closing their IO dependencies. */
    suspend fun shutdown() = shutdownResources()
    companion object {
        /** The caller owns its UI scope; this graph owns and cancels its child work on close. */
        suspend fun create(services: PortableAppServices, parentScope: CoroutineScope): PortableAppGraph {
            val lifetime = SupervisorJob(parentScope.coroutineContext[Job])
            val appScope = CoroutineScope(parentScope.coroutineContext + lifetime)
            val cleanup = mutableListOf<() -> Unit>()
            var completed = false
            fun closeAll() {
                lifetime.cancel()
                cleanup.asReversed().forEach { runCatching { it() } }
                cleanup.clear()
            }
            try {
                val documents = services.documents; val cache = services.cache
                val startupWarnings = services.startupWarnings.toMutableList()
                services.imageDiskCache?.let { cleanup += { it.shutdown() } }
                val appSettings = PortableAppSettings(documents).also { it.initialize().getOrThrow() }
                val readerPreferences = PortableReaderPreferences(documents).also { it.initialize().getOrThrow() }
                val readerTools = PortableReaderTools(documents).also { it.initialize().getOrThrow() }
                val localReading = PortableLocalReading(documents, appScope).also {
                    it.retryRead().onFailure { failure ->
                        startupWarnings += "本地阅读数据无法读取，原文件已保留，请在本地书库重试：${failure.message.orEmpty()}"
                    }
                }
                val novelPreferences = PortableNovelPreferences(documents).also { it.initialize().getOrThrow() }
                val annotations = PortableNovelAnnotations(documents).also { it.initialize().getOrThrow() }
                val blacklist = PortableThemeBlacklist(documents).also { it.initialize().getOrThrow() }
                val logs = PortableNetworkLog(documents, appScope).also { it.initialize().getOrThrow() }
                val identity = PersistedOfficialIdentity.create(documents, services.deviceName, services.deviceModel, services.tapString)
                val state = PortableNetworkState(); val risk = PortableRiskControlGate()
                val cleanClients = AppResourcePool<Triple<Long, NetworkSettings, Int>, HttpClient> { it.close() }
                cleanup += cleanClients::close
                fun engine(settings: NetworkSettings? = null, timeout: Int = 30): HttpClient {
                    val snapshot = state.snapshot
                    val selected = settings ?: snapshot.settings
                    return cleanClients.get(Triple(snapshot.generation, selected, timeout)) { services.createHttpClient(selected, timeout) }
                }
                val client = CopyMangaClient(engine(), state, risk, identity, services.proxyPolicy, diagnostics = logs::record,
                    engineProvider = { services.createHttpClient(it, 30) })
                cleanup += client::close
                lateinit var network: PortableNetworkSettings
                val users = PortableUserRepository(client, services.secureCredentials,
                    persistImportedConfiguration = { settings, generation -> network.persistImportedConfiguration(settings, generation) })
                network = PortableNetworkSettings(documents, state, identity, KtorNetworkDiagnostics({ engine(it) }, risk) { identity.tapString.isNotEmpty() }, risk,
                    clearAppSession = { users.clearModeSecureSession(RequestMode.OFFICIAL_APP, it) })
                network.initialize().getOrThrow(); users.restoreSecureSessions()
                val session = PortableReadingSession(state)
                val downloadServices = services.downloadServices(appScope, appSettings, { engine() }) { "https://${state.snapshot.settings.apiHost}/" }
                suspend fun requireDownloadNetwork(): DownloadNetworkState {
                    downloadServices.networkPolicy.ready.await()
                    return downloadServices.networkPolicy.state.value.also { policy ->
                        check(policy.allowed) { policy.unavailableReason ?: "当前网络不允许下载" }
                    }
                }
                val boundClients = AppResourcePool<Pair<Long, HttpClient>, CopyMangaClient> { it.close() }
                cleanup += boundClients::close
                suspend fun boundClient(): CopyMangaClient {
                    val generation = state.snapshot.generation
                    val selected = downloadServices.clientProvider.client(requireDownloadNetwork())
                    state.requireCurrent(generation)
                    return boundClients.get(generation to selected) { client.withEngineClient(selected, services.proxyPolicy, generation) }
                }
                val themes = PersistentThemeCatalogs(documents)
                themes.initialize().onFailure { startupWarnings += "题材缓存无法读取，将暂用内存资料：${it.message}" }
                val manga = PortableMangaRepository(client, themeCache = themes, downloadClient = { boundClient() })
                val discovery = PortableComicDiscoveryRepository(client, themeCatalog = themes)
                val novelCache = VerifiedPortableNovelCache(cache)
                val debugStore = com.rchdliang.copymanga_neo.domain.reading.NovelImagePositionDebugStore()
                val novels = PortableNovelRepository(client, novelCache,
                    requireDownloadNetwork = { requireDownloadNetwork() }, downloadClient = { boundClient() },
                    imagePositionDebug = debugStore, imagePositionDebugEnabled = { services.debugBuild }, themeCatalog = themes,
                    inspectImage = services.readerImageIo::inspect)
                novels.refreshCachedVolumes().getOrThrow()
                val novelShelf = PortableNovelShelfRepository(client)
                val progress = PortableReadingProgress(documents, state, manga, appScope)
                val records = DownloadRecords(documents, cache).also {
                    try { it.awaitReady() }
                    catch (cancel: CancellationException) { throw cancel }
                    catch (error: Exception) { startupWarnings += error.message ?: "下载记录无法读取，原数据已保留" }
                }
                val access = LocalDownloadAccess()
                val comments = PortableCommentRepository(client, DownloadedCommentStore(records))
                val manager = DownloadManager(manga, records, cache, KtorDownloadImageTransport(downloadServices.clientProvider, networkState = state),
                    downloadServices.runtime, DownloadQueueStore(documents), downloadServices.networkPolicy, network, comments, access, novels, session, appScope)
                try { manager.awaitRestoration() }
                catch (cancel: CancellationException) { throw cancel }
                catch (error: Exception) {
                    startupWarnings += "下载队列无法恢复，原记录已保留，下载操作已停用；修复存储后请重新启动：${error.message.orEmpty()}"
                }
                val downloads = PortableDownloadRepository(records, manager, access)
                val export = PortableComicExportRepository(downloads, services.comicExportIo, documents, access, appScope)
                export.awaitRestoration().onFailure { error ->
                    startupWarnings += error.message ?: "导出任务无法读取，原数据已保留"
                }
                val loaders = AppResourcePool<Int, ImageLoader> { it.shutdown() }
                cleanup += loaders::close
                // UI remembers loader instances. Keep them stable across login/settings generations;
                // the fetcher reads the current client state per request. Timeout keys are bounded.
                fun imageLoader(timeout: Int): ImageLoader {
                    val seconds = timeout.coerceIn(5, 60)
                    return loaders.get(seconds) {
                        ImageLoader.Builder(services.imageContext).diskCache(services.imageDiskCache)
                            .components {
                                platformLocalImageFetcherFactory { seconds }?.let { add(it) }
                                add(copyMangaImageFetcherFactory(client) { seconds })
                            }.build()
                    }
                }
                appScope.launch {
                    state.snapshots.map { it.generation }.distinctUntilChanged().collectLatest { generation ->
                        state.awaitRetiredRequests(generation)
                        manager.awaitActiveImageRequests()
                        currentCoroutineContext().ensureActive()
                        boundClients.retire { it.first < generation }
                        loaders.forEach { it.memoryCache?.clear() }
                        cleanClients.retire { it.first < generation }
                    }
                }
                val imageActions = PortableReaderImageRepository(KtorReaderImageTransport(client), services.readerImageIo)
                val storageBase = PortableStorageRepository(cache, novelCache)
                suspend fun storageAction(block: suspend () -> Unit): Result<Unit> = try {
                    block(); Result.success(Unit)
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) { Result.failure(failure) }
                val storage = object : StorageRepository by storageBase {
                    override suspend fun getUsage(): Result<StorageUsage> = storageBase.getUsage().map { usage ->
                        usage.copy(imageCacheBytes = services.imageDiskCache?.size ?: usage.imageCacheBytes)
                    }
                    override suspend fun clearImageCache(): Result<Unit> = storageAction {
                        loaders.forEach { it.memoryCache?.clear() }
                        services.imageDiskCache?.clear() ?: storageBase.clearImageCache().getOrThrow()
                    }
                    override suspend fun clearNovelCache(): Result<Unit> = storageAction {
                        // Wait for downloads to exit before clearing, so a late response cannot republish a volume.
                        downloads.cancelAllNovels()
                        novels.clearCache().getOrThrow()
                    }
                }
                val repositories = AppRepositories(manga, discovery, novels, novelShelf, users, session, downloads, comments,
                    PortableShareLinkRepository(client, network::persistShareHosts), localReading, progress, appSettings, readerPreferences, readerTools,
                    novelPreferences, annotations, blacklist, network, risk, storage,
                    PortableSettingsBackup(appSettings, readerPreferences, localReading, readerTools), logs, imageActions, export, debugStore)
                completed = true
                return PortableAppGraph(repositories, ::imageLoader, startupWarnings, ::closeAll, { drainGraphLifetime(lifetime, ::closeAll) })
            } finally { if (!completed) drainGraphLifetime(lifetime, ::closeAll) }
        }
    }
}
