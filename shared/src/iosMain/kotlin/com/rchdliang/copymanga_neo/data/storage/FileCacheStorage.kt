@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package com.rchdliang.copymanga_neo.data.storage

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.*
import platform.posix.memcpy

/** Foundation atomic replacement in the app sandbox; no image bytes leave the device. */
class FileCacheStorage(directory: String) : CacheStorage {
    private val files = NSFileManager.defaultManager
    private val root = NSString.create(string = directory).stringByResolvingSymlinksInPath.trimEnd('/')
    init { require(root.isNotEmpty()) { "Cache must use a dedicated app directory" } }
    private fun path(relative: String, allowEmpty: Boolean = false): String {
        validateCachePath(relative, allowEmpty)
        val candidate = if (relative.isEmpty()) root else "$root/$relative"
        val resolved = NSString.create(string = candidate).stringByResolvingSymlinksInPath
        require(resolved == root || resolved.startsWith("$root/")) { "Cache path escapes root" }
        return resolved
    }
    private fun attributes(path: String): Map<Any?, *> = files.attributesOfItemAtPath(path, null)
        ?: error("Cannot inspect cache file")
    override suspend fun read(path: String, limit: Int): ByteArray? {
        require(limit >= 0)
        val target = path(path)
        if (!files.fileExistsAtPath(target)) return null
        val size = (attributes(target)[NSFileSize] as? NSNumber)?.longLongValue ?: error("Invalid cache file size")
        require(size in 0..limit.toLong()) { "Cache file exceeds limit" }
        val data = NSData.dataWithContentsOfFile(target) ?: error("Cannot read cache file")
        require(data.length <= limit.toULong()) { "Cache file exceeds limit" }
        return ByteArray(data.length.toInt()).also { bytes ->
            if (bytes.isNotEmpty()) bytes.usePinned { memcpy(it.addressOf(0), data.bytes, data.length) }
        }
    }
    override suspend fun write(path: String, bytes: ByteArray) {
        val target = path(path)
        val parent = target.substringBeforeLast('/')
        check(files.createDirectoryAtPath(parent, true, null, null)) { "Cannot create cache directory" }
        val data = if (bytes.isEmpty()) NSData.create(bytes = null, length = 0uL)
        else bytes.usePinned { NSData.create(bytes = it.addressOf(0), length = bytes.size.toULong()) }
        check(data.writeToFile(target, true)) { "Atomic cache write failed" }
    }
    override suspend fun list(prefix: String): List<String> {
        val directory = path(prefix, allowEmpty = true)
        if (!files.fileExistsAtPath(directory)) return emptyList()
        if (attributes(directory)[NSFileType] == NSFileTypeRegular) return listOf(prefix)
        val iterator = files.enumeratorAtPath(directory) ?: error("Cannot enumerate cache directory")
        return buildList {
            while (true) {
                val entry = iterator.nextObject() as? String ?: break
                val absolute = "$directory/$entry"
                val type = attributes(absolute)[NSFileType]
                if (type == NSFileTypeSymbolicLink) iterator.skipDescendants()
                else if (type == NSFileTypeRegular) add(if (prefix.isEmpty()) entry else "$prefix/$entry")
            }
        }
    }
    override suspend fun remove(path: String) {
        val target = path(path, allowEmpty = true)
        if (files.fileExistsAtPath(target)) check(files.removeItemAtPath(target, null)) { "Cache deletion failed" }
    }
    override suspend fun size(path: String): Long =
        (attributes(path(path))[NSFileSize] as? NSNumber)?.longLongValue ?: error("Invalid cache file size")
    override suspend fun availableBytes(): Long {
        check(files.createDirectoryAtPath(root, true, null, null)) { "Cannot create cache directory" }
        return (files.attributesOfFileSystemForPath(root, null)?.get(NSFileSystemFreeSize) as? NSNumber)?.longLongValue
            ?: error("Available device storage is unknown")
    }
    override suspend fun imageLocation(path: String): String = requireNotNull(NSURL.fileURLWithPath(path(path)).absoluteString)
}

actual fun platformCacheStorage(directory: String): CacheStorage = FileCacheStorage(directory)
