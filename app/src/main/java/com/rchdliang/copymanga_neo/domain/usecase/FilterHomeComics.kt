package com.rchdliang.copymanga_neo.domain.usecase

import com.rchdliang.copymanga_neo.domain.model.ComicSummary
import com.rchdliang.copymanga_neo.domain.model.HomeComicsData
import com.rchdliang.copymanga_neo.domain.model.Theme
import com.rchdliang.copymanga_neo.domain.model.blocks
import com.rchdliang.copymanga_neo.domain.repository.MangaRepository
import com.rchdliang.copymanga_neo.domain.repository.RiskControlException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds

enum class HomeFilterSection { RECOMMENDED, HOT, NEWEST, FINISHED, RANKS }

fun HomeComicsData.inSection(section: HomeFilterSection): List<ComicSummary> = when (section) {
    HomeFilterSection.RECOMMENDED -> recComics
    HomeFilterSection.HOT -> hotComics
    HomeFilterSection.NEWEST -> newComics
    HomeFilterSection.FINISHED -> finishComics
    HomeFilterSection.RANKS -> rankComics
}

data class FilteredHomeComics(
    val comics: HomeComicsData,
    val unresolvedCount: Int = 0,
    val isResolving: Boolean = false,
    val showUnresolvedWarning: Boolean = false,
    val enrichmentComplete: Boolean = true,
    val readySections: Set<HomeFilterSection> = HomeFilterSection.entries.toSet()
)

/** 仅用于首页：优先复用持久标签，未知标签超时后放行，确认命中黑名单才隐藏。 */
@Singleton
class FilterHomeComics @Inject constructor(private val mangaRepository: MangaRepository) {
    fun verifiedPreview(data: HomeComicsData, blocked: List<Theme>): HomeComicsData =
        resolve(data, blocked, emptyMap(), allowUnknown = false).comics

    /** 页面持续观察缓存；分区轮转取数并受控并发，刷新/风控时取消整轮补全。 */
    fun observe(
        data: HomeComicsData,
        blocked: List<Theme>,
        refresh: Boolean = true
    ): Flow<FilteredHomeComics> = channelFlow {
        if (blocked.isEmpty()) {
            send(FilteredHomeComics(data))
            return@channelFlow
        }
        val allowUnknown = MutableStateFlow(false)
        val enrichmentComplete = MutableStateFlow(!refresh)
        launch {
            delay(3.seconds)
            allowUnknown.value = true
        }
        launch {
            combine(
                mangaRepository.cachedComicThemes,
                allowUnknown,
                enrichmentComplete
            ) { cache, allow, complete ->
                resolve(data, blocked, cache.mapValues { it.value.themes }, allow)
                    .copy(enrichmentComplete = complete)
            }.collect { send(it) }
        }
        if (refresh) launch {
            val cache = mangaRepository.cachedComicThemes.first()
            val now = System.currentTimeMillis()
            val sections = HomeFilterSection.entries.map(data::inSection)
            val candidates = (0 until sections.maxOf { it.size }).flatMap { index ->
                sections.mapNotNull { it.getOrNull(index) }
            }.distinctBy { it.pathWord }
                .filter { comic ->
                    val entry = cache[comic.pathWord]
                    comic.pathWord.isNotBlank() &&
                            (entry?.refreshAfterMillis?.let { it <= now }
                                ?: comic.theme.isNullOrEmpty())
                }
                .sortedBy { it.pathWord in cache }
            kotlinx.coroutines.coroutineScope {
                val guard = kotlinx.coroutines.sync.Mutex()
                var next = 0
                var failures = 0
                var stopped = false
                lateinit var workers: List<kotlinx.coroutines.Job>
                workers = List(minOf(candidates.size, com.rchdliang.copymanga_neo.domain.model.ThemeLoadingPolicy.MAX_CONCURRENCY)) {
                    launch(start = kotlinx.coroutines.CoroutineStart.LAZY) {
                        while (true) {
                            val comic = guard.withLock {
                                if (stopped) null else candidates.getOrNull(next)?.also { next++ }
                            } ?: break
                            val result = mangaRepository.refreshComicThemes(comic.pathWord)
                            val error = result.exceptionOrNull()
                            if (error is CancellationException || error is RiskControlException) throw error
                            guard.withLock {
                                failures = if (result.isFailure) failures + 1 else 0
                                if (failures >= 3) {
                                    stopped = true
                                    // 取消已排队及在途的同轮补全，断网时不把整页继续请求完。
                                    val current = kotlinx.coroutines.currentCoroutineContext()[kotlinx.coroutines.Job]
                                    workers.filter { it !== current }.forEach { it.cancel() }
                                }
                            }
                        }
                    }
                }
                workers.forEach { it.start() }
                workers.joinAll()
            }
            enrichmentComplete.value = true
        }
    }

    /** 一次性调用复用相同的并发、缓存、失败停止和未知标签回退规则。 */
    suspend fun filter(data: HomeComicsData, blocked: List<Theme>): FilteredHomeComics =
        observe(data, blocked).first { it.enrichmentComplete && !it.isResolving }

    private fun resolve(
        data: HomeComicsData,
        blocked: List<Theme>,
        cached: Map<String, List<Theme>>,
        allowUnknown: Boolean
    ): FilteredHomeComics {
        if (blocked.isEmpty()) return FilteredHomeComics(data)
        val provided = data.allComics().filter { !it.theme.isNullOrEmpty() }
            .associate { it.pathWord to it.theme.orEmpty() }
        val known = provided + cached
        val missing = data.allComics().map { it.pathWord }.distinct().count { it !in known }
        val filtered = data.mapComics { comic ->
            val themes = known[comic.pathWord]
            when {
                themes == null -> comic.takeIf { allowUnknown }
                themes.any { blocked.blocks(it) } -> null
                else -> comic.copy(theme = themes)
            }
        }
        return FilteredHomeComics(
            filtered,
            missing,
            !allowUnknown && missing > 0,
            allowUnknown && missing > 0,
            readySections = HomeFilterSection.entries.filter { section ->
                allowUnknown || data.inSection(section).all { it.pathWord in known }
            }.toSet()
        )
    }
}

private fun HomeComicsData.allComics() =
    recComics + hotComics + newComics + finishComics + rankComics

private fun HomeComicsData.mapComics(transform: (ComicSummary) -> ComicSummary?): HomeComicsData =
    copy(
        recComics = recComics.mapNotNull(transform),
        hotComics = hotComics.mapNotNull(transform),
        newComics = newComics.mapNotNull(transform),
        finishComics = finishComics.mapNotNull(transform),
        rankComics = rankComics.mapNotNull(transform)
    )
