package com.rchdliang.copymanga_neo.domain.repository

import com.rchdliang.copymanga_neo.domain.model.ReaderBookmark
import com.rchdliang.copymanga_neo.domain.model.ReaderToolsSettings
import kotlinx.coroutines.flow.StateFlow

interface ReaderToolsRepository {
    val settings: StateFlow<ReaderToolsSettings>
    val bookmarks: StateFlow<List<ReaderBookmark>>
    suspend fun updateSettings(transform: (ReaderToolsSettings) -> ReaderToolsSettings): Result<Unit>
    suspend fun toggleBookmark(bookmark: ReaderBookmark): Result<Unit>
}
