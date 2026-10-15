package com.rchdliang.copymanga_neo.data.export.portable

import com.rchdliang.copymanga_neo.domain.model.*
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.URI
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Real streaming ZIP/CBZ/image export. Source files are confined to the downloads directory. */
class FileComicExportIo(downloadDirectory: String) : ComicExportIo {
    private val root = File(downloadDirectory).canonicalFile
    private fun file(location: String) = if (location.startsWith("file:")) File(URI(location)) else File(location)
    private fun source(location: String): File = file(location).canonicalFile.also {
        require(it.toPath().startsWith(root.toPath()) && it != root) { "图片路径不属于下载目录" }
        if (!it.isFile || it.length() <= 0) throw IOException("本地图片缺失")
    }
    private fun header(input: InputStream): ByteArray {
        val buffer = ByteArray(64); var used = 0
        while (used < buffer.size) {
            val count = input.read(buffer, used, buffer.size - used)
            if (count < 0) break
            used += count
        }
        return buffer.copyOf(used)
    }
    override suspend fun inspect(source: String): ComicExportPage = withContext(Dispatchers.IO) {
        val target = source(source)
        ComicExportPage(target.toURI().toString(), target.length(), target.inputStream().use { imageExtension(header(it)) })
    }
    private fun unique(parent: File, name: String, isDirectory: Boolean): File {
        require(parent.isDirectory) { "请选择有效导出目录" }
        val extension = if (isDirectory) "" else name.substringAfterLast('.', "")
        val stem = if (extension.isEmpty()) name else name.removeSuffix(".$extension")
        var number = 1
        while (true) {
            val next = if (number == 1) name else "$stem ($number)" + if (extension.isEmpty()) "" else ".$extension"
            val target = File(parent, next)
            if (if (isDirectory) target.mkdir() else target.createNewFile()) return target
            if (!target.exists()) throw IOException("无法创建导出文件")
            number++
        }
    }
    override suspend fun write(
        unit: ExportUnit, destination: String, directory: Boolean, format: ComicExportFormat,
        onCreated: suspend (String) -> Unit, onBytes: suspend (Long) -> Unit, onChapter: (String) -> Unit
    ): ComicExportOutput = withContext(Dispatchers.IO) {
        currentCoroutineContext().ensureActive()
        val selected = file(destination).absoluteFile
        val reservation = if (!directory) DesktopExportDestinations.claim(selected.toPath()) else null
        val target = if (directory && format == ComicExportFormat.IMAGES) {
            fun childDirectory(parent: File, name: String): File {
                val child = File(parent, name).canonicalFile
                require(child.toPath().startsWith(parent.toPath())) { "导出目录无效" }
                if (!child.isDirectory && !child.mkdir()) throw IOException("无法创建导出目录")
                return child
            }
            val comic = childDirectory(selected, comicExportName(unit.comic))
            val chapter = unit.chapters.single()
            val group = childDirectory(comic, exportGroupName(unit.comic, chapter.group))
            unique(group, chapterExportName(chapter), true)
        } else if (directory) unique(selected, unit.name, false)
        else {
            require(format != ComicExportFormat.IMAGES) { "原图导出需要选择目录" }
            Files.createTempFile(requireNotNull(reservation).path.parent, ".copymanga-export-", ".tmp").toFile()
        }
        try {
            onCreated(target.toURI().toString())
            if (format == ComicExportFormat.IMAGES) {
                for (chapter in unit.chapters) {
                    onChapter("${unit.comic.name}／${chapter.name}")
                    for ((index, page) in chapter.pages.withIndex()) {
                        File(target, exportPageName(index, page)).outputStream().use { copy(page, it, onBytes) }
                    }
                }
                File(target, "manifest.json").writeText(exportManifest(unit), Charsets.UTF_8)
            } else {
                ZipOutputStream(target.outputStream().buffered(), Charsets.UTF_8).use { zip ->
                    zip.setLevel(0)
                    for (chapter in unit.chapters) {
                        onChapter("${unit.comic.name}／${chapter.name}")
                        for ((index, page) in chapter.pages.withIndex()) {
                            val prefix = if (format == ComicExportFormat.CBZ) "" else "${exportGroupName(unit.comic, chapter.group)}/${chapterExportName(chapter)}/"
                            zip.putNextEntry(ZipEntry(prefix + exportPageName(index, page)))
                            copy(page, zip, onBytes)
                            zip.closeEntry()
                        }
                    }
                    zip.putNextEntry(ZipEntry("manifest.json"))
                    zip.write(exportManifest(unit).encodeToByteArray()); zip.closeEntry()
                }
            }
            currentCoroutineContext().ensureActive()
            reservation?.publish(target.toPath())
            val output = reservation?.path?.toFile() ?: target
            ComicExportOutput(unit.id, output.toURI().toString(), output.name, format.mimeType)
        } finally {
            // The persisted active URI is staging, never the approved existing destination.
            if (reservation != null) Files.deleteIfExists(target.toPath())
        }
    }
    private suspend fun copy(page: ComicExportPage, output: OutputStream, onBytes: suspend (Long) -> Unit) {
        val file = source(page.source)
        if (file.length() != page.size) throw IOException("本地图片已变化，请重新选择章节")
        file.inputStream().buffered().use { input ->
            input.mark(64)
            if (imageExtension(header(input)) != page.extension) throw IOException("图片格式已变化")
            input.reset()
            val buffer = ByteArray(64 * 1024); var copied = 0L
            while (true) {
                currentCoroutineContext().ensureActive()
                val count = input.read(buffer)
                if (count < 0) break
                output.write(buffer, 0, count); copied += count; onBytes(count.toLong())
            }
            if (copied != page.size) throw IOException("图片读取不完整：$copied/${page.size}")
        }
    }
    override suspend fun delete(uri: String): Boolean = withContext(Dispatchers.IO) {
        try { val target = file(uri); !target.exists() || target.deleteRecursively() } catch (_: Exception) { false }
    }
}
