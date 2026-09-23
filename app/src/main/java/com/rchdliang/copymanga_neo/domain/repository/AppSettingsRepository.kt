package com.rchdliang.copymanga_neo.domain.repository

import com.rchdliang.copymanga_neo.domain.model.AppSettings
import com.rchdliang.copymanga_neo.domain.model.AppThemeMode
import kotlinx.coroutines.flow.Flow

interface AppSettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun setThemeMode(mode: AppThemeMode): Result<Unit>
    suspend fun setWifiOnlyDownloads(enabled: Boolean): Result<Unit>
    suspend fun resetDefaults(): Result<Unit>
}
