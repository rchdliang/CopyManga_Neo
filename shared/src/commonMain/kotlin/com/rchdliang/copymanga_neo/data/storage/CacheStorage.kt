package com.rchdliang.copymanga_neo.data.storage

/**
 * App-private binary files, addressed by relative paths. Writes publish atomically.
 * A missing file returns null; corrupt/oversize/inaccessible files must throw.
 */
interface CacheStorage {
    suspend fun read(path: String, limit: Int): ByteArray?
    suspend fun write(path: String, bytes: ByteArray)
    /** 分块写入；生产文件后端覆盖此方法，成功返回后才发布正式文件。 */
    suspend fun writeStream(path: String, publish: (suspend (suspend () -> Unit) -> Unit)? = null, produce: suspend (suspend (ByteArray) -> Unit) -> Unit) {
        val chunks = mutableListOf<ByteArray>()
        var size = 0
        produce { chunk ->
            require(size.toLong() + chunk.size <= 64L * 1024 * 1024) { "图片超过单张安全大小限制" }
            chunks += chunk.copyOf(); size += chunk.size
        }
        val bytes = ByteArray(size)
        var offset = 0
        chunks.forEach { it.copyInto(bytes, offset); offset += it.size }
        if (publish == null) write(path, bytes) else publish { write(path, bytes) }
    }
    suspend fun list(prefix: String = ""): List<String>
    suspend fun remove(path: String)
    suspend fun size(path: String): Long
    suspend fun availableBytes(): Long
    /** A URI/path readable by the platform's image loader. */
    suspend fun imageLocation(path: String): String
}

internal fun validateCachePath(path: String, allowEmpty: Boolean = false) {
    require((allowEmpty && path.isEmpty()) ||
        (path.isNotEmpty() && path.split('/').all { it.isNotEmpty() && it != "." && it != ".." } &&
            path.none { it == '\\' || it == '\u0000' || it == ':' })) { "Invalid cache path" }
}

/** Creates the native file or transactional browser cache for this app-private namespace. */
expect fun platformCacheStorage(directory: String): CacheStorage
