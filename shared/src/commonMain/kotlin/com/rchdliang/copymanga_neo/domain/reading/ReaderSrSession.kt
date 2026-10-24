package com.rchdliang.copymanga_neo.domain.reading

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okio.ByteString.Companion.encodeUtf8

data class SrPage(val id: String, val source: String)
data class SrUserSettings(val enabled: Boolean = false,
    val mode: SrExecutionMode = SrExecutionMode.AUTO, val cpuFallback: Boolean = true)

sealed interface SrModelState {
    data object Missing : SrModelState
    data object Cancelled : SrModelState
    data object Preparing : SrModelState
    data object Ready : SrModelState
    data class Downloading(val bytes: Long, val total: Long) : SrModelState
    data class Failed(val message: String) : SrModelState
}
interface ReaderSrModels {
    val state: StateFlow<SrModelState>
    val downloadBytes: Long
    suspend fun refresh()
    fun startDownload()
    fun cancelDownload()
    suspend fun download()
    suspend fun delete()
}

interface ReaderSrPlatform : ReaderSrBackend {
    val models: ReaderSrModels? get() = null
    val budgetBytes: Long
    val detail: StateFlow<String>
    suspend fun source(page: SrPage): SrSource
}

object ReaderSrModel {
    const val weights = "d82ed30207fd07e1c16726a9ca838ce44e6e25a105b0278c7ca681516ee0e18e"
    const val graph = "ccc3b2f1c9fad829ecdda40cac8dc853fcddf14917170efba888b256fbf9074a"
    const val runtime = "waifu2x-20250915-ncnn-c4193aad-cmp-sr1"
    val model = SrModel("waifu2x-cunet", "20250915", (weights + graph).encodeUtf8().sha256().hex(), runtime)
}

/** One reader session, keyed by real page IDs, independent of spread/pager layout. */
class ReaderSrSession(scope: CoroutineScope, private val platform: ReaderSrPlatform) {
    val settings = MutableStateFlow(SrUserSettings())
    private val selected = MutableStateFlow<SrPage?>(null)
    private val ready = MutableStateFlow<Map<String, Int>>(emptyMap())
    private val coordinator = ReaderSrCoordinator(scope, platform, pixelBufferBudget = platform.budgetBytes, timeoutMillis = 60_000)
    private val request = MutableStateFlow<SrRequest?>(null)
    private val preparation = MutableStateFlow<String?>(null)
    val models = platform.models
    val detail = platform.detail
    private val modelRefresh = scope.launch { models?.refresh() }
    val unavailableReason get() = platform.unavailableReason
    val display: StateFlow<Pair<String, SrStatus>?> = combine(request, coordinator.states) { current, states ->
        current?.let { it.source.scope to (states[it.cacheKey] ?: SrStatus.Original) }
    }.stateIn(scope, SharingStarted.Eagerly, null)
    val preparationMessage: StateFlow<String?> = preparation.asStateFlow()
    private val worker = scope.launch {
        combine(settings, selected, ready, models?.state ?: MutableStateFlow<SrModelState>(SrModelState.Ready)) { settings, page, ready, model -> Triple(settings.copy(enabled = settings.enabled && model == SrModelState.Ready), page, page?.let { ready[it.id] }) }
            .distinctUntilChanged().collectLatest { (settings, page, revision) ->
                coordinator.submit(null); request.value = null; preparation.value = null
                if (!settings.enabled || page == null || revision == null) return@collectLatest
                platform.unavailableReason?.let { preparation.value = it; return@collectLatest }
                try {
                    val source = platform.source(page)
                    ensureActive()
                    val next = SrRequest(source, ReaderSrModel.model,
                        SrParameters(mode = settings.mode, cpuFallback = settings.cpuFallback))
                    request.value = next
                    coordinator.submit(next) // Current only; no speculative whole-chapter or spread queue.
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (error: Exception) { preparation.value = error.message ?: "超分准备失败，继续显示原图" }
            }
    }
    fun select(page: SrPage?) { selected.value = page }
    fun originalReady(id: String, revision: Int = 0) { ready.value = (ready.value.toList().filterNot { it.first == id }.takeLast(2) + (id to revision)).toMap() }
    fun close() { modelRefresh.cancel(); worker.cancel(); coordinator.close(); request.value = null; ready.value = emptyMap() }
}
