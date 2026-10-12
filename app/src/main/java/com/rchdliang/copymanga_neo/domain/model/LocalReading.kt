package com.rchdliang.copymanga_neo.domain.model

/** One position per chapter/volume. Novel character offsets are UTF-16 offsets in the raw line. */
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
    val updatedAt: Long = System.currentTimeMillis(),
    val cover: String = "",
    val authors: String = ""
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

data class LocalReadingSnapshot(
    val records: List<LocalReadingRecord> = emptyList(),
    val bookmarks: List<LocalReadingRecord> = emptyList(),
    val deleted: Map<String, Long> = emptyMap(),
    val clearedOwners: Map<String, Long> = emptyMap(),
    val deletedBooks: Map<String, Long> = emptyMap()
)

/** Credential-free atomic identity for reading/cache ownership checks. */
data class LocalReadingSession(val mode: RequestMode, val owner: String?, val generation: Long)
