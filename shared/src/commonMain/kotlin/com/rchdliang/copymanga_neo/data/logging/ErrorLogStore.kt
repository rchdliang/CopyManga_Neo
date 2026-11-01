package com.rchdliang.copymanga_neo.data.logging

/** 同步存储接口：崩溃处理不能依赖即将取消的协程或 UI 线程。 */
interface ErrorLogFiles {
    fun <T> locked(block: () -> T): T
    fun read(name: String): String?
    fun size(name: String): Long = read(name)?.encodeToByteArray()?.size?.toLong() ?: 0L
    fun write(name: String, text: String, durable: Boolean)
    fun remove(name: String)
}

/** 普通错误与崩溃各保留四段，单段最多 128 KiB；重启不清空。 */
class ErrorLogStore(private val files: ErrorLogFiles) {
    private val prefixes = listOf("errors", "crashes")
    private fun name(prefix: String, index: Int) = "$prefix-$index.jsonl"

    fun append(record: String, crash: Boolean) = files.locked {
        val prefix = if (crash) "crashes" else "errors"
        val line = record + "\n"
        require(line.encodeToByteArray().size <= SEGMENT_BYTES)
        val current = files.read(name(prefix, 0)).orEmpty()
        val next = if ((current + line).encodeToByteArray().size > SEGMENT_BYTES) {
            for (index in SLOTS - 1 downTo 1) {
                val old = files.read(name(prefix, index - 1))
                if (old != null) files.write(name(prefix, index), old, crash)
                else files.remove(name(prefix, index))
            }
            line
        } else current + line
        files.write(name(prefix, 0), next, crash)
    }

    fun snapshot(): String = files.locked {
        buildString {
            for (prefix in prefixes) for (index in SLOTS - 1 downTo 0) {
                files.read(name(prefix, index))?.let(::append)
            }
        }
    }

    fun clear() = files.locked {
        for (prefix in prefixes) for (index in 0 until SLOTS) files.remove(name(prefix, index))
    }

    fun size(): Long = files.locked {
        prefixes.sumOf { prefix -> (0 until SLOTS).sumOf { files.size(name(prefix, it)) } }
    }

    companion object {
        const val SEGMENT_BYTES = 128 * 1024
        const val SLOTS = 4
    }
}
