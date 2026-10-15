@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package com.rchdliang.copymanga_neo.multiplatform.platform

import kotlinx.cinterop.*
import platform.Foundation.*

/** Each returned lease owns exactly one successful security-scope acquisition. */
class IosDocumentLease internal constructor(val url: NSURL, private val scope: NSURL?) : AutoCloseable {
    private var closed = false
    override fun close() { if (!closed) { closed = true; scope?.stopAccessingSecurityScopedResource() } }
}

/** Apple's minimal bookmarks preserve explicit document-picker grants across app launches. */
object IosDocumentBookmarks {
    private const val KEY = "copymanga.document-bookmarks.v1"
    private val lock = NSRecursiveLock()
    private val liveUrls = mutableMapOf<String, NSURL>()
    private val defaults get() = NSUserDefaults.standardUserDefaults

    fun remember(url: NSURL): Result<Unit> = locked {
        runCatching {
            val reference = requireNotNull(url.absoluteString)
            require(url.fileURL) { "请选择系统文件位置" }
            liveUrls[reference] = url
            save(reference, url)
        }
    }

    /** Resolves an exact document or a child of a bookmarked directory, including moved directories. */
    fun acquire(reference: String): IosDocumentLease = locked {
        val requested = fileUrl(reference)
        val requestedPath = requireNotNull(requested.path).trimEnd('/')
        require(!requestedPath.split('/').any { it == "." || it == ".." }) { "文件路径无效" }
        val bookmarks = stored()
        val keys = (liveUrls.keys + bookmarks.keys).distinct().sortedByDescending { it.length }
        for (key in keys) {
            val original = NSURL.URLWithString(key) ?: continue
            var stale = false
            val resolved = bookmarks[key]?.let { data -> memScoped {
                val isStale = alloc<BooleanVar>().apply { value = false }
                val result = NSURL.URLByResolvingBookmarkData(data, NSURLBookmarkResolutionWithoutUI, null, isStale.ptr, null)
                stale = isStale.value
                result
            } } ?: liveUrls[key] ?: continue
            val originalPath = original.path?.trimEnd('/') ?: continue
            val resolvedPath = resolved.path?.trimEnd('/') ?: continue
            val base = when {
                requestedPath == originalPath || requestedPath.startsWith("$originalPath/") -> originalPath
                requestedPath == resolvedPath || requestedPath.startsWith("$resolvedPath/") -> resolvedPath
                else -> continue
            }
            val scoped = resolved.startAccessingSecurityScopedResource()
            val privateContainer = resolvedPath == NSHomeDirectory() || resolvedPath.startsWith(NSHomeDirectory().trimEnd('/') + "/")
            if (!scoped && !privateContainer) continue
            try {
                val relative = requestedPath.removePrefix(base).trimStart('/')
                if (relative.isNotEmpty()) {
                    val attributes = NSFileManager.defaultManager.attributesOfItemAtPath(resolvedPath, null)
                    require(attributes?.get(NSFileType) == NSFileTypeDirectory) { "所选目录已不可用，请重新选择导出位置" }
                }
                val target = if (relative.isEmpty()) resolved else requireNotNull(resolved.URLByAppendingPathComponent(relative))
                if (stale) save(key, resolved)
                return@locked IosDocumentLease(target, resolved.takeIf { scoped })
            } catch (failure: Throwable) {
                if (scoped) resolved.stopAccessingSecurityScopedResource()
                throw failure
            }
        }
        error("保存位置权限已失效或未保存，请重新选择文件或文件夹后重试")
    }

    private fun stored(): Map<String, NSData> = defaults.dictionaryForKey(KEY).orEmpty().entries.mapNotNull { (key, value) ->
        if (key is String && value is NSData && value.length <= 1024u * 1024u) key to value else null
    }.toMap()
    private fun save(reference: String, url: NSURL) {
        val data = url.bookmarkDataWithOptions(NSURLBookmarkCreationMinimalBookmark, null, null, null)
            ?: error("此文件提供方不能保存长期权限；重启应用后请重新选择保存位置")
        require(data.length <= 1024u * 1024u) { "文件权限书签过大，无法保存" }
        defaults.setObject(stored() + (reference to data), KEY)
    }
    private inline fun <T> locked(block: () -> T): T { lock.lock(); return try { block() } finally { lock.unlock() } }
}
