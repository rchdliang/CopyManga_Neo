package com.rchdliang.copymanga_neo.data.export

import com.google.gson.Gson
import com.rchdliang.copymanga_neo.domain.model.ComicExportChapter
import com.rchdliang.copymanga_neo.domain.model.ComicExportComic
import com.rchdliang.copymanga_neo.domain.model.ComicExportFormat
import com.rchdliang.copymanga_neo.domain.model.ComicExportPage
import com.rchdliang.copymanga_neo.domain.model.ComicExportPlan
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

internal data class ExportUnit(
    val id: String,
    val comic: ComicExportComic,
    val chapters: List<ComicExportChapter>,
    val name: String
) {
    val bytes get() = chapters.sumOf { it.pages.sumOf { page -> page.size } }
}

internal fun exportUnits(plan: ComicExportPlan, format: ComicExportFormat): List<ExportUnit> =
    plan.comics.flatMap { comic ->
        if (format == ComicExportFormat.ZIP) listOf(
            ExportUnit(
                comic.pathWord, comic, comic.chapters,
                exportFileName("${comicExportName(comic)}_已下载${comic.chapters.size}章", "zip")
            )
        )
        else comic.chapters.map { chapter ->
            ExportUnit(
                "${comic.pathWord}/${chapter.uuid}", comic,
                listOf(chapter), exportFileName(
                    "${comicExportName(comic)}_${chapterExportName(chapter)}",
                    if (format == ComicExportFormat.CBZ) "cbz" else ""
                )
            )
        }
    }

/** 与 Android 保存位置无关的原图归档器，可用真实压缩包验证页序、字节和取消行为。 */
internal object ComicArchiveWriter {
    suspend fun write(
        unit: ExportUnit, format: ComicExportFormat, output: OutputStream,
        onBytes: suspend (Long) -> Unit, onChapter: (String) -> Unit
    ) {
        ZipOutputStream(output, Charsets.UTF_8).use { zip ->
            zip.setLevel(0)
            unit.chapters.forEach { chapter ->
                onChapter("${unit.comic.name}／${chapter.name}")
                chapter.pages.forEachIndexed { index, page ->
                    val prefix = if (format == ComicExportFormat.CBZ) "" else
                        "${groupName(unit.comic, chapter.group)}/${chapterExportName(chapter)}/"
                    zip.putNextEntry(ZipEntry(prefix + pageName(index, page)))
                    copyPage(page, zip, onBytes)
                    zip.closeEntry()
                }
            }
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(manifest(unit).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
        currentCoroutineContext().ensureActive()
    }

    internal fun manifest(unit: ExportUnit): String = Gson().toJson(
        mapOf(
            "version" to 1, "comic" to unit.comic.name, "path_word" to unit.comic.pathWord,
            "author" to unit.comic.author, "chapters" to unit.chapters.map { chapter ->
                mapOf(
                    "uuid" to chapter.uuid,
                    "name" to chapter.name,
                    "index" to chapter.index,
                    "group" to chapter.group,
                    "pages" to chapter.pages.mapIndexed { i, page ->
                        mapOf(
                            "name" to pageName(
                                i,
                                page
                            ), "size" to page.size
                        )
                    }
                )
            }
        ))

    internal fun groupName(comic: ComicExportComic, group: String): String {
        val groups = comic.chapters.map { it.group }.distinct().sorted()
        return "${(groups.indexOf(group) + 1).toString().padStart(3, '0')}_${exportName(group)}"
    }

    internal fun pageName(index: Int, page: ComicExportPage) =
        "${(index + 1).toString().padStart(5, '0')}.${page.extension}"

    internal suspend fun copyPage(
        page: ComicExportPage,
        output: OutputStream,
        onBytes: suspend (Long) -> Unit
    ) {
        val file = File(page.source)
        if (!file.isFile || file.length() != page.size || page.size <= 0) throw IOException("本地图片缺失或已变化，请重新选择章节")
        file.inputStream().buffered().use { input ->
            input.mark(64)
            if (imageExtension(readImageHeader(input)) != page.extension) throw IOException("图片格式已变化")
            input.reset()
            val buffer = ByteArray(64 * 1024)
            var copied = 0L
            while (true) {
                currentCoroutineContext().ensureActive()
                val count = input.read(buffer)
                if (count < 0) break
                output.write(buffer, 0, count)
                copied += count
                onBytes(count.toLong())
            }
            if (copied != page.size) throw IOException("图片读取不完整：$copied/${page.size}")
        }
    }

}
