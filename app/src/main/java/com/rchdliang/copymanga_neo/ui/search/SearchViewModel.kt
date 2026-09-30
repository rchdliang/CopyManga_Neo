package com.rchdliang.copymanga_neo.ui.search

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rchdliang.copymanga_neo.domain.model.ComicFilterOption
import com.rchdliang.copymanga_neo.domain.model.ComicFilterOptions
import com.rchdliang.copymanga_neo.domain.model.ComicSummary
import com.rchdliang.copymanga_neo.domain.model.QueryType
import com.rchdliang.copymanga_neo.domain.model.Theme
import com.rchdliang.copymanga_neo.domain.repository.ComicDiscoveryRepository
import com.rchdliang.copymanga_neo.domain.repository.MangaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 搜索和题材筛选共用的列表状态。 */
data class SearchUiState(
    val query: String = "",
    val submittedQuery: String = "",
    val searchResults: List<ComicSummary> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
    val currentPage: Int = 1,
    val hasMore: Boolean = false,
    val queryType: QueryType? = null,
    val toastMessage: String? = null,
    val theme: Theme? = null,
    val nextOffset: Int? = null,
    val discoveryOptions: ComicFilterOptions? = null,
    val discoveryComics: List<ComicSummary> = emptyList(),
    val selectedDiscoveryTheme: ComicFilterOption? = null,
    val selectedDiscoveryTop: ComicFilterOption? = null,
    val discoveryNextOffset: Int? = null,
    val isDiscoveryLoading: Boolean = false,
    val isDiscoveryLoadingMore: Boolean = false,
    val discoveryOptionsError: String? = null,
    val discoveryError: String? = null
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val mangaRepository: MangaRepository,
    private val comicDiscoveryRepository: ComicDiscoveryRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        android.util.Log.e("SearchViewModel", "Unhandled coroutine exception", throwable)
        _uiState.update {
            it.copy(
                isLoading = false,
                isLoadingMore = false,
                error = throwable.message,
                toastMessage = throwable.message ?: "操作失败"
            )
        }
    }

    private var searchJob: Job? = null
    private var loadMoreJob: Job? = null
    private var discoveryJob: Job? = null
    private var discoveryOptionsJob: Job? = null
    private val discoveryExceptionHandler = CoroutineExceptionHandler { _, error ->
        android.util.Log.e("SearchViewModel", "加载发现漫画失败", error)
        _uiState.update {
            it.copy(
                isDiscoveryLoading = false, isDiscoveryLoadingMore = false,
                discoveryError = error.message ?: "加载失败"
            )
        }
    }

    init {
        val themePath = savedStateHandle.get<String>("themePathWord")
        if (!themePath.isNullOrBlank() && savedStateHandle.get<Boolean>("themeCleared") != true) {
            browseTheme(Theme(savedStateHandle["themeName"] ?: themePath, themePath))
        } else {
            loadDiscovery()
        }
    }

    fun clearToastMessage() {
        _uiState.update { it.copy(toastMessage = null) }
    }

    fun updateQuery(query: String) {
        _uiState.update { it.copy(query = query) }
    }

    fun clearSearch() {
        searchJob?.cancel()
        loadMoreJob?.cancel()
        discoveryJob?.cancel()
        discoveryOptionsJob?.cancel()
        savedStateHandle["themeCleared"] = true
        _uiState.value = SearchUiState()
        loadDiscovery()
    }

    fun selectDiscoveryTheme(option: ComicFilterOption?) {
        if (_uiState.value.selectedDiscoveryTheme == option) return
        _uiState.update { it.copy(selectedDiscoveryTheme = option) }
        loadDiscoveryComics()
    }

    fun selectDiscoveryTop(option: ComicFilterOption?) {
        if (_uiState.value.selectedDiscoveryTop == option) return
        _uiState.update { it.copy(selectedDiscoveryTop = option) }
        loadDiscoveryComics()
    }

    private fun loadDiscovery() {
        discoveryOptionsJob?.cancel()
        discoveryJob?.cancel()
        _uiState.update {
            it.copy(
                isDiscoveryLoading = true, isDiscoveryLoadingMore = false,
                discoveryError = null
            )
        }
        discoveryOptionsJob = viewModelScope.launch(discoveryExceptionHandler) {
            val result = comicDiscoveryRepository.getFilterOptions()
            currentCoroutineContext().ensureActive()
            result.fold(
                onSuccess = { options ->
                    _uiState.update {
                        it.copy(
                            discoveryOptions = options,
                            discoveryOptionsError = null
                        )
                    }
                },
                onFailure = { error ->
                    if (error is CancellationException) throw error
                    _uiState.update {
                        it.copy(
                            discoveryOptionsError = error.message ?: "筛选项暂不可用"
                        )
                    }
                }
            )
            if (_uiState.value.submittedQuery.isBlank() && _uiState.value.theme == null) loadDiscoveryComics()
        }
    }

    /** 筛选与分页共用同一列表；切换条件会取消旧请求并重置游标。 */
    private fun loadDiscoveryComics(append: Boolean = false) {
        val state = _uiState.value
        val offset = if (append) state.discoveryNextOffset ?: return else 0
        discoveryJob?.cancel()
        _uiState.update {
            it.copy(
                discoveryComics = if (append) it.discoveryComics else emptyList(),
                discoveryNextOffset = if (append) it.discoveryNextOffset else null,
                isDiscoveryLoading = !append, isDiscoveryLoadingMore = append, discoveryError = null
            )
        }
        discoveryJob = viewModelScope.launch(discoveryExceptionHandler) {
            val result = comicDiscoveryRepository.filterComics(
                state.selectedDiscoveryTheme?.pathWord,
                state.selectedDiscoveryTop?.pathWord, "-datetime_updated", offset
            )
            currentCoroutineContext().ensureActive()
            result.fold(
                onSuccess = { page ->
                    _uiState.update {
                        it.copy(
                            discoveryComics = (if (append) it.discoveryComics + page.comics else page.comics)
                                .distinctBy { comic -> comic.pathWord },
                            discoveryNextOffset = page.nextOffset?.takeIf { next -> next > offset },
                            isDiscoveryLoading = false,
                            isDiscoveryLoadingMore = false,
                            discoveryError = null
                        )
                    }
                },
                onFailure = { error ->
                    if (error is CancellationException) throw error
                    _uiState.update {
                        it.copy(
                            isDiscoveryLoading = false, isDiscoveryLoadingMore = false,
                            discoveryError = error.message ?: "加载失败"
                        )
                    }
                }
            )
        }
    }

    fun updateQueryType(queryType: QueryType?) {
        _uiState.update { it.copy(queryType = queryType) }
        if (_uiState.value.query.isNotBlank()) search(_uiState.value.query)
    }

    fun search(query: String) {
        searchJob?.cancel()
        loadMoreJob?.cancel()
        discoveryJob?.cancel()
        discoveryOptionsJob?.cancel()
        savedStateHandle["themeCleared"] = true
        _uiState.update {
            it.copy(
                query = query,
                submittedQuery = query.trim(),
                theme = null,
                nextOffset = null,
                searchResults = emptyList(),
                isLoading = query.isNotBlank(),
                isLoadingMore = false,
                error = null,
                toastMessage = null,
                currentPage = 1,
                hasMore = false,
                isDiscoveryLoading = false,
                isDiscoveryLoadingMore = false
            )
        }
        if (query.isBlank()) {
            if (_uiState.value.discoveryOptions == null) loadDiscovery() else loadDiscoveryComics()
            return
        }
        searchJob = viewModelScope.launch(exceptionHandler) { performSearch(isLoadMore = false) }
    }

    private fun browseTheme(theme: Theme) {
        searchJob?.cancel()
        loadMoreJob?.cancel()
        _uiState.value = SearchUiState(theme = theme, isLoading = true)
        searchJob = viewModelScope.launch(exceptionHandler) { performSearch(isLoadMore = false) }
    }

    fun refresh() {
        val state = _uiState.value
        val theme = state.theme
        if (theme != null) browseTheme(theme)
        else if (state.query.isBlank()) loadDiscovery()
        else search(state.query)
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.theme == null && state.submittedQuery.isBlank()) {
            if (!state.isDiscoveryLoading && !state.isDiscoveryLoadingMore && state.discoveryNextOffset != null)
                loadDiscoveryComics(append = true)
            return
        }
        if (state.isLoading || state.isLoadingMore || !state.hasMore) return
        if (state.theme == null && state.submittedQuery.isBlank()) return
        _uiState.update { it.copy(isLoadingMore = true) }
        loadMoreJob = viewModelScope.launch(exceptionHandler) { performSearch(isLoadMore = true) }
    }

    private suspend fun performSearch(isLoadMore: Boolean) {
        val state = _uiState.value
        val page = if (isLoadMore) state.currentPage + 1 else 1
        val themePath = state.theme?.pathWord
        if (!themePath.isNullOrBlank()) {
            val offset = if (isLoadMore) state.nextOffset ?: return else 0
            val result = mangaRepository.getThemeComics(themePath, offset)
            currentCoroutineContext().ensureActive()
            result.fold(
                onSuccess = { resultPage ->
                    publishResults(resultPage.comics, isLoadMore, page, resultPage.nextOffset)
                },
                onFailure = { publishError(it, isLoadMore) }
            )
        } else {
            val result = mangaRepository.searchComics(state.submittedQuery, page, state.queryType)
            currentCoroutineContext().ensureActive()
            result.fold(
                onSuccess = { comics ->
                    publishResults(
                        comics,
                        isLoadMore,
                        page,
                        if (comics.size >= 20) page * 20 else null
                    )
                },
                onFailure = { publishError(it, isLoadMore) }
            )
        }
    }

    private fun publishResults(
        comics: List<ComicSummary>,
        isLoadMore: Boolean,
        page: Int,
        nextOffset: Int?
    ) {
        _uiState.update {
            it.copy(
                searchResults = (if (isLoadMore) it.searchResults + comics else comics)
                    .distinctBy { comic -> comic.pathWord },
                isLoading = false,
                isLoadingMore = false,
                error = null,
                currentPage = page,
                hasMore = nextOffset != null,
                nextOffset = nextOffset
            )
        }
    }

    private fun publishError(error: Throwable, isLoadMore: Boolean) {
        _uiState.update {
            it.copy(
                isLoading = false,
                isLoadingMore = false,
                error = if (isLoadMore) it.error else error.message ?: "搜索失败",
                toastMessage = error.message ?: "搜索失败"
            )
        }
    }
}
