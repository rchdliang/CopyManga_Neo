package com.rchdliang.copymanga_neo.ui.components

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rchdliang.copymanga_neo.domain.model.*
import com.rchdliang.copymanga_neo.domain.repository.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class NovelPreviewUiState(
    val book: NovelBook? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val epoch: Long = 0
)

/** 仅展开时补全小说资料；会话隔离、取消和有限缓存不依赖漫画详情接口。 */
@dagger.hilt.android.lifecycle.HiltViewModel
class NovelPreviewViewModel @javax.inject.Inject constructor(
    private val repository: NovelRepository,
    private val sessions: ReadingSessionRepository,
    private val network: NetworkSettingsRepository
) : ViewModel() {
    private data class Context(val session: LocalReadingSession?, val settings: NetworkSettings)
    private data class Cached(val book: NovelBook, val time: Long)
    private val mutable = MutableStateFlow(NovelPreviewUiState())
    val state = mutable.asStateFlow()
    private val cache = linkedMapOf<String, Cached>()
    private var context = currentContext()
    private var revision = 0L
    private var job: Job? = null
    private var seed: NovelBook? = null
    private val errors = CoroutineExceptionHandler { _, error ->
        mutable.update { it.copy(loading = false, error = error.message ?: "简介加载失败") }
    }

    init {
        viewModelScope.launch(errors) {
            combine(sessions.cacheReadyFlow, sessions.sessionChanges, network.settings) { _, _, _ ->
                currentContext()
            }.distinctUntilChanged().collect { updateContext(it) }
        }
    }

    private fun currentContext() = Context(sessions.getReadingSessionSync(), network.settings.value)
    private fun updateContext(next: Context) {
        if (next == context) return
        context = next
        revision++
        job?.cancel()
        seed = null
        cache.clear()
        mutable.value = NovelPreviewUiState(epoch = mutable.value.epoch + 1)
    }

    fun show(book: NovelBook?) {
        updateContext(currentContext())
        seed = book
        val version = ++revision
        job?.cancel()
        if (book == null) {
            mutable.update { it.copy(loading = false) }
            return
        }
        val now = System.currentTimeMillis()
        val cached = cache.remove(book.path)?.takeIf {
            now - it.time in 0 until 25L * 60 * 60 * 1000 &&
                (book.updated.isBlank() || book.updated == it.book.updated)
        }?.also { cache[book.path] = it }
        val available = cached?.book?.withFallback(book) ?: book
        mutable.value = NovelPreviewUiState(available, epoch = mutable.value.epoch)
        if (cached != null || available.brief.isNotBlank()) return
        load(book, version, context)
    }

    fun retry() {
        val book = seed ?: return
        updateContext(currentContext())
        if (seed == null) return
        cache.remove(book.path)
        job?.cancel()
        load(book, ++revision, context)
    }

    private fun load(book: NovelBook, version: Long, captured: Context) {
        mutable.update { it.copy(loading = true, error = null) }
        job = viewModelScope.launch(errors) {
            try {
                sessions.awaitReady()
                val session = captured.session
                val owner = session?.owner
                if (owner == null || captured != currentContext()) {
                    if (version == revision) mutable.update {
                        it.copy(loading = false, error = "会话已变化，请重试")
                    }
                    return@launch
                }
                repository.detail(session.mode, owner, book.path).fold(
                    onSuccess = { detail ->
                        ensureActive()
                        if (version != revision || captured != currentContext()) return@fold
                        if (detail.path != book.path) {
                            mutable.update { it.copy(loading = false, error = "小说资料不匹配，请重试") }
                            return@fold
                        }
                        val resolved = detail.withFallback(book)
                        cache[book.path] = Cached(resolved, System.currentTimeMillis())
                        while (cache.size > 32) cache.remove(cache.keys.first())
                        mutable.update { it.copy(book = resolved, loading = false, error = null) }
                    },
                    onFailure = { error ->
                        if (error is CancellationException) throw error
                        ensureActive()
                        if (version == revision && captured == currentContext()) mutable.update {
                            it.copy(loading = false, error = error.message ?: "简介加载失败")
                        }
                    })
            } catch (cancel: CancellationException) {
                if (isActive && version == revision && captured == currentContext()) mutable.update {
                    it.copy(loading = false, error = "请求已取消，请重试")
                }
                throw cancel
            }
        }
    }
}
