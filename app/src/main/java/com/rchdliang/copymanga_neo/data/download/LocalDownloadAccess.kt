package com.rchdliang.copymanga_neo.data.download

import java.io.Closeable
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** 导出持有章节读租约；删除、重下载持有写租约，避免文件和数据库状态相互穿插。 */
@Singleton
class LocalDownloadAccess @Inject constructor() {
    private val readers = mutableMapOf<String, Int>()
    private val writers = mutableMapOf<String, Int>()

    @Synchronized
    fun export(keys: List<String>): Closeable =
        acquire(keys, readers, writers, "下载文件正在变更，请稍后导出")

    @Synchronized
    fun mutate(key: String): Closeable =
        acquire(listOf(key), writers, readers, "这些章节正在导出，请完成或取消导出后再修改")

    private fun overlaps(a: String, b: String) = a == b ||
            (a.endsWith('/') && b.startsWith(a)) || (b.endsWith('/') && a.startsWith(b))

    private fun acquire(
        keys: List<String>,
        own: MutableMap<String, Int>,
        other: Map<String, Int>,
        message: String
    ): Closeable {
        val unique = keys.distinct()
        if (unique.any { key -> other.keys.any { overlaps(key, it) } }) throw IOException(message)
        unique.forEach { own[it] = (own[it] ?: 0) + 1 }
        var closed = false
        return Closeable {
            synchronized(this) {
                if (!closed) {
                    closed = true
                    unique.forEach { key ->
                        val count = (own[key] ?: 1) - 1
                        if (count == 0) own.remove(key) else own[key] = count
                    }
                }
            }
        }
    }
}
