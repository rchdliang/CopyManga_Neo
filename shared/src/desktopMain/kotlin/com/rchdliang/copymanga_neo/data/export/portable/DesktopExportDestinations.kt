package com.rchdliang.copymanga_neo.data.export.portable

import com.sun.jna.Platform
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileTime
import java.util.concurrent.ConcurrentHashMap

/** One-use approval captured by the picker. Never delete an old destination to make a move work. */
object DesktopExportDestinations {
    class Reservation internal constructor(
        internal val path: Path,
        private val identity: Identity?
    ) {
        internal fun validate() {
            if (snapshot(path) != identity) throw IOException("目标文件已变化，请重新选择导出位置；原文件未被覆盖")
        }
        internal fun publish(staging: Path) {
            validate()
            if (identity == null) {
                // ATOMIC_MOVE may replace an existing target even without REPLACE_EXISTING.
                // The ordinary same-filesystem move preserves no-clobber semantics for new names.
                Files.move(staging, path)
            } else {
                Files.move(staging, path, ATOMIC_MOVE, REPLACE_EXISTING)
            }
        }
    }
    internal data class Identity(val key: Any, val size: Long, val modified: FileTime, val created: FileTime)
    private val approved = ConcurrentHashMap<Path, Reservation>()
    private fun normalized(path: Path): Path = path.toAbsolutePath().normalize().let {
        requireNotNull(it.parent).toRealPath().resolve(it.fileName)
    }
    private fun snapshot(path: Path): Identity? {
        if (Platform.isWindows()) return windowsExportFileIdentity(path)
        val attributes = try { Files.readAttributes(path, BasicFileAttributes::class.java, NOFOLLOW_LINKS) }
        catch (_: java.nio.file.NoSuchFileException) { return null }
        if (!attributes.isRegularFile || attributes.isSymbolicLink) throw IOException("请选择普通文件，不能替换目录或符号链接")
        val key = attributes.fileKey() ?: throw IOException("此文件系统无法验证原文件身份，请选择新的文件名")
        return Identity(key, attributes.size(), attributes.lastModifiedTime(), attributes.creationTime())
    }
    /** Capture before displaying confirmation, so changes while the dialog is open are rejected. */
    fun capture(path: Path): Reservation {
        val selected = normalized(path)
        approved.remove(selected)
        return Reservation(selected, snapshot(selected))
    }
    fun approve(reservation: Reservation) {
        reservation.validate()
        approved[reservation.path] = reservation
    }
    internal fun claim(path: Path): Reservation {
        val selected = normalized(path)
        val reservation = approved.remove(selected)
        if (reservation != null) return reservation.also { it.validate() }
        // Existing files always need explicit picker approval, including retries after restart.
        if (Files.exists(selected, NOFOLLOW_LINKS)) throw IOException("替换现有文件需要重新选择并确认导出位置")
        return Reservation(selected, null)
    }
}
