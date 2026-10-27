package com.rchdliang.copymanga_neo.domain.model

enum class AppThemeMode { SYSTEM, LIGHT, DARK }

data class AppSettings(
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val wifiOnlyDownloads: Boolean = false,
    val includePasswordInCredentialExport: Boolean = false,
    val comicImageConcurrency: Int = 3,
    /** 漫画图片共享总限速，单位 KiB/s；0 表示不限速。 */
    val comicDownloadLimitKiB: Int = 0,
    val blurNovelIllustrationPreviews: Boolean = false,
    /** 只转换作品标题、简介和标签的显示文字。 */
    val simplifyChinese: Boolean = false,
    val autoCheckUpdates: Boolean = true
)
