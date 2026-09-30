package com.rchdliang.copymanga_neo.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rchdliang.copymanga_neo.domain.model.ComicSummary
import com.rchdliang.copymanga_neo.domain.model.ComicTopic
import com.rchdliang.copymanga_neo.domain.model.HomeComicsData
import com.rchdliang.copymanga_neo.domain.repository.ComicDiscoveryRepository
import com.rchdliang.copymanga_neo.domain.repository.MangaRepository
import com.rchdliang.copymanga_neo.domain.repository.RiskControlNotice
import com.rchdliang.copymanga_neo.domain.repository.RiskControlRepository
import com.rchdliang.copymanga_neo.domain.repository.ThemeBlacklistRepository
import com.rchdliang.copymanga_neo.domain.usecase.FilterHomeComics
import com.rchdliang.copymanga_neo.domain.usecase.FilteredHomeComics
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.max

enum class HomeShelf { RECOMMENDED, NEWEST, FINISHED, RANKS }

data class HomeShelfPaging(
    val nextOffset: Int? = null,
    val isLoading: Boolean = false,
    val requiredCount: Int = 0,
    val error: String? = null
)

/**
 * 首页 UI 状态
 */
data class HomeUiState(
    val recComicList: List<ComicSummary> = emptyList(),
    val hotComics: List<ComicSummary> = emptyList(),
    val newComics: List<ComicSummary> = emptyList(),
    val finishComics: List<ComicSummary> = emptyList(),
    val rankComics: List<ComicSummary> = emptyList(),
    val rankPositions: Map<String, Int> = emptyMap(),
    val topics: List<ComicTopic> = emptyList(),
    val paging: Map<HomeShelf, HomeShelfPaging> = emptyMap(),
    val rankAudience: String = "male",
    val rankPeriod: String = "day",
    val filterReady: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val toastMessage: String? = null,
    val filterWarning: String? = null,
    val riskControl: RiskControlNotice? = null,
    val showRiskDialog: Boolean = false
)

/**
 * 首页 ViewModel
 */
@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel @Inject constructor(
    private val mangaRepository: MangaRepository,
    private val discoveryRepository: ComicDiscoveryRepository,
    themeBlacklistRepository: ThemeBlacklistRepository,
    private val filterHomeComics: FilterHomeComics,
    private val riskControl: RiskControlRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    private val dismissedRisk = MutableStateFlow<Long?>(null)

    private val blockedThemes = combine(
        themeBlacklistRepository.blockedThemes,
        themeBlacklistRepository.isEnabled
    ) { themes, enabled -> if (enabled) themes else emptyList() }

    // 弹窗确认、Toast 等无关状态变化不能重建标签请求队列或重新开始超时计时。
    private val source = _uiState.map { it.toComics() to it.isLoading }.distinctUntilChanged()
    private val filtered =
        combine(source, blockedThemes, riskControl.notice) { source, blocked, risk ->
            Triple(source, blocked, risk)
        }.flatMapLatest { (source, blocked, risk) ->
            if (risk != null) flowOf(source.first to FilteredHomeComics(HomeComicsData()))
            else filterHomeComics.observe(source.first, blocked, refresh = !source.second)
                .map { source.first to it }
                .catch { error ->
                    if (error is CancellationException) throw error
                    android.util.Log.w("HomeViewModel", "标签后台补全已停止", error)
                    emit(
                        source.first to FilteredHomeComics(
                            filterHomeComics.verifiedPreview(source.first, blocked),
                            showUnresolvedWarning = true
                        )
                    )
                }
        }
    val uiState: StateFlow<HomeUiState> = combine(
        _uiState,
        filtered,
        riskControl.notice,
        dismissedRisk
    ) { state, filteredResult, risk, dismissed ->
        val (filteredSource, result) = filteredResult
        state.withComics(if (risk == null) result.comics else HomeComicsData()).copy(
            isLoading = risk == null && (state.isLoading || result.isResolving),
            filterReady = risk == null && state.toComics() == filteredSource &&
                    !result.isResolving && result.enrichmentComplete,
            rankComics = if (risk != null || state.rankComics != filteredSource.rankComics) emptyList()
            else result.comics.rankComics,
            rankPositions = state.rankComics.mapIndexed { index, comic -> comic.pathWord to index + 1 }
                .toMap(),
            filterWarning = if (risk == null && result.showUnresolvedWarning)
                "漫画标签未加载完成，某些漫画可能未按黑名单设置屏蔽" else null,
            riskControl = risk,
            showRiskDialog = risk != null && risk.detectedAt != dismissed
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState(isLoading = true))

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

    fun dismissRiskDialog() {
        dismissedRisk.value = riskControl.notice.value?.detectedAt
    }

    fun retryAfterRiskControl() {
        // 先停止旧列表的补全；闸门解除后统一由 notice 观察者加载一次首页。
        _uiState.value = HomeUiState(isLoading = true)
        riskControl.resumeByUser()
    }

    private var loadJob: Job? = null
    private val pageJobs = mutableMapOf<HomeShelf, Job>()
    private val desiredCounts = mutableMapOf<HomeShelf, Int>()

    init {
        viewModelScope.launch {
            var wasBlocked = false
            riskControl.notice.collect { notice ->
                if (notice != null) pageJobs.values.forEach { it.cancel() }
                if (wasBlocked && notice == null) loadComics()
                wasBlocked = notice != null
            }
        }
        loadComics()
    }

    fun loadComics() {
        if (riskControl.notice.value != null) return
        loadJob?.cancel()
        pageJobs.values.forEach { it.cancel() }
        pageJobs.clear()
        loadJob = viewModelScope.launch(exceptionHandler) {
            _uiState.update { it.copy(isLoading = true, error = null, paging = emptyMap()) }
            mangaRepository.getHomeComics().fold(
                onSuccess = { homeComicsData ->
                    _uiState.update {
                        val defaultRank = it.rankAudience == "male" && it.rankPeriod == "day"
                        it.copy(
                            recComicList = homeComicsData.recComics,
                            hotComics = homeComicsData.hotComics,
                            newComics = homeComicsData.newComics,
                            finishComics = homeComicsData.finishComics,
                            rankComics = if (defaultRank) homeComicsData.rankComics else emptyList(),
                            topics = homeComicsData.topics,
                            paging = mapOf(
                                HomeShelf.RECOMMENDED to HomeShelfPaging(homeComicsData.recNextOffset),
                                HomeShelf.NEWEST to HomeShelfPaging(homeComicsData.newNextOffset),
                                HomeShelf.FINISHED to HomeShelfPaging(homeComicsData.finishNextOffset),
                                HomeShelf.RANKS to HomeShelfPaging(if (defaultRank) homeComicsData.rankNextOffset else 0)
                            ),
                            isLoading = false
                        )
                    }
                    if (_uiState.value.rankAudience != "male" || _uiState.value.rankPeriod != "day") {
                        loadMore(HomeShelf.RANKS)
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

    /** 首页横向列表按可见卡片数取数；每个分区维护独立的服务端 offset。 */
    fun onShelfViewportChanged(shelf: HomeShelf, visibleCards: Int) {
        desiredCounts[shelf] = max(visibleCards + 1, desiredCounts[shelf] ?: 0)
    }

    fun loadMore(shelf: HomeShelf, automatic: Boolean = false) {
        if (riskControl.notice.value != null) return
        val state = _uiState.value
        val paging = state.paging[shelf] ?: return
        val offset = paging.nextOffset ?: return
        if (state.isLoading || paging.isLoading || automatic && (paging.error != null || !uiState.value.filterReady)) return
        val visibleCount = if (offset == 0 && state.comicsFor(shelf).isEmpty()) 0
        else uiState.value.comicsFor(shelf).size
        val viewportTarget = desiredCounts[shelf] ?: 4
        val targetCount = if (automatic) max(viewportTarget, paging.requiredCount)
        else max(max(viewportTarget, paging.requiredCount), visibleCount + viewportTarget - 1)
        if (automatic && visibleCount >= targetCount) return
        val limit = max(6, targetCount - visibleCount).coerceAtMost(21)
        val rankAudience = state.rankAudience
        val rankPeriod = state.rankPeriod
        _uiState.update {
            it.copy(
                paging = it.paging + (shelf to paging.copy(
                    isLoading = true,
                    requiredCount = targetCount,
                    error = null
                ))
            )
        }
        pageJobs[shelf] = viewModelScope.launch(exceptionHandler) {
            val result = when (shelf) {
                HomeShelf.RECOMMENDED -> discoveryRepository.getRecommendations(offset, limit)
                HomeShelf.NEWEST -> discoveryRepository.getNewest(offset, limit)
                HomeShelf.FINISHED -> discoveryRepository.getFinished(offset, limit)
                HomeShelf.RANKS -> discoveryRepository.getRankings(
                    rankPeriod,
                    rankAudience,
                    offset,
                    limit
                )
            }
            if (shelf == HomeShelf.RANKS &&
                (_uiState.value.rankAudience != rankAudience || _uiState.value.rankPeriod != rankPeriod)
            ) return@launch
            result.fold(
                onSuccess = { page ->
                    _uiState.update { current ->
                        val nextPaging = current.paging[shelf]?.copy(
                            nextOffset = page.nextOffset?.takeIf { it > offset },
                            isLoading = false,
                            error = null
                        ) ?: paging
                        current.withShelf(
                            shelf,
                            (current.comicsFor(shelf) + page.comics).distinctBy { it.pathWord }
                        ).copy(paging = current.paging + (shelf to nextPaging))
                    }
                },
                onFailure = { error ->
                    if (error is CancellationException) throw error
                    _uiState.update { current ->
                        val nextPaging = current.paging[shelf]?.copy(
                            isLoading = false,
                            error = error.message ?: "加载更多失败"
                        ) ?: paging
                        current.copy(paging = current.paging + (shelf to nextPaging))
                    }
                }
            )
        }
    }

    fun selectRankAudience(audience: String) {
        if (audience !in setOf("male", "female") || _uiState.value.rankAudience == audience) return
        selectRank(audience, if (audience == "female") "total" else "day")
    }

    fun selectRankPeriod(period: String) {
        val state = _uiState.value
        if (period !in setOf("day", "week", "month", "total") ||
            state.rankAudience == "female" && period != "total" || state.rankPeriod == period
        ) return
        selectRank(state.rankAudience, period)
    }

    private fun selectRank(audience: String, period: String) {
        pageJobs[HomeShelf.RANKS]?.cancel()
        _uiState.update {
            it.copy(
                rankAudience = audience, rankPeriod = period,
                rankComics = emptyList(),
                paging = it.paging + (HomeShelf.RANKS to HomeShelfPaging(nextOffset = 0))
            )
        }
        loadMore(HomeShelf.RANKS)
    }

}

private fun HomeUiState.comicsFor(shelf: HomeShelf): List<ComicSummary> = when (shelf) {
    HomeShelf.RECOMMENDED -> recComicList
    HomeShelf.NEWEST -> newComics
    HomeShelf.FINISHED -> finishComics
    HomeShelf.RANKS -> rankComics
}

private fun HomeUiState.withShelf(shelf: HomeShelf, comics: List<ComicSummary>): HomeUiState =
    when (shelf) {
        HomeShelf.RECOMMENDED -> copy(recComicList = comics)
        HomeShelf.NEWEST -> copy(newComics = comics)
        HomeShelf.FINISHED -> copy(finishComics = comics)
        HomeShelf.RANKS -> copy(rankComics = comics)
    }

private fun HomeUiState.withComics(data: HomeComicsData): HomeUiState = copy(
    recComicList = data.recComics,
    hotComics = data.hotComics,
    newComics = data.newComics,
    finishComics = data.finishComics,
    rankComics = data.rankComics,
    topics = data.topics
)

private fun HomeUiState.toComics() =
    HomeComicsData(recComicList, hotComics, newComics, finishComics, rankComics, topics)
