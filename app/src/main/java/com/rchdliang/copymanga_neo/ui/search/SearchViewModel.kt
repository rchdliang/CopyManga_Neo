package com.rchdliang.copymanga_neo.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rchdliang.copymanga_neo.data.remote.dto.ComicDto
import com.rchdliang.copymanga_neo.domain.repository.MangaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 搜索 UI 状态
 */
data class SearchUiState(
    val query: String = "",
    val searchResults: List<ComicDto> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val currentPage: Int = 1
)

/**
 * 搜索 ViewModel
 */
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val mangaRepository: MangaRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    /**
     * 更新搜索关键词并执行搜索
     */
    fun search(query: String) {
        // 取消之前的搜索任务
        searchJob?.cancel()

        // 防抖：延迟 500ms 后再执行搜索
        searchJob = viewModelScope.launch {
            delay(500)
            _uiState.value = _uiState.value.copy(
                query = query,
                isLoading = true,
                error = null
            )

            if (query.isBlank()) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    searchResults = emptyList()
                )
                return@launch
            }

            performSearch(query)
        }
    }

    private suspend fun performSearch(query: String) {
        mangaRepository.searchComics(query, _uiState.value.currentPage).fold(
            onSuccess = { comics ->
                _uiState.value = _uiState.value.copy(
                    searchResults = comics,
                    isLoading = false
                )
            },
            onFailure = { error ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = error.message
                )
            }
        )
    }

    /**
     * 刷新搜索
     */
    fun refresh() {
        if (_uiState.value.query.isNotBlank()) {
            viewModelScope.launch {
                performSearch(_uiState.value.query)
            }
        }
    }

    /**
     * 加载更多搜索结果
     */
    suspend fun loadMore() {
        if (_uiState.value.isLoading || _uiState.value.query.isBlank()) return
        _uiState.value = _uiState.value.copy(
            currentPage = _uiState.value.currentPage + 1
        )
        performSearch(_uiState.value.query)
    }
}
