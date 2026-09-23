package com.rchdliang.copymanga_neo.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.rchdliang.copymanga_neo.domain.model.AppSettings
import com.rchdliang.copymanga_neo.domain.model.AppThemeMode
import com.rchdliang.copymanga_neo.domain.repository.AppSettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.distinctUntilChanged
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

    override val settings = store.data.map { prefs ->
        AppSettings(
            themeMode = AppThemeMode.entries.firstOrNull { it.name == prefs[themeKey] }
                ?: AppThemeMode.SYSTEM,
            wifiOnlyDownloads = prefs[wifiOnlyKey] ?: false
        )
    }.distinctUntilChanged()

    override suspend fun setThemeMode(mode: AppThemeMode) = save {
        store.edit { it[themeKey] = mode.name }
    }

    override suspend fun setWifiOnlyDownloads(enabled: Boolean) = save {
        store.edit { it[wifiOnlyKey] = enabled }
    }

    override suspend fun resetDefaults() = save {
        store.edit {
            it.remove(themeKey)
            it.remove(wifiOnlyKey)
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
