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
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds

data class FilteredHomeComics(
    val comics: HomeComicsData,
    val unresolvedCount: Int = 0,
    val isResolving: Boolean = false,
    val showUnresolvedWarning: Boolean = false,
    val enrichmentComplete: Boolean = true
)

/** 仅用于首页：优先复用持久标签，未知标签超时后放行，确认命中黑名单才隐藏。 */
@Singleton
class FilterHomeComics @Inject constructor(private val mangaRepository: MangaRepository) {
    fun verifiedPreview(data: HomeComicsData, blocked: List<Theme>): HomeComicsData =
        resolve(data, blocked, emptyMap(), allowUnknown = false).comics

    /** 页面持续观察缓存；后台补全逐项进行，刷新/风控时由调用方取消。 */
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
            val candidates = data.allComics().distinctBy { it.pathWord }
                .filter { comic ->
                    val entry = cache[comic.pathWord]
                    comic.pathWord.isNotBlank() &&
                            (entry?.refreshAfterMillis?.let { it <= now }
                                ?: comic.theme.isNullOrEmpty())
                }
                .sortedBy { it.pathWord in cache }
            var failures = 0
            for (comic in candidates) {
                val result = mangaRepository.refreshComicThemes(comic.pathWord)
                val error = result.exceptionOrNull()
                if (error is CancellationException) throw error
                if (error is RiskControlException) throw error
                failures = if (result.isFailure) failures + 1 else 0
                // 普通断网连续失败也不遍历整个首页反复请求，旧标签与漫画继续显示。
                if (failures >= 3) break
            }
            enrichmentComplete.value = true
        }
    }

    /** 一次性调用同样串行处理并保留未知条目；210 必须向上传递，不能当成缺失标签。 */
    suspend fun filter(data: HomeComicsData, blocked: List<Theme>): FilteredHomeComics {
        if (blocked.isEmpty()) return FilteredHomeComics(data)
        val themes = mutableMapOf<String, List<Theme>>()
        for (comic in data.allComics().distinctBy { it.pathWord }) {
            if (!comic.theme.isNullOrEmpty()) continue
            mangaRepository.getComicThemes(comic.pathWord).fold(
                onSuccess = { themes[comic.pathWord] = it },
                onFailure = {
                    if (it is CancellationException || it is RiskControlException) throw it
                }
            )
        }
        return resolve(data, blocked, themes, allowUnknown = true)
    }

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
            allowUnknown && missing > 0
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
