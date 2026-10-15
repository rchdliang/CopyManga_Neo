package com.rchdliang.copymanga_neo.multiplatform.platform

import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.file.*
import java.nio.file.attribute.*
import java.util.UUID

/** A lock is never removed/replaced: doing so would permit locks on two different inodes. */
class DesktopInstanceGuard private constructor(
    val directory: Path,
    private val channel: FileChannel,
    private val lock: FileLock?,
    private val session: String?
) : AutoCloseable {
    val primary: Boolean get() = lock != null
    private val control = directory.resolve(".desktop-instance")
    private val owner = Files.getOwner(directory)

    /** Primary only. Called on the UI thread; acknowledge only after reopening succeeds. */
    fun pollOpenRequests(reopen: () -> Unit) {
        check(primary)
        Files.newDirectoryStream(control, "request-*").use { entries ->
            for (request in entries) {
                val name = request.fileName.toString()
                if (!name.matches(Regex("request-[0-9a-f-]{36}-[0-9a-f-]{36}"))) continue
                try { verifyPrivateFile(request, owner) } catch (_: NoSuchFileException) { continue }
                if (name.startsWith("request-$session-")) {
                    reopen()
                    Files.move(request, request.resolveSibling(name.replaceFirst("request-", "ack-")))
                } else {
                    // Old-session requests can never count as an acknowledgement for a new launch.
                    Files.deleteIfExists(request)
                }
            }
        }
    }

    /** Secondary only. Wait for a session-specific acknowledgement; never create a graph here. */
    fun requestOpen(timeoutMillis: Long = 8_000): Boolean {
        check(!primary)
        val deadline = System.nanoTime() + timeoutMillis * 1_000_000
        val requests = mutableListOf<Path>()
        val id = UUID.randomUUID().toString()
        var lastSession: String? = null
        try {
            do {
                val current = readSession()
                if (current != null && current != lastSession) {
                    val path = control.resolve("request-$current-$id")
                    createPrivateFile(path, owner)
                    requests.add(path)
                    lastSession = current
                }
                for (request in requests) {
                    val ack = request.resolveSibling(request.fileName.toString().replaceFirst("request-", "ack-"))
                    if (Files.exists(ack, LinkOption.NOFOLLOW_LINKS)) {
                        verifyPrivateFile(ack, owner)
                        Files.delete(ack)
                        return true
                    }
                }
                Thread.sleep(50)
            } while (System.nanoTime() < deadline)
            return false
        } finally {
            requests.forEach { Files.deleteIfExists(it) }
        }
    }

    private fun readSession(): String? {
        val data = ByteBuffer.allocate(36)
        channel.read(data, 1) // Byte zero is locked, including on Windows; metadata is outside it.
        if (data.position() != 36) return null
        return String(data.array(), Charsets.US_ASCII).takeIf { it.matches(Regex("[0-9a-f-]{36}")) }
    }

    override fun close() {
        try { lock?.release() } finally { channel.close() }
    }

    companion object {
        /** Acquire before constructing any repository or Coil cache. Failure is fatal, not a fallback. */
        fun acquire(dataDirectory: Path): DesktopInstanceGuard {
            Files.createDirectories(dataDirectory)
            val canonical = dataDirectory.toRealPath()
            val owner = Files.getOwner(canonical)
            val control = canonical.resolve(".desktop-instance")
            try {
                Files.createDirectory(control, *privateAttributes(canonical, owner, directory = true))
                finishNewPrivateObject(control, owner)
            }
            catch (_: FileAlreadyExistsException) { }
            require(!Files.isSymbolicLink(control) && Files.isDirectory(control, LinkOption.NOFOLLOW_LINKS)) {
                "应用实例目录不是普通目录"
            }
            verifyPrivateFile(control, owner, directory = true)
            val path = control.resolve("lock")
            try { createPrivateFile(path, owner) } catch (_: FileAlreadyExistsException) { }
            verifyPrivateFile(path, owner)
            val channel = FileChannel.open(path, StandardOpenOption.READ, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)
            try {
                val lock = try { channel.tryLock(0, 1, false) } catch (_: OverlappingFileLockException) { null }
                val session = if (lock != null) UUID.randomUUID().toString() else null
                if (session != null) {
                    val bytes = ByteBuffer.wrap(session.toByteArray(Charsets.US_ASCII))
                    var offset = 1L
                    while (bytes.hasRemaining()) offset += channel.write(bytes, offset)
                    channel.force(true)
                    // Session-qualified acknowledgements from crashed secondaries are stale.
                    Files.newDirectoryStream(control, "ack-*").use { entries -> entries.forEach { ack ->
                        if (ack.fileName.toString().matches(Regex("ack-[0-9a-f-]{36}-[0-9a-f-]{36}"))) {
                            verifyPrivateFile(ack, owner)
                            Files.deleteIfExists(ack)
                        }
                    } }
                }
                return DesktopInstanceGuard(canonical, channel, lock, session)
            } catch (failure: Throwable) { channel.close(); throw failure }
        }
    }
}

private fun createPrivateFile(path: Path, owner: UserPrincipal) {
    Files.createFile(path, *privateAttributes(path.parent, owner, directory = false))
    finishNewPrivateObject(path, owner)
    verifyPrivateFile(path, owner)
}

/** Set access only on new coordination objects; never modify existing OS or user-file permissions. */
private fun privateAttributes(parent: Path, owner: UserPrincipal, directory: Boolean): Array<FileAttribute<*>> =
    if (Files.getFileStore(parent).supportsFileAttributeView("posix")) {
        arrayOf(PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString(if (directory) "rwx------" else "rw-------")))
    } else if (Files.getFileStore(parent).supportsFileAttributeView("acl")) {
        val acl = listOf(AclEntry.newBuilder().setType(AclEntryType.ALLOW).setPrincipal(owner)
            .setPermissions(AclEntryPermission.values().toSet()).build())
        arrayOf(object : FileAttribute<List<AclEntry>> {
            override fun name() = "acl:acl"
            override fun value() = acl
        })
    } else error("当前文件系统无法建立仅供当前用户访问的实例信号")

// Windows may add inherited ACEs at creation. Replace the DACL only on the new object,
// before publishing any session/request, then verify. Never repair existing unsafe paths.
private fun finishNewPrivateObject(path: Path, owner: UserPrincipal) {
    if (Files.getFileAttributeView(path, PosixFileAttributeView::class.java, LinkOption.NOFOLLOW_LINKS) == null) {
        val acl = Files.getFileAttributeView(path, AclFileAttributeView::class.java, LinkOption.NOFOLLOW_LINKS)
            ?: error("无法设置新建实例信号权限")
        acl.acl = listOf(AclEntry.newBuilder().setType(AclEntryType.ALLOW).setPrincipal(owner)
            .setPermissions(AclEntryPermission.values().toSet()).build())
    }
}

private fun verifyPrivateFile(path: Path, owner: UserPrincipal, directory: Boolean = false) {
    val attributes = Files.readAttributes(path, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
    require(if (directory) attributes.isDirectory else attributes.isRegularFile) { "实例信号不是普通文件" }
    require(Files.getOwner(path, LinkOption.NOFOLLOW_LINKS) == owner) { "实例信号所有者不匹配" }
    val posix = Files.getFileAttributeView(path, PosixFileAttributeView::class.java, LinkOption.NOFOLLOW_LINKS)
    if (posix != null) {
        require(posix.readAttributes().permissions().none { it.name.startsWith("GROUP_") || it.name.startsWith("OTHERS_") }) {
            "实例信号权限过宽"
        }
    } else {
        val acl = Files.getFileAttributeView(path, AclFileAttributeView::class.java, LinkOption.NOFOLLOW_LINKS)
            ?: error("无法验证实例信号权限")
        require(acl.acl.none { it.type() == AclEntryType.ALLOW && it.principal() != owner }) { "实例信号权限过宽" }
    }
}
