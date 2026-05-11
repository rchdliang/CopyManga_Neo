package com.rchdliang.copymanga_neo.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rchdliang.copymanga_neo.data.remote.dto.ComicDto
import com.rchdliang.copymanga_neo.domain.repository.MangaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 首页 UI 状态
 */
data class HomeUiState(
    val comicList: List<ComicDto> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val currentPage: Int = 1
)

/**
 * 首页 ViewModel
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val mangaRepository: MangaRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadComics()
    }

    fun loadComics() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            mangaRepository.getHomeComics(
                page = _uiState.value.currentPage,
                limit = 20
            ).fold(
                onSuccess = { comics ->
                    _uiState.value = _uiState.value.copy(
                        comicList = comics,
                        isLoading = false
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = error.message
                    )
                    android.util.Log.e(
                        "HomeViewModel",
                        "Failed to load comics: ${error.message}",
                        error
                    )
                }
            )
        }
    }

    fun refresh() {
        _uiState.value = _uiState.value.copy(currentPage = 1)
        loadComics()
    }

    fun loadMore() {
        if (_uiState.value.isLoading) return
        _uiState.value = _uiState.value.copy(
            currentPage = _uiState.value.currentPage + 1
        )
        loadComics()
    }
}
