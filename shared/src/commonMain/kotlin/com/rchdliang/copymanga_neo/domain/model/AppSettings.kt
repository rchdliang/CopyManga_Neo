package com.rchdliang.copymanga_neo.domain.model

@kotlinx.serialization.Serializable
enum class AppThemeMode { SYSTEM, LIGHT, DARK }

@kotlinx.serialization.Serializable
data class AppSettings(
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val wifiOnlyDownloads: Boolean = false,
    val includePasswordInCredentialExport: Boolean = false,
    val comicImageConcurrency: Int = 3,
    val comicDownloadLimitKiB: Int = 0,
    val blurNovelIllustrationPreviews: Boolean = false,
    /** 只转换作品标题、简介和标签的显示文字。 */
    val simplifyChinese: Boolean = false
)
