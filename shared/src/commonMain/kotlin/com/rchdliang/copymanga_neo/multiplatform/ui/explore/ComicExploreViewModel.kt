package com.rchdliang.copymanga_neo.multiplatform.ui.explore

import com.rchdliang.copymanga_neo.multiplatform.ui.components.UiLog
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rchdliang.copymanga_neo.domain.model.ComicFilterOptions
import com.rchdliang.copymanga_neo.domain.model.ComicSummary
import com.rchdliang.copymanga_neo.domain.model.ComicTopic
import com.rchdliang.copymanga_neo.domain.repository.ComicDiscoveryRepository
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ExploreSection(val route: String, val title: String) {
    RECOMMENDED("recommended", "漫画推荐"),
    RANKS("ranks", "排行榜"),
    NEWEST("newest", "最新上架"),
    FINISHED("finished", "完结漫画"),
    TOPICS("topics", "漫画专题"),
    FILTER("filter", "发现");

    companion object {
        fun fromRoute(route: String?): ExploreSection =
            entries.firstOrNull { it.route == route } ?: RANKS
    }
}

data class ComicExploreUiState(
    val section: ExploreSection = ExploreSection.RANKS,
    val topic: ComicTopic? = null,
    val comics: List<ComicSummary> = emptyList(),
    val topics: List<ComicTopic> = emptyList(),
    val filters: ComicFilterOptions? = null,
    val rankPeriod: String = "day",
    val rankAudience: String = "male",
    val theme: String? = null,
    val top: String? = null,
    val ordering: String = "-datetime_updated",
    val nextOffset: Int? = null,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
    val filterError: String? = null
)

/** 漫画发现页面的分页、筛选及专题状态。 */
class ComicExploreViewModel constructor(
    private val repository: ComicDiscoveryRepository,
    private val savedStateHandle: SavedStateHandle,
    autoStart: Boolean = true
) : ViewModel() {
    private val initialTopic = savedStateHandle.get<String>("topic")?.takeIf { it.isNotBlank() }
    val openedFromTopic = initialTopic != null
    private val _uiState = MutableStateFlow(
        ComicExploreUiState(
            section = ExploreSection.fromRoute(savedStateHandle["section"]),
            filters = repository.cachedFilterOptions(),
            theme = savedStateHandle.get<String>("theme")?.takeIf { it.isNotBlank() },
            top = savedStateHandle.get<String>("top")?.takeIf { it.isNotBlank() },
            rankAudience = savedStateHandle.get<String>("audience")
                ?.takeIf { it in setOf("male", "female") } ?: "male",
            rankPeriod = savedStateHandle.get<String>("period")
                ?.takeIf { it in setOf("day", "week", "month", "total") } ?: "day"
        )
    )
    val uiState: StateFlow<ComicExploreUiState> = _uiState.asStateFlow()
    private var loadJob: Job? = null
    private var filterJob: Job? = null
    private var filterOptionsLoaded = false
    private val exceptionHandler = CoroutineExceptionHandler { _, error ->
        UiLog.e("ComicExplore", "加载漫画发现页失败", error)
        _uiState.update {
            it.copy(
                isLoading = false,
                isRefreshing = false,
                isLoadingMore = false,
                error = error.message ?: "加载失败"
            )
        }
    }

    private var started = false
    init { if (autoStart) start() }

    fun start() {
        if (started) return
        started = true
        if (initialTopic != null) {
            openTopic(
                ComicTopic(
                    initialTopic,
                    savedStateHandle.get<String>("topicTitle").orEmpty(),
                    null,
                    null
                )
            )
        } else refresh()
    }

    fun selectSection(section: ExploreSection) {
        if (_uiState.value.section == section && _uiState.value.topic == null) return
        _uiState.update { it.copy(section = section, topic = null) }
        refresh()
    }

    fun selectRankPeriod(period: String) {
        if (_uiState.value.rankPeriod == period || period !in setOf(
                "day",
                "week",
                "month",
                "total"
            )
        ) return
        _uiState.update { it.copy(rankPeriod = period) }
        refresh()
    }

    fun selectRankAudience(audience: String) {
        if (_uiState.value.rankAudience == audience || audience !in setOf("male", "female")) return
        _uiState.update { it.copy(rankAudience = audience) }
        refresh()
    }

    fun selectTheme(theme: String?) {
        if (_uiState.value.filters?.themes?.firstOrNull { it.pathWord == theme }?.enabled == false) return
        if (_uiState.value.theme == theme) return
        _uiState.update { it.copy(theme = theme) }
        refresh()
    }

    fun selectTop(top: String?) {
        if (_uiState.value.top == top) return
        _uiState.update { it.copy(top = top) }
        refresh()
    }

    fun selectOrdering(ordering: String) {
        if (_uiState.value.ordering == ordering) return
        _uiState.update { it.copy(ordering = ordering) }
        refresh()
    }

    fun openTopic(topic: ComicTopic) {
        _uiState.update { it.copy(topic = topic) }
        refresh()
        viewModelScope.launch(exceptionHandler) {
            repository.getTopic(topic.pathWord).fold(
                onSuccess = { detail ->
                    if (_uiState.value.topic?.pathWord == detail.pathWord) _uiState.update {
                        it.copy(
                            topic = detail
                        )
                    }
                },
                onFailure = { UiLog.w("ComicExplore", "专题简介暂不可用", it) }
            )
        }
    }

    fun closeTopic() {
        _uiState.update { it.copy(topic = null) }
        refresh()
    }

    fun refresh() {
        refreshContent(keepContent = false)
    }

    /** 下拉刷新保留当前内容，完成后再替换首屏数据。 */
    fun pullToRefresh() {
        if (_uiState.value.isLoading) return
        refreshContent(keepContent = true)
    }

    private fun refreshContent(keepContent: Boolean) {
        if (_uiState.value.section == ExploreSection.FILTER &&
            _uiState.value.topic == null && (!filterOptionsLoaded || keepContent)
        ) refreshFilterOptions()
        loadJob?.cancel()
        _uiState.update {
            it.copy(
                comics = if (keepContent) it.comics else emptyList(),
                topics = if (keepContent) it.topics else emptyList(),
                nextOffset = null,
                isLoading = true, isRefreshing = keepContent,
                isLoadingMore = false, error = null
            )
        }
        loadJob = viewModelScope.launch(exceptionHandler) {
            currentCoroutineContext().ensureActive()
            loadPage(0, append = false)
        }
    }

    private fun refreshFilterOptions() {
        if (filterJob?.isActive == true) return
        filterOptionsLoaded = true
        filterJob = viewModelScope.launch(exceptionHandler) {
            repository.getFilterOptions().fold(
                onSuccess = { options ->
                    val selected = _uiState.value.theme
                    val updated = selected?.takeIf { path ->
                        options.themes.any { it.pathWord == path && it.enabled }
                    }
                    _uiState.update {
                        it.copy(
                            filters = options,
                            theme = updated,
                            filterError = null
                        )
                    }
                    if (selected != null && updated == null &&
                        _uiState.value.section == ExploreSection.FILTER && _uiState.value.topic == null
                    ) refresh()
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(filterError = error.message ?: "标签数量暂不可用")
                    }
                }
            )
        }
    }

    fun loadMore() {
        val state = _uiState.value
        val offset = state.nextOffset ?: return
        if (state.isLoading || state.isLoadingMore) return
        _uiState.update { it.copy(isLoadingMore = true, error = null) }
        loadJob = viewModelScope.launch(exceptionHandler) { loadPage(offset, append = true) }
    }

    private suspend fun loadPage(offset: Int, append: Boolean) {
        val state = _uiState.value
        if (state.section == ExploreSection.TOPICS && state.topic == null) {
            repository.getTopics(offset).fold(
                onSuccess = { page ->
                    _uiState.update {
                        it.copy(
                            topics = (if (append) it.topics + page.topics else page.topics).distinctBy { topic -> topic.pathWord },
                            nextOffset = page.nextOffset?.takeIf { next -> next > offset },
                            isLoading = false,
                            isRefreshing = false,
                            isLoadingMore = false,
                            error = null
                        )
                    }
                },
                onFailure = { publishError(it, append) }
            )
            return
        }
        val result = when {
            state.topic != null -> repository.getTopicComics(state.topic.pathWord, offset)
            state.section == ExploreSection.RANKS -> repository.getRankings(
                state.rankPeriod,
                state.rankAudience,
                offset
            )

            state.section == ExploreSection.NEWEST -> repository.getNewest(offset)
            state.section == ExploreSection.RECOMMENDED -> repository.getRecommendations(offset)
            state.section == ExploreSection.FINISHED -> repository.getFinished(offset)
            else -> repository.filterComics(state.theme, state.top, state.ordering, offset)
        }
        currentCoroutineContext().ensureActive()
        result.fold(
            onSuccess = { page ->
                _uiState.update {
                    it.copy(
                        comics = (if (append) it.comics + page.comics else page.comics).distinctBy { comic -> comic.pathWord },
                        nextOffset = page.nextOffset?.takeIf { next -> next > offset },
                        isLoading = false,
                        isRefreshing = false,
                        isLoadingMore = false,
                        error = null
                    )
                }
            },
            onFailure = { publishError(it, append) }
        )
    }

    private fun publishError(error: Throwable, append: Boolean) {
        _uiState.update {
            it.copy(
                isLoading = false, isRefreshing = false, isLoadingMore = false,
                error = error.message ?: if (append) "加载更多失败" else "加载失败"
            )
        }
    }
}
