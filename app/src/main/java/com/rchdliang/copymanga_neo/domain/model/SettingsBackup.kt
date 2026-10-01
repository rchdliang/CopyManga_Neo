package com.rchdliang.copymanga_neo.domain.model

/** 软件偏好备份；不包含账号、网络身份及漫画数据。 */
data class SettingsBackup(
    val settings: AppSettings,
    val readerDefaults: ReaderPreferences,
    val homeRank: HomeRankPreferences
)
