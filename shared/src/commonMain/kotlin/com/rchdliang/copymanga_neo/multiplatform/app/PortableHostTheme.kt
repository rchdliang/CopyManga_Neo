package com.rchdliang.copymanga_neo.multiplatform.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rchdliang.copymanga_neo.domain.model.AppSettings
import com.rchdliang.copymanga_neo.domain.model.AppThemeMode
import com.rchdliang.copymanga_neo.domain.repository.AppSettingsRepository
import com.rchdliang.copymanga_neo.multiplatform.ui.theme.CopyManga_NeoTheme
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf

/** Host-owned warnings, startup and close dialogs follow the application's selected palette too. */
@Composable
fun PortableHostTheme(repository: AppSettingsRepository?, content: @Composable () -> Unit) {
    val settings = remember(repository) {
        (repository?.settings ?: flowOf(AppSettings())).catch { emit(AppSettings()) }
    }
    val preferences by settings.collectAsStateWithLifecycle(AppSettings())
    val dark = when (preferences.themeMode) {
        AppThemeMode.DARK -> true
        AppThemeMode.LIGHT -> false
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    CopyManga_NeoTheme(darkTheme = dark, dynamicColor = false) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground, content = content)
    }
}
