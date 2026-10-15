@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package com.rchdliang.copymanga_neo.data.export.portable

import com.rchdliang.copymanga_neo.domain.model.*
import com.rchdliang.copymanga_neo.domain.platform.PlatformIOException
import com.rchdliang.copymanga_neo.multiplatform.platform.IosDocumentBookmarks
import com.rchdliang.copymanga_neo.data.local.portable.platformDocumentStorage
import kotlinx.cinterop.*
import kotlinx.coroutines.*
import platform.Foundation.*
import platform.posix.*

/** Uses the security-scoped directory URL retained by the iOS picker host.
 * Sources are confined to app downloads; archive and image bytes are streamed in 64 KiB chunks.
 */
class FileComicExportIo(downloadDirectory: String) : ComicExportIo {
    private val files = NSFileManager.defaultManager
    private val root = resolved(location(downloadDirectory)).trimEnd('/')
    init { require(root.isNotEmpty()) { "Downloads need a dedicated directory" } }
    private val ownership = IosExportOwnership(platformDocumentStorage("${root.substringBeforeLast('/')}/export-ownership"))
    private fun location(value: String): String = if (value.startsWith("file:"))
        NSURL.URLWithString(value)?.path ?: throw PlatformIOException("文件地址无效") else value
    private fun resolved(path: String) = NSString.create(string = path).stringByResolvingSymlinksInPath
    private fun error(message: String): Nothing = throw PlatformIOException("$message: ${strerror(errno)?.toKString() ?: errno}")
    private fun source(value: String): String = resolved(location(value)).also {
        require(it.startsWith("$root/")) { "图片路径不属于下载目录" }
        val attributes = files.attributesOfItemAtPath(it, null)
        if (attributes?.get(NSFileType) != NSFileTypeRegular || size(it) <= 0) throw PlatformIOException("本地图片缺失")
    }
    private fun size(path: String): Long = (files.attributesOfItemAtPath(path, null)?.get(NSFileSize) as? NSNumber)
        ?.longLongValue ?: throw PlatformIOException("无法读取文件大小")
    private fun uri(path: String) = requireNotNull(NSURL.fileURLWithPath(path).absoluteString)
    private fun read(fd: Int, buffer: ByteArray): Int = buffer.usePinned {
        var count: Long
        do { count = platform.posix.read(fd, it.addressOf(0), buffer.size.toULong()) } while (count < 0 && errno == EINTR)
        if (count < 0) error("图片读取失败")
        count.toInt()
    }
    private fun header(path: String): ByteArray {
        val fd = open(path, O_RDONLY); if (fd < 0) error("无法打开图片")
        return try { val bytes = ByteArray(64); bytes.copyOf(read(fd, bytes)) } finally { close(fd) }
    }
    override suspend fun inspect(source: String): ComicExportPage = withContext(Dispatchers.IO) {
        val path = source(source); ComicExportPage(uri(path), size(path), imageExtension(header(path)))
    }
    private suspend fun copy(page: ComicExportPage, consume: suspend (ByteArray) -> Unit) {
        val path = source(page.source)
        if (size(path) != page.size || imageExtension(header(path)) != page.extension)
            throw PlatformIOException("本地图片已变化，请重新选择章节")
        val fd = open(path, O_RDONLY); if (fd < 0) error("无法打开图片")
        try {
            val buffer = ByteArray(64 * 1024); var copied = 0L
            while (true) {
                currentCoroutineContext().ensureActive()
                val count = read(fd, buffer); if (count == 0) break
                if (copied > page.size - count) throw PlatformIOException("图片读取长度已变化")
                consume(buffer.copyOf(count)); copied += count
            }
            if (copied != page.size) throw PlatformIOException("图片读取不完整")
        } finally { close(fd) }
    }
    private fun mkdir(path: String) {
        if (files.fileExistsAtPath(path)) {
            if (files.attributesOfItemAtPath(path, null)?.get(NSFileType) != NSFileTypeDirectory)
                throw PlatformIOException("目标不是目录")
        } else if (platform.posix.mkdir(path, 0x1c0u) != 0) error("无法创建导出目录")
    }
    private fun createFile(path: String): Int = open(path, O_WRONLY or O_CREAT or O_EXCL, 0x180u)
    private fun unique(parent: String, name: String, directory: Boolean): Pair<String, Int?> {
        val extension = if (directory) "" else name.substringAfterLast('.', "")
        val stem = if (extension.isEmpty()) name else name.removeSuffix(".$extension")
        var number = 1
        while (true) {
            val child = if (number == 1) name else "$stem ($number)" + if (extension.isEmpty()) "" else ".$extension"
            val path = "$parent/$child"
            if (directory) {
                if (platform.posix.mkdir(path, 0x1c0u) == 0) return path to null
            } else {
                val fd = createFile(path); if (fd >= 0) return path to fd
            }
            if (errno != EEXIST) error("无法创建导出文件")
            number++
        }
    }
    private fun write(fd: Int, bytes: ByteArray) {
        if (bytes.isEmpty()) return
        bytes.usePinned { pinned ->
            var offset = 0
            while (offset < bytes.size) {
                val count = platform.posix.write(fd, pinned.addressOf(offset), (bytes.size - offset).toULong())
                if (count < 0 && errno == EINTR) continue
                if (count <= 0) error("导出写入失败")
                offset += count.toInt()
            }
        }
    }
    private suspend fun output(path: String, block: suspend (Int) -> Unit) {
        val fd = createFile(path); if (fd < 0) error("无法创建导出图片")
        try { block(fd); if (fsync(fd) != 0) error("导出文件同步失败") } finally { close(fd) }
    }
    override suspend fun write(
        unit: ExportUnit, destination: String, directory: Boolean, format: ComicExportFormat,
        onCreated: suspend (String) -> Unit, onBytes: suspend (Long) -> Unit, onChapter: (String) -> Unit
    ): ComicExportOutput = withContext(Dispatchers.IO) {
        currentCoroutineContext().ensureActive()
        ownership.load()
        val destinationLease = IosDocumentBookmarks.acquire(destination)
        try {
        val selected = resolved(requireNotNull(destinationLease.url.path))
        var staging: String? = null
        var reservation: IosExportDestinations.Reservation? = null
        val (target, fd) = if (directory) {
            if (files.attributesOfItemAtPath(selected, null)?.get(NSFileType) != NSFileTypeDirectory)
                throw PlatformIOException("请选择有效导出目录")
            if (format == ComicExportFormat.IMAGES) {
                val comic = "$selected/${comicExportName(unit.comic)}"; mkdir(comic)
                val chapter = unit.chapters.single()
                val group = "$comic/${exportGroupName(unit.comic, chapter.group)}"; mkdir(group)
                unique(group, chapterExportName(chapter), true)
            } else unique(selected, unit.name, false)
        } else {
            require(format != ComicExportFormat.IMAGES) { "原图导出需要选择目录" }
            if (files.fileExistsAtPath(selected)) {
                reservation = IosExportDestinations.claim(destination)
                // Verify before announcing the output, then verify again inside coordinated publication.
                close(requireNotNull(reservation).openForWriting(selected))
                val temporary = NSTemporaryDirectory() + "copymanga-comic-export-" + NSUUID().UUIDString + ".tmp"
                val opened = createFile(temporary); if (opened < 0) error("无法建立导出暂存文件")
                staging = temporary
                selected to opened
            } else {
                val opened = createFile(selected); if (opened < 0) error("无法创建导出文件")
                selected to opened
            }
        }
        try {
            onCreated(uri(target))
            ownership.record(uri(target), target, fd == null, reservation != null)
            if (fd == null) {
                for (chapter in unit.chapters) {
                    onChapter("${unit.comic.name}／${chapter.name}")
                    for ((index, page) in chapter.pages.withIndex()) output("$target/${exportPageName(index, page)}") { image ->
                        copy(page) { bytes -> write(image, bytes); onBytes(bytes.size.toLong()) }
                    }
                }
                output("$target/manifest.json") { write(it, exportManifest(unit).encodeToByteArray()) }
            } else {
                writeComicArchive(unit, format, { write(fd, it) }, ::copy, onBytes, onChapter)
                if (fsync(fd) != 0) error("导出文件同步失败")
            }
            currentCoroutineContext().ensureActive()
            staging?.let { temporary ->
                publishReserved(temporary, target, requireNotNull(reservation), currentCoroutineContext())
            }
            currentCoroutineContext().ensureActive()
            ownership.committed()
            ComicExportOutput(unit.id, uri(target), target.substringAfterLast('/'), format.mimeType)
        } finally {
            if (fd != null) close(fd)
            staging?.let { files.removeItemAtPath(it, null) }
        }
        } finally { destinationLease.close() }
    }
    private fun publishReserved(
        temporary: String, target: String, reservation: IosExportDestinations.Reservation,
        context: kotlin.coroutines.CoroutineContext
    ) {
        var completed = false
        var failure: Throwable? = null
        memScoped {
            val coordinationError = alloc<ObjCObjectVar<NSError?>>()
            coordinationError.value = null
            NSFileCoordinator(filePresenter = null).coordinateWritingItemAtURL(
                NSURL.fileURLWithPath(target), NSFileCoordinatorWritingForReplacing, coordinationError.ptr
            ) { coordinated ->
                try {
                    val selected = coordinated?.path ?: throw PlatformIOException("无法协调导出位置")
                    val output = reservation.openForWriting(selected)
                    ownership.startedWriting(uri(target))
                    try {
                        val input = open(temporary, O_RDONLY); if (input < 0) error("无法读取已完成的导出文件")
                        try {
                            val bytes = ByteArray(64 * 1024)
                            while (true) {
                                context.ensureActive()
                                val count = read(input, bytes); if (count == 0) break
                                write(output, bytes.copyOf(count))
                            }
                            if (fsync(output) != 0) error("导出文件同步失败")
                            completed = true
                        } finally { close(input) }
                    } finally { close(output) }
                } catch (error: Throwable) { failure = error }
            }
            failure?.let { throw it }
            if (!completed) throw PlatformIOException(coordinationError.value?.localizedDescription ?: "无法确认文件已写入所选位置")
        }
    }
    override suspend fun delete(uri: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val lease = IosDocumentBookmarks.acquire(uri)
            try { ownership.delete(uri, requireNotNull(lease.url.path)) } finally { lease.close() }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { false }
    }
}
