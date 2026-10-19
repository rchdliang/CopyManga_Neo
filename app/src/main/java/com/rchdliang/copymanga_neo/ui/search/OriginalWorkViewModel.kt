package com.rchdliang.copymanga_neo.ui.search

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rchdliang.copymanga_neo.domain.repository.AppSettingsRepository
import com.rchdliang.copymanga_neo.domain.repository.ComicDiscoveryRepository
import com.rchdliang.copymanga_neo.domain.repository.NetworkSettingsRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/** 独立路由状态；不写入普通搜索条件或布局偏好。 */
@dagger.hilt.android.lifecycle.HiltViewModel
class OriginalWorkViewModel @javax.inject.Inject constructor(
    private val repository: ComicDiscoveryRepository,
    private val network: NetworkSettingsRepository,
    private val appSettings: AppSettingsRepository,
    private val savedState: SavedStateHandle
) : ViewModel() {
    val path = savedState.get<String>("originalPath").orEmpty()
    val title = savedState.get<String>("originalName")?.takeIf(String::isNotBlank) ?: path
    private val initialOrder = savedState.get<String>("originalOrdering")
        ?.takeIf { it in orders } ?: "-datetime_updated"
    private val mutable = MutableStateFlow(SearchUiState(
        compactLayout = savedState.get<Boolean>("originalCompactLayout") ?: false,
        ordering = initialOrder, isLoading = true))
    val uiState = mutable.asStateFlow()
    private val pages = mutableMapOf<String, SearchUiState>()
    private var request: Job? = null
    private var revision = 0
    private var started = false
    private val handler = CoroutineExceptionHandler { _, error ->
        mutable.update { it.copy(isLoading = false, isRefreshing = false, isLoadingMore = false,
            error = error.message ?: "加载失败") }
    }

    fun start() {
        if (started) return
        started = true
        viewModelScope.launch(handler) {
            appSettings.getSearchCompactLayout(false).fold(
                onSuccess = { compact ->
                    if (!savedState.contains("originalCompactLayout"))
                        mutable.update { it.copy(compactLayout = compact) }
                },
                onFailure = { if (it is CancellationException) throw it })
            network.settings.collect {
                pages.clear()
                load()
            }
        }
    }

    fun toggleLayout() {
        val compact = !mutable.value.compactLayout
        savedState["originalCompactLayout"] = compact
        mutable.update { it.copy(compactLayout = compact) }
    }

    fun selectOrdering(order: String) {
        if (order !in orders || order == mutable.value.ordering) return
        savedState["originalOrdering"] = order
        mutable.update { it.copy(ordering = order) }
        load()
    }

    fun refresh() = load(refresh = true)
    fun loadMore() {
        val state = mutable.value
        if (state.isLoading || state.isLoadingMore || state.nextOffset == null) return
        load(append = true)
    }

    private fun load(append: Boolean = false, refresh: Boolean = false) {
        request?.cancel()
        val version = ++revision
        val state = mutable.value
        val order = state.ordering
        if (path.isBlank()) {
            mutable.update { it.copy(isLoading = false, error = "原作缺少筛选标识") }
            return
        }
        if (!append && !refresh) pages[order]?.let { cached ->
            mutable.value = cached.copy(compactLayout = state.compactLayout)
            return
        }
        if (refresh) pages.remove(order)
        val offset = if (append) state.nextOffset ?: return else 0
        mutable.update {
            it.copy(searchResults = if (append || refresh) it.searchResults else emptyList(),
                total = if (append || refresh) it.total else null,
                isLoading = !append, isRefreshing = refresh, isLoadingMore = append,
                error = null, loadMoreError = null,
                nextOffset = if (append) it.nextOffset else null,
                hasMore = if (append) it.hasMore else false)
        }
        request = viewModelScope.launch(handler) {
            try {
            repository.getOriginalWorkComics(path, order, offset).fold(
                onSuccess = { page ->
                    ensureActive()
                    if (version != revision) return@fold
                    mutable.update {
                        it.copy(searchResults = (if (append) it.searchResults + page.comics else page.comics)
                            .distinctBy { comic -> comic.pathWord },
                            total = page.total, nextOffset = page.nextOffset, hasMore = page.nextOffset != null,
                            isLoading = false, isRefreshing = false, isLoadingMore = false,
                            error = null, loadMoreError = null)
                    }
                    pages[order] = mutable.value
                },
                onFailure = { error ->
                    if (error is CancellationException) throw error
                    ensureActive()
                    if (version != revision) return@fold
                    mutable.update { it.copy(isLoading = false, isRefreshing = false, isLoadingMore = false,
                        error = if (append) it.error else error.message ?: "加载失败",
                        loadMoreError = if (append) error.message ?: "加载更多失败" else null) }
                })
            } catch (cancel: CancellationException) {
                if (isActive && version == revision) mutable.update {
                    it.copy(isLoading = false, isRefreshing = false, isLoadingMore = false,
                        error = if (append) it.error else "请求已取消，请重试",
                        loadMoreError = if (append) "请求已取消，请重试" else null)
                }
                throw cancel
            }
        }
    }

    private companion object {
        val orders = setOf("-datetime_updated", "datetime_updated", "-popular", "popular")
    }
}
