package com.rchdliang.copymanga_neo.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.rchdliang.copymanga_neo.domain.model.AppSettings
import com.rchdliang.copymanga_neo.domain.model.AppThemeMode
import com.rchdliang.copymanga_neo.domain.model.HomeRankPreferences
import com.rchdliang.copymanga_neo.domain.repository.AppSettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.appSettingsDataStore by preferencesDataStore(name = "app_settings")

@Singleton
class AppSettingsRepositoryImpl @Inject constructor(
    @ApplicationContext context: Context
) : AppSettingsRepository {
    private val store = context.appSettingsDataStore
    private val themeKey = stringPreferencesKey("theme_mode")
    private val wifiOnlyKey = booleanPreferencesKey("wifi_only_downloads")
    private val imageConcurrencyKey = intPreferencesKey("comic_image_concurrency")
    private val downloadLimitKey = intPreferencesKey("comic_download_limit_kib")
    private val exportPasswordKey = booleanPreferencesKey("include_password_in_credential_export")
    private val rankAudienceKey = stringPreferencesKey("home_rank_audience")
    private val rankPeriodKey = stringPreferencesKey("home_rank_period")

    override val settings = store.data.map { prefs ->
        AppSettings(
            themeMode = AppThemeMode.entries.firstOrNull { it.name == prefs[themeKey] }
                ?: AppThemeMode.SYSTEM,
            wifiOnlyDownloads = prefs[wifiOnlyKey] ?: false,
            includePasswordInCredentialExport = prefs[exportPasswordKey] ?: false,
            comicImageConcurrency = (prefs[imageConcurrencyKey] ?: 3).coerceIn(1, 10),
            comicDownloadLimitKiB = (prefs[downloadLimitKey] ?: 0).coerceAtLeast(0)
        )
    }.distinctUntilChanged()

    override suspend fun setThemeMode(mode: AppThemeMode) = save {
        store.edit { it[themeKey] = mode.name }
    }

    override suspend fun setWifiOnlyDownloads(enabled: Boolean) = save {
        store.edit { it[wifiOnlyKey] = enabled }
    }

    override suspend fun setCredentialExportPassword(enabled: Boolean) = save {
        store.edit { it[exportPasswordKey] = enabled }
    }

    override suspend fun setComicImageConcurrency(count: Int) = save {
        require(count in 1..10) { "并发数需为 1–10" }
        store.edit { it[imageConcurrencyKey] = count }
    }

    override suspend fun setComicDownloadLimitKiB(limit: Int) = save {
        require(limit >= 0) { "限速不能为负数" }
        store.edit { it[downloadLimitKey] = limit }
    }

    override suspend fun getHomeRankPreferences(): Result<HomeRankPreferences> = try {
        val prefs = store.data.first()
        Result.success(
            HomeRankPreferences(
                audience = prefs[rankAudienceKey] ?: "male",
                period = prefs[rankPeriodKey] ?: "day"
            ).normalized()
        )
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    override suspend fun setHomeRankPreferences(preferences: HomeRankPreferences) = save {
        val value = preferences.normalized()
        // 同一次编辑保存受众与周期，避免重启后读到混合组合。
        store.edit {
            it[rankAudienceKey] = value.audience
            it[rankPeriodKey] = value.period
        }
    }

    private fun searchLayoutKey(keywordSearch: Boolean) =
        booleanPreferencesKey(if (keywordSearch) "search_compact_layout" else "discovery_compact_layout")

    override suspend fun getSearchCompactLayout(keywordSearch: Boolean): Result<Boolean> = try {
        Result.success(store.data.first()[searchLayoutKey(keywordSearch)] ?: false)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    override suspend fun setSearchCompactLayout(keywordSearch: Boolean, compact: Boolean) = save {
        store.edit { it[searchLayoutKey(keywordSearch)] = compact }
    }

    override suspend fun resetDefaults() = save {
        store.edit {
            it.remove(themeKey)
            it.remove(wifiOnlyKey)
            it.remove(imageConcurrencyKey)
            it.remove(downloadLimitKey)
            it.remove(exportPasswordKey)
            it.remove(rankAudienceKey)
            it.remove(rankPeriodKey)
            it.remove(searchLayoutKey(true))
            it.remove(searchLayoutKey(false))
        }
    }

    private suspend fun save(block: suspend () -> Unit): Result<Unit> = try {
        block()
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }
}
