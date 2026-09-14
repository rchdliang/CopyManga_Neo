package com.rchdliang.copymanga_neo.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rchdliang.copymanga_neo.domain.model.ComicSummary
import com.rchdliang.copymanga_neo.domain.model.withoutBlockedThemes
import com.rchdliang.copymanga_neo.domain.repository.MangaRepository
import com.rchdliang.copymanga_neo.domain.repository.ThemeBlacklistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
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
    val toastMessage: String? = null
)

/**
 * 首页 ViewModel
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val mangaRepository: MangaRepository,
    themeBlacklistRepository: ThemeBlacklistRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())

    // 保留原始列表，让黑名单变化即时生效，解除屏蔽也无需重新请求。
    // combine 等待本地黑名单首次读取，避免启动时闪现已屏蔽漫画。
    val uiState: StateFlow<HomeUiState> = combine(
        _uiState,
        themeBlacklistRepository.blockedThemes
    ) { state, blockedThemes ->
        state.copy(
            recComicList = state.recComicList.withoutBlockedThemes(blockedThemes),
            hotComics = state.hotComics.withoutBlockedThemes(blockedThemes),
            newComics = state.newComics.withoutBlockedThemes(blockedThemes),
            finishComics = state.finishComics.withoutBlockedThemes(blockedThemes)
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

    init {
        loadComics()
    }

    fun loadComics() {
        viewModelScope.launch(exceptionHandler) {
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
