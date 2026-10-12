package com.rchdliang.copymanga_neo.domain.repository

import com.rchdliang.copymanga_neo.domain.model.ReaderToolsSettings
import kotlinx.coroutines.flow.StateFlow

interface ReaderToolsRepository {
    val settings: StateFlow<ReaderToolsSettings>
    suspend fun updateSettings(transform: (ReaderToolsSettings) -> ReaderToolsSettings): Result<Unit>
}
