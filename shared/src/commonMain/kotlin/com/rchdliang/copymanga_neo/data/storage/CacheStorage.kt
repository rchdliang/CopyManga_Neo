package com.rchdliang.copymanga_neo.data.storage

/**
 * App-private binary files, addressed by relative paths. Writes publish atomically.
 * A missing file returns null; corrupt/oversize/inaccessible files must throw.
 */
interface CacheStorage {
    suspend fun read(path: String, limit: Int): ByteArray?
    suspend fun write(path: String, bytes: ByteArray)
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
