package com.rchdliang.copymanga_neo.data.export

import com.rchdliang.copymanga_neo.domain.model.ComicExportChapter
import com.rchdliang.copymanga_neo.domain.model.ComicExportComic
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest

/** 输出名称只用于展示，不把漫画标题或章节标题作为文件系统路径使用。 */
internal fun exportName(value: String): String = value
    .replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_")
    .trim().trim('.').take(64).ifBlank { "未命名" }

internal fun chapterExportName(chapter: ComicExportChapter): String =
    "${
        chapter.index.toString().padStart(5, '0')
    }_${exportName(chapter.name)}_${exportId(chapter.uuid)}"

internal fun comicExportName(comic: ComicExportComic): String =
    "${exportName(comic.name)}_${exportId(comic.pathWord)}"

private fun exportId(value: String) = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray(Charsets.UTF_8)).take(6).joinToString("") { "%02x".format(it) }

/** 按 UTF-8 字节限制名称，保持中文和补充平面字符完整，并保留末尾的唯一标识。 */
internal fun exportFileName(name: String, extension: String): String {
    val suffix = "_${exportId(name)}" + if (extension.isEmpty()) "" else ".$extension"
    val budget = 220 - suffix.toByteArray(Charsets.UTF_8).size
    val builder = StringBuilder()
    var used = 0
    for (code in name.codePoints().toArray()) {
        val part = String(Character.toChars(code))
        val size = part.toByteArray(Charsets.UTF_8).size
        if (used + size > budget) break
        builder.append(part)
        used += size
    }
    return builder.toString() + suffix
}

internal fun readImageHeader(input: InputStream): ByteArray {
    val header = ByteArray(64)
    var count = 0
    while (count < header.size) {
        val read = input.read(header, count, header.size - count)
        if (read < 0) break
        if (read == 0) continue
        count += read
    }
    return header.copyOf(count)
}

/** 按文件头判断格式，避免下载 URL 后缀与原图格式不一致。只读头部，不解码长图。 */
internal fun imageExtension(header: ByteArray): String {
    fun ascii(start: Int, count: Int) =
        if (header.size >= start + count) String(header, start, count, Charsets.US_ASCII) else ""

    fun starts(vararg bytes: Int) =
        header.size >= bytes.size && bytes.indices.all { (header[it].toInt() and 255) == bytes[it] }
    return when {
        starts(0xff, 0xd8, 0xff) -> "jpg"
        starts(0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a) -> "png"
        ascii(0, 6) in listOf("GIF87a", "GIF89a") -> "gif"
        ascii(0, 4) == "RIFF" && ascii(8, 4) == "WEBP" -> "webp"
        ascii(4, 4) == "ftyp" && (8 until header.size - 3 step 4).any {
            ascii(
                it,
                4
            ) in listOf("avif", "avis")
        } -> "avif"

        ascii(4, 4) == "ftyp" && (8 until header.size - 3 step 4).any {
            ascii(
                it,
                4
            ) in listOf("heic", "heix", "hevc", "hevx", "mif1")
        } -> "heic"

        else -> throw IOException("图片格式无效或不支持，请重新下载该章节")
    }
}
