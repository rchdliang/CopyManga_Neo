package com.rchdliang.copymanga_neo.data.local.portable

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption

actual fun platformDocumentStorage(directory: String): DocumentStorage = FileDocumentStorage(Paths.get(directory))

private class FileDocumentStorage(private val root: Path) : DocumentStorage {
    private fun path(key: String): Path {
        require(key.matches(Regex("[A-Za-z0-9_.-]+")) && key != "." && key != "..")
        return root.resolve(key)
    }
    override suspend fun read(key: String): String? = withContext(Dispatchers.IO) {
        val file = path(key)
        if (Files.exists(file)) Files.readAllBytes(file).decodeToString(throwOnInvalidSequence = true) else null
    }
    override suspend fun write(key: String, value: String): Unit = withContext(Dispatchers.IO) {
        Files.createDirectories(root)
        val destination = path(key)
        val temporary = Files.createTempFile(root, ".write-", ".tmp")
        try {
            // 部分 Android ART 没有 Java 11 的 Files.writeString/readString。
            Files.write(temporary, value.toByteArray(Charsets.UTF_8))
            FileChannel.open(temporary, StandardOpenOption.WRITE).use { it.force(true) }
            try { Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING) }
            catch (unsupported: AtomicMoveNotSupportedException) {
                // Do not destroy an existing document when the filesystem cannot guarantee atomicity.
                if (Files.exists(destination)) throw unsupported
                Files.move(temporary, destination)
            }
        } finally { Files.deleteIfExists(temporary) }
    }
    override suspend fun remove(key: String): Unit = withContext(Dispatchers.IO) { Files.deleteIfExists(path(key)) }
    override suspend fun append(key: String, value: String): Unit = withContext(Dispatchers.IO) {
        Files.createDirectories(root)
        Files.write(path(key), value.toByteArray(Charsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.APPEND)
    }
    override suspend fun size(key: String): Long = withContext(Dispatchers.IO) {
        val file = path(key)
        if (Files.exists(file)) Files.size(file) else 0L
    }
    override suspend fun readPrefix(key: String, maxChars: Int): String? = withContext(Dispatchers.IO) {
        require(maxChars in 1..4096)
        val file = path(key)
        if (!Files.exists(file)) null else Files.newBufferedReader(file, Charsets.UTF_8).use {
            val chars = CharArray(maxChars)
            val count = it.read(chars)
            if (count < 0) "" else chars.concatToString(0, count)
        }
    }
}
