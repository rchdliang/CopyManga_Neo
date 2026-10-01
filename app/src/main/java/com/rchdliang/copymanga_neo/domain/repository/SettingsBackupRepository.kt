package com.rchdliang.copymanga_neo.domain.repository

interface SettingsBackupRepository {
    suspend fun exportSettings(): Result<String>
    suspend fun importSettings(serialized: String): Result<Unit>
}
