package com.rchdliang.copymanga_neo.multiplatform.ui.export

import com.rchdliang.copymanga_neo.domain.model.ComicExportChapter
import com.rchdliang.copymanga_neo.domain.model.ComicExportPlan
import kotlinx.serialization.Serializable

/** 章节身份包含漫画，跨漫画批量选择不会因相同 UUID 串项。 */
@Serializable
internal data class ExportChapterKey(val comic: String, val chapter: String)

internal data class ExportChapterGroup(
    val comic: String,
    val comicName: String,
    val name: String,
    val chapters: List<ComicExportChapter>
) {
    val id: String get() = "${comic.length}:$comic$name"
    val keys: Set<ExportChapterKey> = chapters.map { ExportChapterKey(comic, it.uuid) }.toSet()
}

internal fun ComicExportPlan.selectionGroups(): List<ExportChapterGroup> = comics.flatMap { comic ->
    comic.chapters.groupBy { it.group }.map { (group, chapters) ->
        ExportChapterGroup(comic.pathWord, comic.name, group, chapters.sortedWith(compareBy({ it.index }, { it.uuid })))
    }
}

internal fun ComicExportPlan.onlySelected(selected: Set<ExportChapterKey>): ComicExportPlan = copy(
    comics = comics.mapNotNull { comic ->
        val chapters = comic.chapters.filter { ExportChapterKey(comic.pathWord, it.uuid) in selected }
        if (chapters.isEmpty()) null else comic.copy(chapters = chapters)
    }
)

/** 一基序号、包含两端；替换本组范围而不修改其他组。 */
internal fun selectExportRange(
    selected: Set<ExportChapterKey>, group: ExportChapterGroup, first: Int, last: Int
): Set<ExportChapterKey> {
    require(first in 1..group.chapters.size && last in first..group.chapters.size)
    return selected - group.keys + group.chapters.subList(first - 1, last)
        .map { ExportChapterKey(group.comic, it.uuid) }
}
