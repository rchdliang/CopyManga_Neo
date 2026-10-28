package com.rchdliang.copymanga_neo.domain.repository

import com.rchdliang.copymanga_neo.domain.model.BrowseInfo
import com.rchdliang.copymanga_neo.domain.model.ReadingProgressSnapshot
import kotlinx.coroutines.flow.StateFlow

interface ReadingProgressRepository {
    val progress: StateFlow<ReadingProgressSnapshot>
    val currentOwner: String?
    suspend fun awaitOwner(): String
    suspend fun awaitReady(owner: String, minimumLoadVersion: Long = 0L): ReadingProgressSnapshot
    fun retryLoading(): Long
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
        requestVersion: Long,
        onlyMissing: Boolean = false
    ): Result<Unit>

    fun retryPending()
    suspend fun clearHistory(): Result<Unit>
}
