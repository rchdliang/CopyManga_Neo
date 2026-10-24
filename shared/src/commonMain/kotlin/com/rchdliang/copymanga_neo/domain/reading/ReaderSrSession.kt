package com.rchdliang.copymanga_neo.domain.reading

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okio.ByteString.Companion.encodeUtf8

data class SrPage(val id: String, val source: String)
data class SrUserSettings(val enabled: Boolean = false,
    val mode: SrExecutionMode = SrExecutionMode.AUTO, val cpuFallback: Boolean = true)

enum class SrModelOperation { DOWNLOAD, DELETE }

sealed interface SrModelState {
    data object Missing : SrModelState
    data object Cancelled : SrModelState
    data object Preparing : SrModelState
    data object Ready : SrModelState
    data object Deleting : SrModelState
    data class Downloading(val bytes: Long, val total: Long) : SrModelState
    data class Failed(val message: String, val operation: SrModelOperation = SrModelOperation.DOWNLOAD) : SrModelState
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
    val originalsReady: StateFlow<Map<String, Int>> = ready.asStateFlow()
    private val coordinator = ReaderSrCoordinator(scope, platform, pixelBufferBudget = platform.budgetBytes, timeoutMillis = 60_000)
    private data class ActiveRequest(val value: SrRequest, val revision: Int, val renderId: Long)
    private var nextRenderId = 0L
    private val request = MutableStateFlow<ActiveRequest?>(null)
    private val rendered = MutableStateFlow<SrPresentationReceipt?>(null)
    val presentationReceipt: StateFlow<SrPresentationReceipt?> = rendered.asStateFlow()
    private var renderOwner: Any? = null
    private val failedPreparation = MutableStateFlow<String?>(null)
    val preparationFailedPage: StateFlow<String?> = failedPreparation.asStateFlow()
    private val preparation = MutableStateFlow<String?>(null)
    val models = platform.models
    val detail = platform.detail
    private val modelRefresh = scope.launch { models?.refresh() }
    val unavailableReason get() = platform.unavailableReason
    val frame: StateFlow<SrFrame?> = combine(request, coordinator.states, selected, settings, ready) { current, states, page, options, originals ->
        current?.takeIf { options.enabled && page?.id == it.value.source.scope && originals[page.id] == it.revision &&
            it.value.parameters.mode == options.mode && it.value.parameters.cpuFallback == options.cpuFallback }?.let {
            SrFrame(it.value.source.scope, it.revision, it.renderId, it.value.parameters, states[it.value.cacheKey] ?: SrStatus.Original)
        }
    }.combine(models?.state ?: MutableStateFlow<SrModelState>(SrModelState.Ready)) { frame, model ->
        frame.takeIf { model == SrModelState.Ready }
    }.stateIn(scope, SharingStarted.Eagerly, null)
    val display: StateFlow<Pair<String, SrStatus>?> = frame.map { it?.let { it.pageId to it.status } }
        .stateIn(scope, SharingStarted.Eagerly, null)
    val preparationMessage: StateFlow<String?> = preparation.asStateFlow()
    private val worker = scope.launch {
        combine(settings, selected, ready, models?.state ?: MutableStateFlow<SrModelState>(SrModelState.Ready)) { settings, page, ready, model -> Triple(settings.copy(enabled = settings.enabled && model == SrModelState.Ready), page, page?.let { ready[it.id] }) }
            .distinctUntilChanged().collectLatest { (settings, page, revision) ->
                coordinator.submit(null); request.value = null; preparation.value = null
                rendered.value = null; renderOwner = null; failedPreparation.value = null
                if (!settings.enabled || page == null || revision == null) return@collectLatest
                platform.unavailableReason?.let { preparation.value = it; return@collectLatest }
                try {
                    val source = platform.source(page)
                    ensureActive()
                    val next = SrRequest(source, ReaderSrModel.model,
                        SrParameters(mode = settings.mode, cpuFallback = settings.cpuFallback))
                    request.value = ActiveRequest(next, revision, ++nextRenderId)
                    coordinator.submit(next) // Current only; no speculative whole-chapter or spread queue.
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (error: Exception) { failedPreparation.value = page.id; preparation.value = "增强准备失败，继续显示原图" }
            }
    }
    fun select(page: SrPage?) {
        if (selected.value != page) { request.value = null; rendered.value = null; renderOwner = null; failedPreparation.value = null }
        selected.value = page
    }
    fun originalReady(id: String, revision: Int = 0) {
        if (selected.value?.id == id && ready.value[id] != revision) { request.value = null; rendered.value = null; renderOwner = null }
        ready.value = (ready.value.toList().filterNot { it.first == id }.takeLast(2) + (id to revision)).toMap()
    }
    fun isCurrentFrame(candidate: SrFrame): Boolean = request.value?.renderId == candidate.renderId &&
        selected.value?.id == candidate.pageId && ready.value[candidate.pageId] == candidate.revision &&
        matchingSrFrame(candidate, candidate.pageId, candidate.revision, settings.value) != null &&
        (models == null || models.state.value == SrModelState.Ready)
    /** Called only after the chosen image's draw pass, never merely on inference completion. */
    fun imageDrawn(owner: Any, candidate: SrFrame, artifact: SrArtifact, image: SrRenderedImage) {
        val active = request.value ?: return
        if (active.renderId != candidate.renderId || selected.value?.id != candidate.pageId ||
            matchingSrFrame(frame.value, candidate.pageId, candidate.revision, settings.value)?.renderId != candidate.renderId ||
            (frame.value?.status as? SrStatus.Ready)?.artifact !== artifact ||
            (models != null && models.state.value != SrModelState.Ready)) return
        renderOwner = owner
        rendered.value = SrPresentationReceipt(candidate.renderId, artifact, image)
    }
    fun imageDetached(owner: Any) { if (renderOwner === owner) { rendered.value = null; renderOwner = null } }
    fun close() { modelRefresh.cancel(); worker.cancel(); coordinator.close(); request.value = null; rendered.value = null; renderOwner = null; ready.value = emptyMap() }
}
