package com.rchdliang.copymanga_neo.domain.reading

/** 保持持久化键的 UTF-16 长度前缀，以及清除时间戳的严格边界。 */
object ReadingMergePolicy {
    fun key(vararg parts: String): String = buildString {
        parts.forEach { append(it.length).append(':').append(it) }
    }

    fun canUseCoarse(
        owner: String, kind: String, path: String,
        clearedOwners: Map<String, Long>, deletedBooks: Map<String, Long>
    ): Boolean =
        !clearedOwners.containsKey(owner) && !deletedBooks.containsKey(key(owner, kind, path))

    fun accepts(incoming: Long, current: Long, deletion: Long): Boolean =
        incoming > deletion && incoming >= current

    fun survives(timestamp: Long, deletion: Long): Boolean = timestamp > deletion
}
