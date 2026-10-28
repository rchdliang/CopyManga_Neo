package com.rchdliang.copymanga_neo.data.local.portable

import com.rchdliang.copymanga_neo.domain.model.*
import com.rchdliang.copymanga_neo.domain.repository.*
import kotlinx.coroutines.CancellationException

/** 只接受完整在线目录；下载列表、失败后的部分分页和章节名称都不能用来判断追平。 */
internal suspend fun loadShelfReadingCatalog(
    key: ShelfReadingKey, manga: MangaRepository, novels: NovelRepository
): Result<List<ShelfChapter>> = try {
    if (key.kind == "novel") {
        novels.volumes(key.mode, key.owner, key.path).map { volumes ->
            volumes.sortedBy { it.index }.map { ShelfChapter(it.id, it.name) }
        }
    } else {
        val catalog = manga.getChapterCatalog(key.path, forceRefresh = true)
        val group = catalog.getOrNull()?.chaptersByGroup?.get(key.group)
        if (group != null) Result.success(group.map { ShelfChapter(it.uuid, it.name) })
        else if (catalog.exceptionOrNull() is RiskControlException) Result.failure(catalog.exceptionOrNull()!!)
        else {
            val chapters = mutableListOf<ChapterInfo>()
            var offset = 0
            var total: Int? = null
            do {
                val page = manga.getChapters(key.path, key.group, offset, 100).getOrThrow()
                require(total == null || total == page.total) { "目录在分页过程中发生变化" }
                total = page.total
                require(page.list.isNotEmpty() || total == 0) { "目录分页不完整" }
                chapters += page.list
                offset += page.list.size
            } while (offset < total)
            require(chapters.size == total && chapters.map { it.uuid }.distinct().size == total) { "目录分页不完整" }
            Result.success(chapters.sortedBy { it.index }.map { ShelfChapter(it.uuid, it.name) })
        }
    }
} catch (cancel: CancellationException) { throw cancel }
catch (error: Exception) { Result.failure(error) }
