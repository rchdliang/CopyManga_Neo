package com.rchdliang.copymanga_neo.domain.model

/** 偏好和本地阅读备份；不包含登录凭据、网络身份或下载文件。 */
@kotlinx.serialization.Serializable
data class SettingsBackup(
    val settings: AppSettings,
    val readerDefaults: ReaderPreferences,
    val homeRank: HomeRankPreferences,
    val localReading: String,
    val readerTools: String,
    val comicPreferences: Map<String, ReaderPreferences>
)
