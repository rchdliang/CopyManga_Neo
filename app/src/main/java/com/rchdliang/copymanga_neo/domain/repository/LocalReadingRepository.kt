package com.rchdliang.copymanga_neo.domain.repository

import com.rchdliang.copymanga_neo.domain.model.LocalReadingRecord
import com.rchdliang.copymanga_neo.domain.model.LocalReadingSnapshot
import kotlinx.coroutines.flow.StateFlow

/** 账号隔离的本地阅读位置、完成状态和书签；存储与持久化由数据层处理。 */
interface LocalReadingRepository {
    val snapshot: StateFlow<LocalReadingSnapshot>
    val error: StateFlow<String?>
    fun position(owner: String, kind: String, path: String, chapter: String): LocalReadingRecord?
    fun latest(owner: String, kind: String, path: String): LocalReadingRecord?
    fun permitsCoarse(owner: String, kind: String, path: String): Boolean
    suspend fun retryRead(): Result<Unit>
    fun record(value: LocalReadingRecord)
    fun complete(owner: String, path: String, chapter: String)
    fun flushAsync()
    suspend fun flush(): Result<Unit>
    suspend fun toggleBookmark(r: LocalReadingRecord): Result<Unit>
    suspend fun updateMetadata(
        owner: String,
        kind: String,
        path: String,
        title: String,
        cover: String,
        authors: String
    ): Result<Unit>

    suspend fun removeBookmark(r: LocalReadingRecord): Result<Unit>
    suspend fun removeHistory(r: LocalReadingRecord): Result<Unit>
    suspend fun clearHistory(owner: String): Result<Unit>
}
