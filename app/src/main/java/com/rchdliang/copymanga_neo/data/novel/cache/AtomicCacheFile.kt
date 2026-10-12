package com.rchdliang.copymanga_neo.data.novel.cache

import com.rchdliang.copymanga_neo.domain.reading.ReadingContentHash
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** 限制读取大小，并在同一目录原子替换文件；清单由调用方最后发布。 */
object AtomicCacheFile {
    fun readBounded(stream: InputStream, limit: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val read = stream.read(buffer)
            if (read == -1) break
            if (output.size() > limit - read) throw IOException("Official resource exceeds cache limit")
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    fun write(target: File, bytes: ByteArray) {
        val parent = requireNotNull(target.parentFile)
        if (!parent.isDirectory && !parent.mkdirs()) throw IOException("Cannot create novel cache")
        val temporary = File.createTempFile(target.name, ".partial", parent)
        try {
            FileOutputStream(temporary).use { output ->
                output.write(bytes)
                output.fd.sync()
            }
            try {
                Files.move(
                    temporary.toPath(), target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temporary.toPath())
        }
    }

    fun digest(bytes: ByteArray): String = ReadingContentHash.sha256(bytes)
}
