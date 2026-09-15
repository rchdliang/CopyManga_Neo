package com.rchdliang.copymanga_neo.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rchdliang.copymanga_neo.domain.model.BrowseHistoryItem
import com.rchdliang.copymanga_neo.domain.repository.MangaRepository
import com.rchdliang.copymanga_neo.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HistoryUiState(
    val isCheckingLogin: Boolean = true,
    val isLoggedIn: Boolean = false,
    val items: List<BrowseHistoryItem> = emptyList(),
    val nextOffset: Int? = null,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
    val loadMoreError: String? = null,
    val isClearing: Boolean = false,
    val showClearConfirmation: Boolean = false,
    val clearCompleted: Boolean = false,
    val toastMessage: String? = null
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val mangaRepository: MangaRepository,
    private val userRepository: UserRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState = _uiState.asStateFlow()
    private var loadJob: Job? = null
    private var clearJob: Job? = null
    private var hasResumed = false

    private val exceptionHandler = CoroutineExceptionHandler { _, error ->
        android.util.Log.e("HistoryViewModel", "浏览记录操作失败", error)
        _uiState.update {
            it.copy(
                isCheckingLogin = false,
                isRefreshing = false,
                isLoadingMore = false,
                isClearing = false,
                error = error.message ?: "浏览记录操作失败",
                toastMessage = error.message ?: "浏览记录操作失败"
            )
        }
    }

    init {
        viewModelScope.launch(exceptionHandler) {
            userRepository.isAuthCacheReady().first { it }
            combine(
                userRepository.isLoggedIn(),
                userRepository.getUsername()
            ) { loggedIn, username ->
                loggedIn to username
            }.distinctUntilChanged().collect { (loggedIn, _) ->
                // 会话切换时取消请求，防止旧账号的数据重新填回列表。
                loadJob?.cancel()
                clearJob?.cancel()
                _uiState.value = HistoryUiState(isCheckingLogin = false, isLoggedIn = loggedIn)
                if (loggedIn) refresh()
            }
        }
    }

    /** 初次由认证状态触发加载；再次进入或阅读后返回时刷新。 */
    fun onScreenResumed() {
        if (hasResumed) refresh()
        hasResumed = true
    }

    fun refresh() {
        if (!_uiState.value.isLoggedIn || _uiState.value.isClearing) return
        loadJob?.cancel()
        loadPage(offset = 0, refresh = true)
    }

    fun loadMore() {
        val state = _uiState.value
        val offset = state.nextOffset ?: return
        if (!state.isLoggedIn || state.isClearing || loadJob?.isActive == true) return
        loadPage(offset = offset, refresh = false)
    }

    private fun loadPage(offset: Int, refresh: Boolean) {
        loadJob = viewModelScope.launch(exceptionHandler) {
            _uiState.update {
                it.copy(
                    isRefreshing = refresh,
                    isLoadingMore = !refresh,
                    error = if (refresh) null else it.error,
                    loadMoreError = null
                )
            }
            val result = mangaRepository.getBrowseHistory(offset)
            currentCoroutineContext().ensureActive()
            result.fold(
                onSuccess = { page ->
                    _uiState.update {
                        val items = if (refresh) page.items else it.items + page.items
                        it.copy(
                            items = items.distinctBy { item -> item.id },
                            nextOffset = page.nextOffset,
                            isRefreshing = false,
                            isLoadingMore = false,
                            error = null,
                            loadMoreError = null
                        )
                    }
                },
                onFailure = { error ->
                    val message = error.message ?: "加载浏览记录失败"
                    _uiState.update {
                        it.copy(
                            isRefreshing = false,
                            isLoadingMore = false,
                            error = if (refresh) message else it.error,
                            loadMoreError = if (refresh) null else message,
                            toastMessage = if (refresh && it.items.isNotEmpty()) message else null
                        )
                    }
                }
            )
        }
    }

    fun requestClear() {
        val state = _uiState.value
        if (state.isLoggedIn && state.items.isNotEmpty() && !state.isClearing) {
            _uiState.update { it.copy(showClearConfirmation = true) }
        }
    }

    fun dismissClearConfirmation() {
        _uiState.update { it.copy(showClearConfirmation = false) }
    }

    /** 只在用户确认后发送云端清空请求，成功前保留现有记录。 */
    fun confirmClear() {
        val state = _uiState.value
        if (!state.showClearConfirmation || !state.isLoggedIn || state.isClearing) return
        loadJob?.cancel()
        _uiState.update {
            it.copy(
                showClearConfirmation = false,
                isClearing = true,
                isRefreshing = false,
                isLoadingMore = false
            )
        }
        clearJob = viewModelScope.launch(exceptionHandler) {
            val result = mangaRepository.clearBrowseHistory()
            currentCoroutineContext().ensureActive()
            result.fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            items = emptyList(),
                            nextOffset = null,
                            error = null,
                            loadMoreError = null,
                            isClearing = false,
                            clearCompleted = true,
                            toastMessage = "浏览记录已清空"
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isClearing = false,
                            toastMessage = error.message ?: "清空失败，请重试"
                        )
                    }
                }
            )
        }
    }

    fun consumeClearCompleted() {
        _uiState.update { it.copy(clearCompleted = false) }
    }

    fun clearToastMessage() {
        _uiState.update { it.copy(toastMessage = null) }
    }
}
