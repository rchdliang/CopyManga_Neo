package com.rchdliang.copymanga_neo.domain.model

/** One position per chapter/volume. Novel character offsets are UTF-16 offsets in the raw line. */
@kotlinx.serialization.Serializable
data class LocalReadingRecord(
    val owner: String,
    val kind: String,
    val path: String,
    val chapter: String,
    val name: String,
    val group: String = "default",
    val title: String = "",
    val page: Int = 0,
    val fraction: Double = 0.0,
    val section: String = "",
    val line: Int = 0,
    val character: Int = 0,
    val completed: Boolean = false,
    val updatedAt: Long = kotlin.time.Clock.System.now().toEpochMilliseconds(),
    val cover: String = "",
    val authors: String = "",
    /** 最后记录的位置是否停在章末；回翻后清除，与永久已读标记分开。 */
    val atChapterEnd: Boolean = false,
    /** 云端只提供章节级位置；首次本机阅读会替换为精确位置。 */
    val cloudImported: Boolean = false
) {
    // Length-prefixing prevents delimiter collisions in names and account identifiers.
    val key: String
        get() = com.rchdliang.copymanga_neo.domain.reading.ReadingMergePolicy.key(
            owner,
            kind,
            path,
            chapter
        )
    val bookKey: String
        get() = com.rchdliang.copymanga_neo.domain.reading.ReadingMergePolicy.key(
            owner,
            kind,
            path
        )
}

@kotlinx.serialization.Serializable
data class LocalReadingSnapshot(
    val records: List<LocalReadingRecord> = emptyList(),
    val bookmarks: List<LocalReadingRecord> = emptyList(),
    val deleted: Map<String, Long> = emptyMap(),
    val clearedOwners: Map<String, Long> = emptyMap(),
    val deletedBooks: Map<String, Long> = emptyMap()
)

/** Credential-free atomic identity for reading/cache ownership checks. */
@kotlinx.serialization.Serializable
data class LocalReadingSession(val mode: RequestMode, val owner: String?, val generation: Long)
