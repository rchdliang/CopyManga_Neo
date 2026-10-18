package com.rchdliang.copymanga_neo.data.storage

import kotlinx.coroutines.ensureActive

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.LinkOption
import java.nio.file.SimpleFileVisitor
import java.nio.file.FileVisitResult
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Atomic, bounded cache storage for app-private local files. */
class FileCacheStorage(directory: String) : CacheStorage {
    private val root = File(directory).canonicalFile
    init { require(root.parentFile != null) { "Cache must use a dedicated app directory" } }
    private fun file(path: String, allowEmpty: Boolean = false): File {
        validateCachePath(path, allowEmpty)
        val result = if (path.isEmpty()) root else File(root, path).canonicalFile
        require(result == root || result.toPath().startsWith(root.toPath())) { "Cache path escapes root" }
        return result
    }
    override suspend fun read(path: String, limit: Int): ByteArray? = withContext(Dispatchers.IO) {
        require(limit >= 0)
        val target = file(path)
        if (!target.exists()) return@withContext null
        if (!target.isFile || target.length() > limit) throw IOException("Cache file is invalid or exceeds limit")
        target.inputStream().use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (output.size() > limit - count) throw IOException("Cache file exceeds limit")
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
    }
    override suspend fun write(path: String, bytes: ByteArray): Unit = withContext(Dispatchers.IO) {
        val target = file(path)
        val parent = requireNotNull(target.parentFile)
        Files.createDirectories(parent.toPath())
        val temporary = File.createTempFile("cache-", ".partial", parent)
        try {
            FileOutputStream(temporary).use { output -> output.write(bytes); output.fd.sync() }
            try {
                Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally { Files.deleteIfExists(temporary.toPath()) }
    }
    /** 网络或校验失败时删除临时文件，原目标文件保持可读。 */
    override suspend fun writeStream(path: String, publish: (suspend (suspend () -> Unit) -> Unit)?, produce: suspend (suspend (ByteArray) -> Unit) -> Unit): Unit = withContext(Dispatchers.IO) {
        val target = file(path)
        val parent = requireNotNull(target.parentFile)
        Files.createDirectories(parent.toPath())
        val temporary = File.createTempFile("download-", ".partial", parent)
        try {
            FileOutputStream(temporary).use { output ->
                produce { chunk -> kotlinx.coroutines.currentCoroutineContext().ensureActive(); output.write(chunk) }
                output.fd.sync()
            }
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            val commit: suspend () -> Unit = {
                Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
                Unit
            }
            if (publish == null) commit() else publish(commit)
        } finally { Files.deleteIfExists(temporary.toPath()) }
    }
    override suspend fun list(prefix: String): List<String> = withContext(Dispatchers.IO) {
        val parent = file(prefix, allowEmpty = true)
        if (!parent.exists()) emptyList()
        else parent.walkTopDown().onEnter { !Files.isSymbolicLink(it.toPath()) }.onFail { _, error -> throw error }
            .filter { Files.isRegularFile(it.toPath(), LinkOption.NOFOLLOW_LINKS) }.map { it.relativeTo(root).invariantSeparatorsPath }.toList()
    }
    override suspend fun remove(path: String): Unit = withContext(Dispatchers.IO) {
        val target = file(path, allowEmpty = true)
        if (target.exists()) Files.walkFileTree(target.toPath(), object : SimpleFileVisitor<Path>() {
            override fun visitFile(file: Path, attributes: BasicFileAttributes): FileVisitResult {
                Files.delete(file); return FileVisitResult.CONTINUE
            }
            override fun postVisitDirectory(directory: Path, error: IOException?): FileVisitResult {
                if (error != null) throw error
                Files.delete(directory); return FileVisitResult.CONTINUE
            }
        })
    }
    override suspend fun size(path: String): Long = withContext(Dispatchers.IO) {
        val target = file(path)
        if (!target.isFile) throw IOException("Cache file is missing: $path")
        target.length()
    }
    override suspend fun availableBytes(): Long = withContext(Dispatchers.IO) {
        generateSequence(root) { it.parentFile }.first { it.exists() }.usableSpace
    }
    override suspend fun imageLocation(path: String): String = file(path).toURI().toString()
}

actual fun platformCacheStorage(directory: String): CacheStorage = FileCacheStorage(directory)
