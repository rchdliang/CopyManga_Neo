package com.rchdliang.copymanga_neo.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rchdliang.copymanga_neo.domain.model.ComicSummary
import com.rchdliang.copymanga_neo.domain.model.HomeComicsData
import com.rchdliang.copymanga_neo.domain.repository.MangaRepository
import com.rchdliang.copymanga_neo.domain.repository.RiskControlNotice
import com.rchdliang.copymanga_neo.domain.repository.RiskControlRepository
import com.rchdliang.copymanga_neo.domain.repository.ThemeBlacklistRepository
import com.rchdliang.copymanga_neo.domain.usecase.FilterHomeComics
import com.rchdliang.copymanga_neo.domain.usecase.FilteredHomeComics
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 首页 UI 状态
 */
data class HomeUiState(
    val recComicList: List<ComicSummary> = emptyList(),
    val hotComics: List<ComicSummary> = emptyList(),
    val newComics: List<ComicSummary> = emptyList(),
    val finishComics: List<ComicSummary> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val toastMessage: String? = null,
    val filterWarning: String? = null,
    val riskControl: RiskControlNotice? = null,
    val showRiskDialog: Boolean = false
)

/**
 * 首页 ViewModel
 */
@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel @Inject constructor(
    private val mangaRepository: MangaRepository,
    themeBlacklistRepository: ThemeBlacklistRepository,
    private val filterHomeComics: FilterHomeComics,
    private val riskControl: RiskControlRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    private val dismissedRisk = MutableStateFlow<Long?>(null)

    private val blockedThemes = combine(
        themeBlacklistRepository.blockedThemes,
        themeBlacklistRepository.isEnabled
    ) { themes, enabled -> if (enabled) themes else emptyList() }

    // 弹窗确认、Toast 等无关状态变化不能重建标签请求队列或重新开始超时计时。
    private val source = _uiState.map { it.toComics() to it.isLoading }.distinctUntilChanged()
    private val filtered =
        combine(source, blockedThemes, riskControl.notice) { source, blocked, risk ->
            Triple(source, blocked, risk)
        }.flatMapLatest { (source, blocked, risk) ->
            if (risk != null) flowOf(FilteredHomeComics(HomeComicsData()))
            else filterHomeComics.observe(source.first, blocked, refresh = !source.second)
                .catch { error ->
                    if (error is CancellationException) throw error
                    android.util.Log.w("HomeViewModel", "标签后台补全已停止", error)
                    emit(FilteredHomeComics(source.first, showUnresolvedWarning = true))
            }
        }
    val uiState: StateFlow<HomeUiState> = combine(
        _uiState,
        filtered,
        riskControl.notice,
        dismissedRisk
    ) { state, result, risk, dismissed ->
        state.withComics(if (risk == null) result.comics else HomeComicsData()).copy(
            isLoading = risk == null && (state.isLoading || result.isResolving),
            filterWarning = if (risk == null && result.showUnresolvedWarning)
                "漫画标签未加载完成，某些漫画可能未按黑名单设置屏蔽" else null,
            riskControl = risk,
            showRiskDialog = risk != null && risk.detectedAt != dismissed
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState(isLoading = true))

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        android.util.Log.e("HomeViewModel", "Unhandled coroutine exception", throwable)
        _uiState.update {
            it.copy(
                isLoading = false,
                error = throwable.message,
                toastMessage = throwable.message ?: "操作失败"
            )
        }
    }

    fun clearToastMessage() {
        _uiState.update { it.copy(toastMessage = null) }
    }

    fun dismissRiskDialog() {
        dismissedRisk.value = riskControl.notice.value?.detectedAt
    }

    fun retryAfterRiskControl() {
        // 先停止旧列表的补全；闸门解除后统一由 notice 观察者加载一次首页。
        _uiState.value = HomeUiState(isLoading = true)
        riskControl.resumeByUser()
    }

    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            var wasBlocked = false
            riskControl.notice.collect { notice ->
                if (wasBlocked && notice == null) loadComics()
                wasBlocked = notice != null
            }
        }
        loadComics()
    }

    fun loadComics() {
        if (riskControl.notice.value != null) return
        loadJob?.cancel()
        loadJob = viewModelScope.launch(exceptionHandler) {
            _uiState.update { it.copy(isLoading = true, error = null) }
            mangaRepository.getHomeComics().fold(
                onSuccess = { homeComicsData ->
                    _uiState.update {
                        it.copy(
                            recComicList = homeComicsData.recComics,
                            hotComics = homeComicsData.hotComics,
                            newComics = homeComicsData.newComics,
                            finishComics = homeComicsData.finishComics,
                            isLoading = false
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = error.message,
                            toastMessage = error.message ?: "加载失败"
                        )
                    }
                    android.util.Log.e(
                        "HomeViewModel",
                        "Failed to load comics: ${error.message}",
                        error
                    )
                }
            )
        }
    }

}

private fun HomeUiState.withComics(data: HomeComicsData): HomeUiState = copy(
    recComicList = data.recComics,
    hotComics = data.hotComics,
    newComics = data.newComics,
    finishComics = data.finishComics
)

private fun HomeUiState.toComics() =
    HomeComicsData(recComicList, hotComics, newComics, finishComics)
