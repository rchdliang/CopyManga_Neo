package com.rchdliang.copymanga_neo.data.local.portable

import com.rchdliang.copymanga_neo.domain.model.*
import com.rchdliang.copymanga_neo.domain.repository.*
import kotlinx.coroutines.CancellationException

/** 汉化组用于定位章节；作品最新章由服务端明确指定，不能按组数或章节名猜测。 */
internal suspend fun loadShelfReadingCatalog(
    key: ShelfReadingKey, manga: MangaRepository, novels: NovelRepository
): Result<List<ShelfChapter>> = try {
    if (key.kind == "novel") {
        novels.volumes(key.mode, key.owner, key.path).map { volumes ->
            val ordered = volumes.sortedBy { it.index }
            ordered.map { ShelfChapter(it.id, it.name, latest = it.id == ordered.lastOrNull()?.id) }
        }
    } else {
        val catalog = manga.getChapterCatalog(key.path, forceRefresh = true)
        (catalog.exceptionOrNull() as? RiskControlException)?.let { throw it }
        val detail = manga.getComicDetail(key.path).getOrThrow()
        val latest = requireNotNull(detail.comic.lastChapter) { "作品最新章节未知" }
        val groups = catalog.getOrNull()?.chaptersByGroup ?: buildMap {
            for (group in detail.groups.values) {
                val chapters = mutableListOf<ChapterInfo>()
                var offset = 0
                var total: Int? = null
                do {
                    val page = manga.getChapters(key.path, group.pathWord, offset, 100).getOrThrow()
                    require(total == null || total == page.total) { "目录在分页过程中发生变化" }
                    total = page.total
                    require(page.list.isNotEmpty() || total == 0) { "目录分页不完整" }
                    chapters += page.list
                    offset += page.list.size
                } while (offset < total)
                require(chapters.size == total && chapters.map { it.uuid }.distinct().size == total) { "目录分页不完整" }
                put(group.pathWord, chapters.sortedBy { it.index })
            }
        }
        val chapters = groups.flatMap { (group, chapters) -> chapters.map {
            ShelfChapter(it.uuid, if (it.uuid == latest.id) latest.name else it.name, group, it.uuid == latest.id)
        } }.distinctBy { it.id }
        require(chapters.any { it.latest }) { "目录尚未包含作品最新章节" }
        Result.success(chapters)
    }
} catch (cancel: CancellationException) { throw cancel }
catch (error: Exception) { Result.failure(error) }
