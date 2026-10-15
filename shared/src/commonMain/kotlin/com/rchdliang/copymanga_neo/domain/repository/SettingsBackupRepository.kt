package com.rchdliang.copymanga_neo.domain.repository

interface SettingsBackupRepository {
    companion object {
        const val MAX_BYTES = 12 * 1024 * 1024
    }
    suspend fun exportSettings(): Result<String>
    suspend fun importSettings(serialized: String): Result<Unit>
}
