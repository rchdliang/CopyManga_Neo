package com.rchdliang.copymanga_neo.domain.model

enum class AppThemeMode { SYSTEM, LIGHT, DARK }

data class AppSettings(
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val wifiOnlyDownloads: Boolean = false,
    val includePasswordInCredentialExport: Boolean = false
)
