package com.rchdliang.copymanga_neo.data.download.portable

import com.rchdliang.copymanga_neo.domain.platform.PlatformIOException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Export reads cannot overlap deletion/re-download mutations; whole-book keys end in '/'. */
class LocalDownloadAccess {
    private val mutex = Mutex()
    private val readers = mutableMapOf<String, Int>()
    private val writers = mutableMapOf<String, Int>()

    suspend fun export(keys: List<String>): Lease = mutex.withLock {
        acquire(keys, readers, writers, "下载文件正在变更，请稍后导出")
    }

    suspend fun mutate(key: String): Lease = mutex.withLock {
        acquire(listOf(key), writers, readers, "这些章节正在导出，请完成或取消导出后再修改")
    }

    private fun overlaps(a: String, b: String) = a == b ||
        (a.endsWith('/') && b.startsWith(a)) || (b.endsWith('/') && a.startsWith(b))

    private fun acquire(
        keys: List<String>, own: MutableMap<String, Int>, other: Map<String, Int>, message: String
    ): Lease {
        val unique = keys.distinct()
        if (unique.any { key -> other.keys.any { overlaps(key, it) } }) throw PlatformIOException(message)
        unique.forEach { own[it] = (own[it] ?: 0) + 1 }
        var released = false
        return Lease {
            mutex.withLock {
                if (!released) {
                    released = true
                    unique.forEach { key ->
                        val remaining = (own[key] ?: 1) - 1
                        if (remaining == 0) own.remove(key) else own[key] = remaining
                    }
                }
            }
        }
    }
}

class Lease internal constructor(private val releaseAction: suspend () -> Unit) {
    suspend fun release() = withContext(NonCancellable) { releaseAction() }
}

suspend inline fun <T> Lease.use(block: () -> T): T = try {
    block()
} finally {
    withContext(NonCancellable) { release() }
}
