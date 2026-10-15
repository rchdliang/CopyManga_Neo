package com.rchdliang.copymanga_neo.domain.model

@kotlinx.serialization.Serializable
data class ReadingProgress(
    val chapterUuid: String,
    val chapterName: String,
    val groupPathWord: String?,
    val version: Long,
    val pendingSync: Boolean,
    /** 云端仅返回章节 ID 时保留本机上次阅读的组，供目录无法定位时回退。 */
    val lastReadGroupPathWord: String? = groupPathWord
) {
    fun toBrowseInfo(pathWord: String) = BrowseInfo(null, pathWord, chapterUuid, chapterName)
}

@kotlinx.serialization.Serializable
data class ReadingProgressSnapshot(
    val owner: String? = null,
    val records: Map<String, ReadingProgress> = emptyMap(),
    /** Ready means the read attempt finished; error distinguishes an incomplete migration from success. */
    val isReady: Boolean = false,
    val error: String? = null,
    val loadVersion: Long = 0L
)

fun readingProgressOwner(loggedIn: Boolean, username: String?): String? = when {
    !loggedIn -> "guest"
    !username.isNullOrBlank() -> "user:$username"
    else -> null
}
