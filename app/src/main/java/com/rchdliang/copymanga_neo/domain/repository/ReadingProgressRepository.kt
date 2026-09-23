package com.rchdliang.copymanga_neo.domain.repository

import com.rchdliang.copymanga_neo.domain.model.BrowseInfo
import com.rchdliang.copymanga_neo.domain.model.ReadingProgressSnapshot
import kotlinx.coroutines.flow.StateFlow

interface ReadingProgressRepository {
    val progress: StateFlow<ReadingProgressSnapshot>
    val currentOwner: String?
    suspend fun awaitOwner(): String
    fun requestVersion(): Long
    fun recordRead(
        owner: String,
        comicPathWord: String,
        chapterUuid: String,
        chapterName: String,
        group: String
    )

    suspend fun acceptRemote(
        owner: String,
        records: Map<String, BrowseInfo?>,
        requestVersion: Long
    ): Result<Unit>

    fun retryPending()
    suspend fun clearHistory(): Result<Unit>
}
