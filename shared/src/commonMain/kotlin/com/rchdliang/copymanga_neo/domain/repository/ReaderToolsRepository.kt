package com.rchdliang.copymanga_neo.domain.repository

import com.rchdliang.copymanga_neo.domain.model.ReaderToolsSettings
import kotlinx.coroutines.flow.StateFlow

interface ReaderToolsRepository {
    val settings: StateFlow<ReaderToolsSettings>
    /** 阅读器和备份在使用同步快照前等待持久化设置，不依赖首页预热。 */
    suspend fun awaitReady(): Result<Unit> = Result.success(Unit)
    suspend fun updateSettings(transform: (ReaderToolsSettings) -> ReaderToolsSettings): Result<Unit>
}
