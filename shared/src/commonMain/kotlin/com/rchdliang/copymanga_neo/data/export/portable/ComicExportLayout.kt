package com.rchdliang.copymanga_neo.data.export.portable

import com.rchdliang.copymanga_neo.domain.model.*
import com.rchdliang.copymanga_neo.domain.platform.PlatformIOException
import com.rchdliang.copymanga_neo.domain.reading.NovelTextDocument
import kotlinx.serialization.json.*

/** Names are display names only, never untrusted paths. */
internal fun exportName(value: String): String = value.map { character ->
    if (character in "\\/:*?\"<>|" || character.code < 32 || character.code == 127) '_' else character
}.joinToString("").trim().trim('.').take(64).let {
    if (it.lastOrNull()?.isHighSurrogate() == true) it.dropLast(1) else it
}.ifBlank { "未命名" }
internal fun exportId(value: String) = NovelTextDocument.sha256(value).take(12)
internal fun chapterExportName(chapter: ComicExportChapter) =
    "${chapter.index.toString().padStart(5, '0')}_${exportName(chapter.name)}_${exportId(chapter.uuid)}"
internal fun comicExportName(comic: ComicExportComic) = "${exportName(comic.name)}_${exportId(comic.pathWord)}"
internal fun exportFileName(name: String, extension: String): String {
    val suffix = "_${exportId(name)}" + if (extension.isEmpty()) "" else ".$extension"
    val budget = 220 - suffix.encodeToByteArray().size
    return buildString {
        var used = 0; var index = 0
        while (index < name.length) {
            val count = if (name[index].isHighSurrogate() && index + 1 < name.length && name[index + 1].isLowSurrogate()) 2 else 1
            val part = name.substring(index, index + count)
            val bytes = part.encodeToByteArray().size
            if (used + bytes > budget) break
            append(part); used += bytes; index += count
        }
        append(suffix)
    }
}

/** Original page bytes are exported without decoding or recompression. */
fun imageExtension(header: ByteArray): String {
    fun ascii(start: Int, count: Int): String =
        if (header.size >= start + count) buildString(count) {
            for (index in start until start + count) append((header[index].toInt() and 255).toChar())
        } else ""
    fun starts(vararg bytes: Int) = header.size >= bytes.size && bytes.indices.all { header[it].toInt() and 255 == bytes[it] }
    return when {
        starts(0xff, 0xd8, 0xff) -> "jpg"
        starts(0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a) -> "png"
        ascii(0, 6) in listOf("GIF87a", "GIF89a") -> "gif"
        ascii(0, 4) == "RIFF" && ascii(8, 4) == "WEBP" -> "webp"
        ascii(4, 4) == "ftyp" && (8 until header.size - 3 step 4).any { ascii(it, 4) in listOf("avif", "avis") } -> "avif"
        ascii(4, 4) == "ftyp" && (8 until header.size - 3 step 4).any { ascii(it, 4) in listOf("heic", "heix", "hevc", "hevx", "mif1") } -> "heic"
        else -> throw PlatformIOException("图片格式无效或不支持，请重新下载该章节")
    }
}

data class ExportUnit(val id: String, val comic: ComicExportComic, val chapters: List<ComicExportChapter>, val name: String) {
    val bytes get() = chapters.sumOf { chapter -> chapter.pages.sumOf { it.size } }
}
internal fun exportUnits(plan: ComicExportPlan, format: ComicExportFormat): List<ExportUnit> = plan.comics.flatMap { comic ->
    if (format == ComicExportFormat.ZIP) listOf(ExportUnit(comic.pathWord, comic, comic.chapters,
        exportFileName("${comicExportName(comic)}_已下载${comic.chapters.size}章", "zip")))
    else comic.chapters.map { chapter -> ExportUnit("${comic.pathWord}/${chapter.uuid}", comic, listOf(chapter),
        exportFileName("${comicExportName(comic)}_${chapterExportName(chapter)}", if (format == ComicExportFormat.CBZ) "cbz" else "")) }
}
internal fun exportGroupName(comic: ComicExportComic, group: String): String {
    val groups = comic.chapters.map { it.group }.distinct().sorted()
    return "${(groups.indexOf(group) + 1).toString().padStart(3, '0')}_${exportName(group)}"
}
internal fun exportPageName(index: Int, page: ComicExportPage) = "${(index + 1).toString().padStart(5, '0')}.${page.extension}"
internal fun exportManifest(unit: ExportUnit): String = buildJsonObject {
    put("version", 1); put("comic", unit.comic.name); put("path_word", unit.comic.pathWord); put("author", unit.comic.author)
    put("chapters", JsonArray(unit.chapters.map { chapter -> buildJsonObject {
        put("uuid", chapter.uuid); put("name", chapter.name); put("index", chapter.index); put("group", chapter.group)
        put("pages", JsonArray(chapter.pages.mapIndexed { index, page -> buildJsonObject {
            put("name", exportPageName(index, page)); put("size", page.size)
        } }))
    } }))
}.toString()

/** Platform-selected destinations and streams stay outside the shared state machine. */
interface ComicExportIo {
    suspend fun inspect(source: String): ComicExportPage
    suspend fun write(
        unit: ExportUnit, destination: String, directory: Boolean, format: ComicExportFormat,
        onCreated: suspend (String) -> Unit, onBytes: suspend (Long) -> Unit, onChapter: (String) -> Unit
    ): ComicExportOutput
    suspend fun delete(uri: String): Boolean
}
