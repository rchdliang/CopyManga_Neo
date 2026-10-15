@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package com.rchdliang.copymanga_neo.data.export.portable

import com.rchdliang.copymanga_neo.data.local.portable.DocumentStorage
import kotlinx.cinterop.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import platform.Foundation.NSFileManager
import platform.posix.*

@Serializable
private data class OwnedExport(val device: Long, val inode: Long, val directory: Boolean, val emptyOnly: Boolean)

/** Recovery deletes only an output whose recorded filesystem identity still matches. */
internal class IosExportOwnership(private val storage: DocumentStorage) {
    private val serializer = MapSerializer(String.serializer(), OwnedExport.serializer())
    private var entries: Map<String, OwnedExport>? = null
    suspend fun load() {
        if (entries == null) entries = storage.read("outputs")?.let { Json.decodeFromString(serializer, it) } ?: emptyMap()
    }
    private suspend fun persist() = storage.write("outputs", Json.encodeToString(serializer, requireNotNull(entries)))
    suspend fun record(reference: String, path: String, directory: Boolean, emptyOnly: Boolean) {
        load()
        val identity = inspect(path) ?: error("无法确认新建导出文件的身份")
        check(identity.directory == directory)
        // The shared repository has one active output; previous successful outputs are never rollback targets.
        entries = mapOf(reference to identity.copy(emptyOnly = emptyOnly))
        persist()
    }
    /** Called only after the reserved empty descriptor has been revalidated in the coordinator. */
    fun startedWriting(reference: String) {
        val current = entries?.get(reference) ?: return
        entries = requireNotNull(entries) + (reference to current.copy(emptyOnly = false))
    }
    suspend fun committed() = persist()
    suspend fun delete(reference: String, path: String): Boolean {
        load()
        val owner = entries?.get(reference) ?: return false
        val current = inspect(path)
        if (current == null) {
            if (NSFileManager.defaultManager.fileExistsAtPath(path)) return false
        } else {
            if (owner.device != current.device || owner.inode != current.inode || owner.directory != current.directory) return false
            if (owner.emptyOnly && !current.emptyOnly) return false
            if (!NSFileManager.defaultManager.removeItemAtPath(path, null)) return false
        }
        entries = requireNotNull(entries) - reference
        persist()
        return true
    }
    private fun inspect(path: String): OwnedExport? {
        val fd = open(path, O_RDONLY or O_NOFOLLOW)
        if (fd < 0) return null
        return try { memScoped {
            val info = alloc<stat>()
            if (fstat(fd, info.ptr) != 0) return@memScoped null
            val type = info.st_mode.toInt() and 0xf000
            if (type != 0x8000 && type != 0x4000) return@memScoped null
            OwnedExport(info.st_dev.toLong(), info.st_ino.toLong(), type == 0x4000, info.st_size == 0L)
        } } finally { close(fd) }
    }
}
