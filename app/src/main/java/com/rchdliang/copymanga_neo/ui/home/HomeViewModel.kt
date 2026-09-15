package com.rchdliang.copymanga_neo.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rchdliang.copymanga_neo.domain.model.ComicSummary
import com.rchdliang.copymanga_neo.domain.model.HomeComicsData
import com.rchdliang.copymanga_neo.domain.repository.MangaRepository
import com.rchdliang.copymanga_neo.domain.repository.ThemeBlacklistRepository
import com.rchdliang.copymanga_neo.domain.usecase.FilterHomeComics
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
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
    val filterWarning: String? = null
)

/**
 * 首页 ViewModel
 */
@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel @Inject constructor(
    private val mangaRepository: MangaRepository,
    themeBlacklistRepository: ThemeBlacklistRepository,
    private val filterHomeComics: FilterHomeComics
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())

    // 保留原始列表；黑名单变化会取消旧过滤任务，重新核对缺失的题材。
    // 补取完成前不展示未知条目，避免将空 theme 当作已通过过滤。
    val uiState: StateFlow<HomeUiState> = combine(
        _uiState,
        themeBlacklistRepository.blockedThemes
    ) { state, blockedThemes -> state to blockedThemes }
        .transformLatest { (state, blockedThemes) ->
            if (blockedThemes.isEmpty()) {
                emit(state)
                return@transformLatest
            }
            val source = HomeComicsData(
                state.recComicList, state.hotComics, state.newComics, state.finishComics
            )
            val preview = filterHomeComics.verifiedPreview(source, blockedThemes)
            emit(state.withComics(preview).copy(isLoading = true))
            val result = filterHomeComics.filter(source, blockedThemes)
            emit(
                state.withComics(result.comics).copy(
                    filterWarning = if (result.unresolvedCount > 0)
                        "${result.unresolvedCount} 部漫画的标签暂时无法读取，已暂时隐藏，可下拉刷新重试。"
                    else null
                )
            )
        }.catch { error ->
            android.util.Log.e("HomeViewModel", "读取题材黑名单失败", error)
            emit(HomeUiState(error = "读取标签黑名单失败，请重启应用后重试"))
        }.stateIn(viewModelScope, SharingStarted.Eagerly, HomeUiState(isLoading = true))

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

    private var loadJob: Job? = null

    init {
        loadComics()
    }

    fun loadComics() {
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
