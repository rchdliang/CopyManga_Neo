package com.rchdliang.copymanga_neo.domain.model

@kotlinx.serialization.Serializable
enum class AppThemeMode { SYSTEM, LIGHT, DARK }

@kotlinx.serialization.Serializable
data class AppSettings(
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val wifiOnlyDownloads: Boolean = false,
    val includePasswordInCredentialExport: Boolean = false
)
