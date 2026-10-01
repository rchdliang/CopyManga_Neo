package com.rchdliang.copymanga_neo.data.repository

import com.rchdliang.copymanga_neo.data.local.SettingsBackupCodec
import com.rchdliang.copymanga_neo.domain.model.AppSettings
import com.rchdliang.copymanga_neo.domain.model.AppThemeMode
import com.rchdliang.copymanga_neo.domain.model.HomeRankPreferences
import com.rchdliang.copymanga_neo.domain.model.ReaderPreferences
import com.rchdliang.copymanga_neo.domain.model.SettingsBackup
import com.rchdliang.copymanga_neo.domain.repository.AppSettingsRepository
import com.rchdliang.copymanga_neo.domain.repository.ReaderPreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsBackupRepositoryTest {
    private val incoming = SettingsBackup(
        AppSettings(AppThemeMode.DARK, true),
        ReaderPreferences("TOP_TO_BOTTOM", "FIT_WIDTH", "PORTRAIT"),
        HomeRankPreferences("female", "month")
    )

    @Test
    fun importsPreferencesWhilePreservingComicSettings() = runBlocking {
        val app = FakeAppSettings()
        val reader = FakeReader()
        val comicBefore = reader.comic.value
        val repository = SettingsBackupRepositoryImpl(app, reader)
        assertTrue(repository.importSettings(SettingsBackupCodec.encode(incoming)).isSuccess)
        assertEquals(incoming.settings, app.settings.value)
        assertEquals(incoming.readerDefaults, reader.globalPreferences.value)
        assertEquals(incoming.homeRank, app.rank)
        assertEquals(comicBefore, reader.comic.value)
    }

    @Test
    fun rejectsInvalidBackupBeforeWritingAnything() = runBlocking {
        val app = FakeAppSettings()
        val reader = FakeReader()
        val repository = SettingsBackupRepositoryImpl(app, reader)
        assertTrue(
            repository.importSettings(
                SettingsBackupCodec.encode(incoming)
                    .replace("FIT_WIDTH", "INVALID")
            ).isFailure
        )
        assertEquals(AppSettings(), app.settings.value)
        assertEquals(ReaderPreferences(), reader.globalPreferences.value)
        assertEquals(HomeRankPreferences(), app.rank)
    }

    @Test
    fun restoresEarlierWritesWhenReaderSaveFails() = runBlocking {
        val app = FakeAppSettings()
        val reader = FakeReader().apply { failNextUpdate = true }
        val repository = SettingsBackupRepositoryImpl(app, reader)
        val result = repository.importSettings(SettingsBackupCodec.encode(incoming))
        assertTrue(result.isFailure)
        assertEquals("导入失败，已恢复原设置", result.exceptionOrNull()?.message)
        assertEquals(AppSettings(), app.settings.value)
        assertEquals(HomeRankPreferences(), app.rank)
        assertEquals(ReaderPreferences(), reader.globalPreferences.value)
    }

    private class FakeAppSettings : AppSettingsRepository {
        override val settings = MutableStateFlow(AppSettings())
        var rank = HomeRankPreferences()
        override suspend fun setThemeMode(mode: AppThemeMode): Result<Unit> {
            settings.value = settings.value.copy(themeMode = mode)
            return Result.success(Unit)
        }

        override suspend fun setWifiOnlyDownloads(enabled: Boolean): Result<Unit> {
            settings.value = settings.value.copy(wifiOnlyDownloads = enabled)
            return Result.success(Unit)
        }

        override suspend fun getHomeRankPreferences() = Result.success(rank)
        override suspend fun setHomeRankPreferences(preferences: HomeRankPreferences): Result<Unit> {
            rank = preferences
            return Result.success(Unit)
        }

        override suspend fun resetDefaults() = Result.success(Unit)
    }

    private class FakeReader : ReaderPreferencesRepository {
        override val globalPreferences = MutableStateFlow(ReaderPreferences())
        val comic = MutableStateFlow(ReaderPreferences("LEFT_TO_RIGHT", "FIT_HEIGHT", "LANDSCAPE"))
        var failNextUpdate = false
        override fun comicPreferences(comicPathWord: String) = comic
        override suspend fun initializeComic(comicPathWord: String) = Result.success(comic.value)
        override suspend fun updateGlobal(transform: (ReaderPreferences) -> ReaderPreferences): Result<Unit> {
            if (failNextUpdate) {
                failNextUpdate = false
                return Result.failure(IllegalStateException("模拟存储写入失败"))
            }
            globalPreferences.value = transform(globalPreferences.value)
            return Result.success(Unit)
        }

        override suspend fun updateComic(
            comicPathWord: String,
            transform: (ReaderPreferences) -> ReaderPreferences
        ): Result<Unit> {
            comic.value = transform(comic.value)
            return Result.success(Unit)
        }

        override suspend fun resetGlobal() = Result.success(Unit)
        override suspend fun resetComic(comicPathWord: String) = Result.success(Unit)
    }
}
