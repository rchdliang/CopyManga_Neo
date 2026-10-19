package com.rchdliang.copymanga_neo.domain.repository

interface SettingsBackupRepository {
    companion object {
        const val MAX_BYTES = 12 * 1024 * 1024
        const val USER_DATA_MAX_BYTES = 48 * 1024 * 1024
    }
    suspend fun exportSettings(): Result<String>
    suspend fun importSettings(serialized: String): Result<Unit>
    suspend fun exportUserData(): Result<String> = Result.failure(UnsupportedOperationException())
    suspend fun importUserData(serialized: String): Result<Unit> = Result.failure(UnsupportedOperationException())
    suspend fun hasPendingUserDataRestore(): Boolean = false
    suspend fun cancelUserDataRestore(): Result<Unit> = Result.failure(UnsupportedOperationException())
}
