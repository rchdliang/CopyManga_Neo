@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package com.rchdliang.copymanga_neo.data.export.portable

import com.rchdliang.copymanga_neo.domain.platform.PlatformIOException
import kotlinx.cinterop.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import platform.Foundation.NSURL
import platform.posix.*

/** Only a just-created picker placeholder is eligible for writing an existing file.
 * The device/inode/empty-file checks are repeated on the opened descriptor, without truncation.
 */
object IosExportDestinations {
    internal data class Reservation(val device: Long, val inode: Long) {
        fun openForWriting(reference: String): Int {
            val path = localPath(reference)
            val fd = open(path, O_WRONLY or O_NOFOLLOW)
            if (fd < 0) throw PlatformIOException("无法打开所选导出文件")
            try {
                checkIdentity(fd, this)
                return fd
            } catch (failure: Throwable) { close(fd); throw failure }
        }
        fun validate(reference: String) { close(openForWriting(reference)) }
        /** Caller should invoke inside the same NSFileCoordinator accessor as publication/rollback. */
        fun removeIfStillEmpty(reference: String): Boolean {
            val selected = localPath(reference)
            val fd = open(selected, O_RDONLY or O_NOFOLLOW)
            if (fd < 0) return errno == ENOENT
            return try {
                checkIdentity(fd, this)
                unlink(selected) == 0
            } catch (_: Exception) { false } finally { close(fd) }
        }
    }
    private val reservations = MutableStateFlow<Map<String, Reservation>>(emptyMap())
    private fun localPath(reference: String): String = if (reference.startsWith("file:"))
        requireNotNull(NSURL.URLWithString(reference)?.path) { "导出地址无效" }
        else reference.also { require(it.startsWith('/')) { "导出地址无效" } }
    private fun path(reference: String) = localPath(reference)
    fun reserve(reference: String) {
        val selected = path(reference)
        val fd = open(selected, O_RDONLY or O_NOFOLLOW)
        if (fd < 0) throw PlatformIOException("无法验证所选导出文件")
        val identity = try { checkIdentity(fd, null) } finally { close(fd) }
        reservations.update { it + (selected to identity) }
    }
    internal fun claim(reference: String): Reservation {
        val selected = path(reference)
        return reservations.getAndUpdate { it - selected }[selected]
            ?: throw PlatformIOException("请选择新的导出文件；原文件不会被覆盖")
    }
    private fun checkIdentity(fd: Int, expected: Reservation?): Reservation = memScoped {
        val info = alloc<stat>()
        if (fstat(fd, info.ptr) != 0 || info.st_size != 0L || info.st_mode.toInt() and 0xf000 != 0x8000)
            throw PlatformIOException("目标不是新建空文件；原文件不会被覆盖")
        val actual = Reservation(info.st_dev.toLong(), info.st_ino.toLong())
        if (expected != null && actual != expected) throw PlatformIOException("目标文件已被替换，请重新选择")
        actual
    }
}
