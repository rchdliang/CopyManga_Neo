package com.rchdliang.copymanga_neo.ui.explore

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rchdliang.copymanga_neo.domain.model.ComicFilterOptions
import com.rchdliang.copymanga_neo.domain.model.ComicSummary
import com.rchdliang.copymanga_neo.domain.model.ComicTopic
import com.rchdliang.copymanga_neo.domain.repository.ComicDiscoveryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
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
@HiltViewModel
class ComicExploreViewModel @Inject constructor(
    private val repository: ComicDiscoveryRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val initialTopic = savedStateHandle.get<String>("topic")?.takeIf { it.isNotBlank() }
    val openedFromTopic = initialTopic != null
    private val _uiState = MutableStateFlow(
        ComicExploreUiState(
            section = ExploreSection.fromRoute(savedStateHandle["section"]),
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
    private val exceptionHandler = CoroutineExceptionHandler { _, error ->
        android.util.Log.e("ComicExplore", "加载漫画发现页失败", error)
        _uiState.update {
            it.copy(
                isLoading = false,
                isRefreshing = false,
                isLoadingMore = false,
                error = error.message ?: "加载失败"
            )
        }
    }

    init {
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
                onFailure = { android.util.Log.w("ComicExplore", "专题简介暂不可用", it) }
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
            if (_uiState.value.section == ExploreSection.FILTER && _uiState.value.filters == null) {
                repository.getFilterOptions().fold(
                    onSuccess = { options ->
                        _uiState.update {
                            it.copy(
                                filters = options,
                                filterError = null
                            )
                        }
                    },
                    onFailure = { error ->
                        _uiState.update {
                            it.copy(
                                filterError = error.message ?: "筛选项暂不可用"
                            )
                        }
                    }
                )
            }
            currentCoroutineContext().ensureActive()
            loadPage(0, append = false)
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
