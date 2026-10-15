package com.rchdliang.copymanga_neo.data.image.portable

import com.rchdliang.copymanga_neo.domain.model.ReaderExportImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.URI
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** JVM file boundary shared verbatim by Android and desktop; callers run it on Dispatchers.IO. */
internal class FileReaderImageStore(cacheDirectory: String) {
    private val root = File(cacheDirectory, "reader_shared_images").canonicalFile

    private fun ensureRoot() {
        if (!root.isDirectory && !root.mkdirs()) throw IOException("无法创建图片临时目录")
    }

    suspend fun prune(beforeMillis: Long) {
        ensureRoot()
        root.listFiles()?.forEach { file ->
            currentCoroutineContext().ensureActive()
            if (Files.isRegularFile(file.toPath(), LinkOption.NOFOLLOW_LINKS) && file.lastModified() < beforeMillis) file.delete()
        }
    }

    suspend fun readLocal(source: String, maximumBytes: Int): ByteArray = read(file(source), maximumBytes)

    suspend fun readPrepared(image: ReaderExportImage, maximumBytes: Int): ByteArray {
        val source = file(image.filePath)
        require(!Files.isSymbolicLink(source.toPath()) && source.canonicalFile.parentFile == root && source.name == image.displayName) {
            "图片路径不属于临时导出目录"
        }
        return read(source, maximumBytes)
    }

    suspend fun publish(name: String, bytes: ByteArray): String {
        ensureRoot()
        require(name.isNotBlank() && name.none { it == '/' || it == '\\' || it.code == 0 }) { "图片文件名无效" }
        val target = File(root, name)
        require(target.canonicalFile.parentFile == root && !target.exists()) { "图片临时文件已存在" }
        writeAtomic(target, bytes)
        return target.absolutePath
    }

    suspend fun saveDocument(destination: String, bytes: ByteArray) = writeAtomic(file(destination), bytes)

    suspend fun saveGallery(directory: File, image: ReaderExportImage, bytes: ByteArray) {
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("无法创建相册图片目录")
        val target = File(directory, image.displayName)
        require(!target.exists()) { "相册图片已存在，请重新保存" }
        writeAtomic(target, bytes)
    }

    private suspend fun read(file: File, maximumBytes: Int): ByteArray {
        if (!file.isFile) throw IOException("本地图片不存在，请重新下载")
        require(file.length() <= maximumBytes) { "图片超过单张安全大小限制" }
        return file.inputStream().use { input ->
            ByteArrayOutputStream(minOf(file.length(), 8192L).toInt()).use { output ->
                val buffer = ByteArray(8192)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    require(output.size().toLong() + count <= maximumBytes) { "图片超过单张安全大小限制" }
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
        }
    }

    private suspend fun writeAtomic(file: File, bytes: ByteArray) {
        val destination = file.absoluteFile
        val parent = destination.parentFile ?: throw IOException("无法写入所选位置")
        require(parent.isDirectory && !destination.isDirectory) { "无法写入所选位置" }
        val temporary = File.createTempFile(".reader-", ".tmp", parent)
        try {
            FileOutputStream(temporary).use { output ->
                var offset = 0
                while (offset < bytes.size) {
                    currentCoroutineContext().ensureActive()
                    val count = minOf(8192, bytes.size - offset)
                    output.write(bytes, offset, count)
                    offset += count
                }
                output.fd.sync()
            }
            currentCoroutineContext().ensureActive()
            try {
                Files.move(temporary.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally { temporary.delete() }
    }

    private fun file(location: String): File = if (location.startsWith("file:", ignoreCase = true)) File(URI(location)) else {
        require(!location.contains("://")) { "不支持此本地图片地址" }
        File(location)
    }
}
