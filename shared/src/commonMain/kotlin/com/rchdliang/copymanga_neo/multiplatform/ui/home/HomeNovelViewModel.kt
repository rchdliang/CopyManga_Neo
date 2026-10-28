package com.rchdliang.copymanga_neo.multiplatform.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rchdliang.copymanga_neo.domain.model.LocalReadingSession
import com.rchdliang.copymanga_neo.domain.model.NovelBook
import com.rchdliang.copymanga_neo.domain.repository.NovelRepository
import com.rchdliang.copymanga_neo.domain.repository.ReadingSessionRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.rchdliang.copymanga_neo.multiplatform.app.StartupTrace
import com.rchdliang.copymanga_neo.multiplatform.app.measure

data class HomeNovelUiState(
    val books: List<NovelBook> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null
)

/** 小说预览独立加载，失败不影响漫画首页；会话变化取消旧请求。 */
class HomeNovelViewModel constructor(
    private val repository: NovelRepository,
    private val auth: ReadingSessionRepository,
    autoStart: Boolean = true,
    private val startupTrace: StartupTrace = StartupTrace.None
) : ViewModel() {
    private val refresh = MutableStateFlow(0)
    private val mutable = MutableStateFlow(HomeNovelUiState())
    val state = mutable.asStateFlow()
    private val errors = CoroutineExceptionHandler { _, e ->
        mutable.update { it.copy(loading = false, error = e.message ?: "轻小说加载失败") }
    }

    private var started = false
    init { if (autoStart) start() }

    fun start() {
        if (started) return
        started = true
        viewModelScope.launch(errors) {
            combine(auth.cacheReadyFlow, auth.sessionChanges, refresh) { ready, _, revision ->
                (if (ready) auth.getReadingSessionSync() else null) to revision
            }.distinctUntilChanged().collectLatest { (session, _) ->
                // 公共列表在账号/API 切换时继续展示，旧请求仍由会话检查拦截。
                mutable.update { it.copy(loading = true, error = null) }
                val owner = session?.owner ?: return@collectLatest
                try {
                    val result = withContext(Dispatchers.Default) {
                        startupTrace.mark("home_novel_requests_start")
                        startupTrace.measure("home_novel_response") {
                            repository.discover(session.mode, owner, 0, "-datetime_updated", "", "")
                        }
                    }
                    currentCoroutineContext().ensureActive()
                    if (auth.getReadingSessionSync() == session) {
                        result.fold(
                            onSuccess = { page ->
                                mutable.value =
                                    HomeNovelUiState(books = page.books.take(12), loading = false)
                            },
                            onFailure = { error ->
                                if (error is CancellationException) throw error
                                mutable.update {
                                    it.copy(
                                        loading = false,
                                        error = "轻小说暂时无法加载"
                                    )
                                }
                            }
                        )
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    currentCoroutineContext().ensureActive()
                    if (auth.getReadingSessionSync() == session) {
                        mutable.update { it.copy(loading = false, error = "轻小说暂时无法加载") }
                    }
                }
            }
        }
    }

    fun retry() {
        refresh.update { it + 1 }
    }
}
