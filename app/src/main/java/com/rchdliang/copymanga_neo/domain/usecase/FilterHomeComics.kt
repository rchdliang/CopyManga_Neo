package com.rchdliang.copymanga_neo.domain.usecase

import com.rchdliang.copymanga_neo.domain.model.ComicSummary
import com.rchdliang.copymanga_neo.domain.model.HomeComicsData
import com.rchdliang.copymanga_neo.domain.model.Theme
import com.rchdliang.copymanga_neo.domain.model.blocks
import com.rchdliang.copymanga_neo.domain.repository.MangaRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import javax.inject.Inject
import javax.inject.Singleton

data class FilteredHomeComics(
    val comics: HomeComicsData,
    val unresolvedCount: Int = 0
)

/** 仅用于首页：列表空标签不等于漫画没有标签，需先从详情确认。 */
@Singleton
class FilterHomeComics @Inject constructor(
    private val mangaRepository: MangaRepository
) {
    private val requests = Semaphore(4)

    /** 补取标签期间，只展示已经确认不命中黑名单的条目。 */
    fun verifiedPreview(data: HomeComicsData, blocked: List<Theme>): HomeComicsData =
        if (blocked.isEmpty()) data else data.mapComics { comic ->
            comic.takeIf {
                !it.theme.isNullOrEmpty() && it.theme.none { theme -> blocked.blocks(theme) }
            }
        }

    suspend fun filter(data: HomeComicsData, blocked: List<Theme>): FilteredHomeComics =
        supervisorScope {
            if (blocked.isEmpty()) return@supervisorScope FilteredHomeComics(data)
            val allComics = data.recComics + data.hotComics + data.newComics + data.finishComics
            val known = allComics.filter { !it.theme.isNullOrEmpty() }
                .associate { it.pathWord to it.theme.orEmpty() }
            // 同一漫画可能出现在多个栏目，只补取一次。
            val missing = allComics.distinctBy { it.pathWord }.filter { it.pathWord !in known }
            val resolved = missing.map { comic ->
                async {
                    val themes = requests.withPermit {
                        try {
                            if (comic.pathWord.isBlank()) null
                            else mangaRepository.getComicThemes(comic.pathWord).fold(
                                onSuccess = { it },
                                onFailure = {
                                    if (it is CancellationException) throw it
                                    null
                                }
                            )
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            null
                        }
                    }
                    comic.pathWord to themes
                }
            }.awaitAll().toMap()
            val filtered = data.mapComics { comic ->
                val themes = known[comic.pathWord] ?: resolved[comic.pathWord]
                if (themes == null || themes.any { blocked.blocks(it) }) null
                else comic.copy(theme = themes)
            }
            FilteredHomeComics(filtered, resolved.count { it.value == null })
        }
}

private fun HomeComicsData.mapComics(transform: (ComicSummary) -> ComicSummary?): HomeComicsData =
    copy(
        recComics = recComics.mapNotNull(transform),
        hotComics = hotComics.mapNotNull(transform),
        newComics = newComics.mapNotNull(transform),
        finishComics = finishComics.mapNotNull(transform)
    )
