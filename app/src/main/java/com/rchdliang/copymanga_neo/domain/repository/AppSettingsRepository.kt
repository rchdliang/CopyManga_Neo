package com.rchdliang.copymanga_neo.domain.repository

import com.rchdliang.copymanga_neo.domain.model.AppSettings
import com.rchdliang.copymanga_neo.domain.model.AppThemeMode
import com.rchdliang.copymanga_neo.domain.model.HomeRankPreferences
import kotlinx.coroutines.flow.Flow

interface AppSettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun setThemeMode(mode: AppThemeMode): Result<Unit>
    suspend fun setWifiOnlyDownloads(enabled: Boolean): Result<Unit>

    /** 仅保存是否包含密码的选择，不保存凭据内容。 */
    suspend fun setCredentialExportPassword(enabled: Boolean): Result<Unit>

    suspend fun getHomeRankPreferences(): Result<HomeRankPreferences>
    suspend fun setHomeRankPreferences(preferences: HomeRankPreferences): Result<Unit>

    /** 搜索结果与发现结果分别保存布局偏好。 */
    suspend fun getSearchCompactLayout(keywordSearch: Boolean): Result<Boolean>
    suspend fun setSearchCompactLayout(keywordSearch: Boolean, compact: Boolean): Result<Unit>
    suspend fun resetDefaults(): Result<Unit>
}
