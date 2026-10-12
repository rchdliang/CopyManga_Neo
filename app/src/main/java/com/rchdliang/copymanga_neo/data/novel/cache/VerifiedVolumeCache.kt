package com.rchdliang.copymanga_neo.data.novel.cache

import java.io.File
import java.io.IOException

/** 先校验并发布按内容命名的正文，再原子切换清单。 */
object VerifiedVolumeCache {
    private val digestPattern = Regex("[0-9a-f]{64}")

    fun textName(body: ByteArray): String = "text.${AtomicCacheFile.digest(body)}.txt"

    fun read(directory: File, name: String?, digest: String?, limit: Int): ByteArray {
        if (digest == null || !digestPattern.matches(digest) ||
            name != "text.$digest.txt"
        ) {
            throw IOException("Invalid cached text identity")
        }
        val body = File(directory, requireNotNull(name)).inputStream()
            .use { AtomicCacheFile.readBounded(it, limit) }
        if (AtomicCacheFile.digest(body) != digest) throw IOException("Cached text checksum mismatch")
        return body
    }

    fun publish(
        directory: File,
        body: ByteArray,
        manifest: ByteArray,
        validator: (ByteArray) -> Unit
    ) =
        publish(directory, body, manifest, validator, AtomicCacheFile::write)

    fun publish(
        directory: File, body: ByteArray, manifest: ByteArray,
        validator: (ByteArray) -> Unit, publisher: (File, ByteArray) -> Unit
    ) {
        validator(body)
        AtomicCacheFile.write(File(directory, textName(body)), body)
        // 清单发布失败时，上一版清单和对应正文仍然可读。
        publisher(File(directory, "manifest.json"), manifest)
    }
}
