package com.rchdliang.copymanga_neo.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rchdliang.copymanga_neo.data.remote.api.QueryType
import com.rchdliang.copymanga_neo.data.remote.dto.ComicDto
import com.rchdliang.copymanga_neo.domain.repository.MangaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
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
    val isLoadingMore: Boolean = false,
    val error: String? = null,
    val currentPage: Int = 1,
    val hasMore: Boolean = false,
    val queryType: QueryType? = null
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
    private var loadMoreJob: Job? = null

    fun updateQuery(query: String) {
        _uiState.value = _uiState.value.copy(query = query)
    }

    fun clearSearch() {
        searchJob?.cancel()
        loadMoreJob?.cancel()
        _uiState.value = SearchUiState()
    }

    fun updateQueryType(queryType: QueryType?) {
        _uiState.value = _uiState.value.copy(queryType = queryType)
        if (_uiState.value.query.isNotBlank()) {
            search(_uiState.value.query)
        }
    }

    /**
     * 更新搜索关键词并执行搜索
     */
    fun search(query: String) {
        searchJob?.cancel()
        loadMoreJob?.cancel()

        searchJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                query = query,
                isLoading = true,
                error = null,
                currentPage = 1,
                hasMore = false
            )

            if (query.isBlank()) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    searchResults = emptyList()
                )
                return@launch
            }

            performSearch(query, isLoadMore = false)
        }
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.isLoading || state.isLoadingMore || !state.hasMore || state.query.isBlank()) return

        loadMoreJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingMore = true)
            performSearch(state.query, isLoadMore = true)
        }
    }

    private suspend fun performSearch(query: String, isLoadMore: Boolean) {
        val page = if (isLoadMore) _uiState.value.currentPage + 1 else 1
        mangaRepository.searchComics(query, page, _uiState.value.queryType)
            .fold(
                onSuccess = { comics ->
                    _uiState.value = _uiState.value.copy(
                        searchResults = if (isLoadMore) _uiState.value.searchResults + comics else comics,
                        isLoading = false,
                        isLoadingMore = false,
                        currentPage = page,
                        hasMore = comics.size >= 20
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isLoadingMore = false,
                        error = if (isLoadMore) _uiState.value.error else error.message
                    )
                }
            )
    }
}
