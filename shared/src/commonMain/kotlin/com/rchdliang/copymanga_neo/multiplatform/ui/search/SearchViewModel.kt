package com.rchdliang.copymanga_neo.multiplatform.ui.search

import com.rchdliang.copymanga_neo.multiplatform.ui.components.UiLog
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rchdliang.copymanga_neo.domain.model.ComicFilterOption
import com.rchdliang.copymanga_neo.domain.model.ComicFilterOptions
import com.rchdliang.copymanga_neo.domain.model.ComicSummary
import com.rchdliang.copymanga_neo.domain.model.QueryType
import com.rchdliang.copymanga_neo.domain.repository.AppSettingsRepository
import com.rchdliang.copymanga_neo.domain.repository.ComicDiscoveryRepository
import com.rchdliang.copymanga_neo.domain.repository.MangaRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 有已提交的关键词时搜索；否则按题材、类型和排序发现漫画。 */
data class SearchUiState(
    val compactLayout: Boolean = false,
    val promotedDiscoveryThemePath: String? = null,
    val ordering: String = "-datetime_updated",
    val total: Int? = null,
    val query: String = "",
    val submittedQuery: String = "",
    val submittedQueryType: QueryType? = null,
    val queryType: QueryType? = null,
    val searchResults: List<ComicSummary> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
    val loadMoreError: String? = null,
    val currentPage: Int = 1,
    val hasMore: Boolean = false,
    val nextOffset: Int? = null,
    val discoveryOptions: ComicFilterOptions? = null,
    val selectedDiscoveryTheme: ComicFilterOption? = null,
    val selectedDiscoveryTop: ComicFilterOption? = null,
    val discoveryOptionsError: String? = null,
    val toastMessage: String? = null
) {
    val isKeywordSearch: Boolean get() = submittedQuery.isNotBlank()
}

class SearchViewModel constructor(
    private val mangaRepository: MangaRepository,
    private val comicDiscoveryRepository: ComicDiscoveryRepository,
    private val savedStateHandle: SavedStateHandle,
    private val appSettings: AppSettingsRepository,
    autoStart: Boolean = true
) : ViewModel() {
    private val cachedOptions = comicDiscoveryRepository.cachedFilterOptions()
    private val initialThemePath = savedStateHandle.get<String>("selectedThemePath")
        ?: savedStateHandle.get<String>("themePathWord")?.takeUnless {
            savedStateHandle.get<Boolean>("themeCleared") == true
        }
    private val initialTheme = initialThemePath?.takeIf { it.isNotBlank() }?.let { path ->
        cachedOptions.themes.firstOrNull { it.pathWord == path }
            ?: ComicFilterOption(savedStateHandle.get<String>("themeName") ?: path, path)
    }?.takeIf { it.enabled }
    private val initialQueryType = QueryType.entries.firstOrNull {
        it.value == savedStateHandle.get<String>("queryType")
    }
    private val initialSubmittedQuery = savedStateHandle.get<String>("submittedQuery")
        ?: savedStateHandle.get<String>("query").orEmpty()
    private val _uiState = MutableStateFlow(
        SearchUiState(
            compactLayout = savedStateHandle.get<Boolean>(
                if (initialSubmittedQuery.isBlank())
                    "discoveryCompactLayout" else "searchCompactLayout"
            ) ?: savedStateHandle.get<Boolean>("compactLayout") ?: false,
            promotedDiscoveryThemePath = savedStateHandle.get<String>("themePathWord"),
            ordering = savedStateHandle.get<String>("ordering")
                ?.takeIf {
                    it in setOf(
                        "-datetime_updated",
                        "datetime_updated",
                        "-popular",
                        "popular"
                    )
                }
                ?: "-datetime_updated",
            query = savedStateHandle.get<String>("query").orEmpty(),
            submittedQuery = initialSubmittedQuery,
            submittedQueryType = initialQueryType,
            queryType = initialQueryType,
            selectedDiscoveryTheme = initialTheme,
            selectedDiscoveryTop = cachedOptions.tops.firstOrNull {
                it.pathWord == savedStateHandle.get<String>("selectedTopPath")
            },
            discoveryOptions = cachedOptions
        )
    )
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    /** 仅缓存成功结果和分页信息，刷新始终重新请求；搜索与发现各保留最近八组条件。 */
    private data class ContentKey(
        val query: String,
        val queryType: QueryType?,
        val theme: String?,
        val top: String?,
        val ordering: String?
    )

    private data class CachedContent(
        val comics: List<ComicSummary>,
        val total: Int?,
        val page: Int,
        val nextOffset: Int?
    )

    private val searchCache = LinkedHashMap<ContentKey, CachedContent>()
    private val discoveryCache = LinkedHashMap<ContentKey, CachedContent>()

    private fun contentKey(state: SearchUiState): ContentKey =
        if (state.isKeywordSearch) ContentKey(
            state.submittedQuery, state.submittedQueryType, null, null, null
        ) else ContentKey(
            "", null, state.selectedDiscoveryTheme?.pathWord,
            state.selectedDiscoveryTop?.pathWord, state.ordering
        )

    private fun layoutKey(state: SearchUiState): String =
        if (state.isKeywordSearch) "searchCompactLayout" else "discoveryCompactLayout"

    private val layoutWrites = Channel<Pair<Boolean, Boolean>>(Channel.UNLIMITED)
    private var contentJob: Job? = null
    private var optionsJob: Job? = null
    private val exceptionHandler = CoroutineExceptionHandler { _, error ->
        UiLog.e("SearchViewModel", "搜索加载失败", error)
        _uiState.update {
            it.copy(
                isLoading = false, isRefreshing = false, isLoadingMore = false,
                error = error.message ?: "加载失败"
            )
        }
    }

    private var started = false
    fun start() {
        if (started) return
        started = true
        refreshOptions()
        loadContent()
    }

    init {
        // 路由关键词仅首次自动提交；后续重建仍区分输入草稿和已提交的查询。
        savedStateHandle["submittedQuery"] = initialSubmittedQuery
        viewModelScope.launch(exceptionHandler) {
            for (keywordSearch in listOf(false, true)) {
                appSettings.getSearchCompactLayout(keywordSearch).fold(
                    onSuccess = { compact ->
                        val key =
                            if (keywordSearch) "searchCompactLayout" else "discoveryCompactLayout"
                        if (!savedStateHandle.contains(key)) savedStateHandle[key] = compact
                    },
                    onFailure = { error ->
                        if (error is CancellationException) throw error
                        UiLog.w("SearchViewModel", "读取布局偏好失败", error)
                    }
                )
            }
            _uiState.update {
                it.copy(compactLayout = savedStateHandle.get<Boolean>(layoutKey(it)) ?: false)
            }
            for ((keywordSearch, compact) in layoutWrites) {
                appSettings.setSearchCompactLayout(keywordSearch, compact).fold(
                    onSuccess = {},
                    onFailure = { error ->
                        if (error is CancellationException) throw error
                        _uiState.update { it.copy(toastMessage = "布局设置保存失败") }
                    }
                )
            }
        }
        if (autoStart) start()
    }

    fun clearToastMessage() {
        _uiState.update { it.copy(toastMessage = null) }
    }

    fun updateQuery(query: String) {
        if (query.isBlank() && _uiState.value.isKeywordSearch) {
            clearSearch()
            return
        }
        savedStateHandle["query"] = query
        _uiState.update { it.copy(query = query) }
    }


    fun toggleLayout() {
        val compact = !_uiState.value.compactLayout
        savedStateHandle[layoutKey(_uiState.value)] = compact
        _uiState.update { it.copy(compactLayout = compact) }
        layoutWrites.trySend(_uiState.value.isKeywordSearch to compact)
    }

    fun selectOrdering(ordering: String) {
        if (ordering !in setOf("-datetime_updated", "datetime_updated", "-popular", "popular") ||
            _uiState.value.ordering == ordering
        ) return
        savedStateHandle["ordering"] = ordering
        _uiState.update { it.copy(ordering = ordering) }
        if (!_uiState.value.isKeywordSearch) loadContent()
    }

    fun search(query: String) {
        if (query.isBlank()) {
            clearSearch()
            return
        }
        savedStateHandle["query"] = query
        savedStateHandle["submittedQuery"] = query.trim()
        _uiState.update {
            it.copy(
                query = query, submittedQuery = query.trim(), submittedQueryType = it.queryType
            )
        }
        loadContent()
    }

    fun clearSearch() {
        savedStateHandle["query"] = ""
        savedStateHandle["submittedQuery"] = ""
        _uiState.update { it.copy(query = "", submittedQuery = "") }
        loadContent()
    }

    fun updateQueryType(type: QueryType?) {
        if (_uiState.value.queryType == type) return
        savedStateHandle["queryType"] = type?.value
        _uiState.update { it.copy(queryType = type, submittedQueryType = type) }
        if (_uiState.value.isKeywordSearch) loadContent()
    }

    fun selectDiscoveryTheme(option: ComicFilterOption?) {
        if (option?.enabled == false) return
        if (_uiState.value.selectedDiscoveryTheme?.pathWord == option?.pathWord &&
            !_uiState.value.isKeywordSearch
        ) return
        savedStateHandle["themeCleared"] = true
        savedStateHandle["selectedThemePath"] = option?.pathWord
        selectFilter { it.copy(selectedDiscoveryTheme = option) }
    }

    fun selectDiscoveryTop(option: ComicFilterOption?) {
        if (_uiState.value.selectedDiscoveryTop?.pathWord == option?.pathWord &&
            !_uiState.value.isKeywordSearch
        ) return
        savedStateHandle["selectedTopPath"] = option?.pathWord
        selectFilter { it.copy(selectedDiscoveryTop = option) }
    }

    private fun selectFilter(update: (SearchUiState) -> SearchUiState) {
        savedStateHandle["query"] = ""
        savedStateHandle["submittedQuery"] = ""
        _uiState.update { update(it).copy(query = "", submittedQuery = "") }
        loadContent()
    }

    fun refresh() {
        refreshOptions()
        loadContent(keepContent = true)
    }

    private fun refreshOptions() {
        if (optionsJob?.isActive == true) return
        optionsJob = viewModelScope.launch(exceptionHandler) {
            comicDiscoveryRepository.getFilterOptions().fold(
                onSuccess = { options ->
                    ensureActive()
                    val selected = _uiState.value.selectedDiscoveryTheme
                    val updated = options.themes.firstOrNull {
                        it.pathWord == selected?.pathWord && it.enabled
                    }
                    _uiState.update {
                        it.copy(
                            discoveryOptions = options, selectedDiscoveryTheme = updated,
                            discoveryOptionsError = null
                        )
                    }
                    if (selected != null && updated == null) {
                        savedStateHandle["selectedThemePath"] = null
                        savedStateHandle["themeCleared"] = true
                        if (!_uiState.value.isKeywordSearch) loadContent()
                    }
                },
                onFailure = { error ->
                    if (error is CancellationException) throw error
                    _uiState.update {
                        it.copy(
                            discoveryOptionsError = error.message ?: "标签更新失败"
                        )
                    }
                }
            )
        }
    }

    private fun loadContent(keepContent: Boolean = false) {
        contentJob?.cancel()
        val state = _uiState.value
        val key = contentKey(state)
        val cache = if (state.isKeywordSearch) searchCache else discoveryCache
        val compact = savedStateHandle.get<Boolean>(layoutKey(state))
            ?: savedStateHandle.get<Boolean>("compactLayout") ?: false
        if (!keepContent) {
            cache.remove(key)?.let { cached ->
                cache[key] = cached
                _uiState.update {
                    it.copy(
                        compactLayout = compact, searchResults = cached.comics,
                        total = cached.total, currentPage = cached.page,
                        nextOffset = cached.nextOffset, hasMore = cached.nextOffset != null,
                        isLoading = false, isRefreshing = false, isLoadingMore = false,
                        error = null, loadMoreError = null
                    )
                }
                return
            }
        }
        _uiState.update {
            it.copy(
                compactLayout = compact,
                searchResults = if (keepContent) it.searchResults else emptyList(),
                total = if (keepContent) it.total else null,
                isLoading = true, isRefreshing = keepContent && it.searchResults.isNotEmpty(),
                isLoadingMore = false, error = null, loadMoreError = null,
                hasMore = false, nextOffset = null, currentPage = 1
            )
        }
        contentJob = viewModelScope.launch(exceptionHandler) { loadPage(append = false) }
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.isLoading || state.isLoadingMore || !state.hasMore || state.nextOffset == null) return
        _uiState.update { it.copy(isLoadingMore = true, loadMoreError = null) }
        contentJob = viewModelScope.launch(exceptionHandler) { loadPage(append = true) }
    }

    private suspend fun loadPage(append: Boolean) {
        val state = _uiState.value
        val page = if (append) state.currentPage + 1 else 1
        if (state.isKeywordSearch) {
            mangaRepository.searchComicPage(
                state.submittedQuery, page, state.submittedQueryType,
                offset = if (append) state.nextOffset else 0
            ).fold(
                onSuccess = { result ->
                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
                    publish(result.comics, append, page, result.nextOffset, result.total)
                },
                onFailure = { publishError(it, append) }
            )
        } else {
            comicDiscoveryRepository.filterComics(
                state.selectedDiscoveryTheme?.pathWord,
                state.selectedDiscoveryTop?.pathWord,
                state.ordering, if (append) state.nextOffset ?: return else 0
            ).fold(
                onSuccess = { result ->
                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
                    publish(result.comics, append, page, result.nextOffset, result.total)
                },
                onFailure = { publishError(it, append) }
            )
        }
    }

    private fun publish(
        comics: List<ComicSummary>,
        append: Boolean,
        page: Int,
        next: Int?,
        total: Int?
    ) {
        _uiState.update {
            it.copy(
                searchResults = (if (append) it.searchResults + comics else comics)
                    .distinctBy { comic -> comic.pathWord },
                isLoading = false, isRefreshing = false, isLoadingMore = false,
                error = null, loadMoreError = null, currentPage = page,
                nextOffset = next, hasMore = next != null, total = total
            )
        }
        val state = _uiState.value
        val cache = if (state.isKeywordSearch) searchCache else discoveryCache
        val key = contentKey(state)
        cache.remove(key)
        cache[key] = CachedContent(
            state.searchResults, state.total, state.currentPage, state.nextOffset
        )
        if (cache.size > 8) cache.remove(cache.keys.first())
    }

    private suspend fun publishError(error: Throwable, append: Boolean) {
        if (error is CancellationException) throw error
        kotlinx.coroutines.currentCoroutineContext().ensureActive()
        _uiState.update {
            it.copy(
                isLoading = false, isRefreshing = false, isLoadingMore = false,
                error = if (append) it.error else error.message ?: "加载失败",
                loadMoreError = if (append) error.message ?: "加载更多失败" else null
            )
        }
    }
}
