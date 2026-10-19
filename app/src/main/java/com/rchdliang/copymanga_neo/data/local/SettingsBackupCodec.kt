package com.rchdliang.copymanga_neo.data.local

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.rchdliang.copymanga_neo.domain.model.AppSettings
import com.rchdliang.copymanga_neo.domain.model.AppThemeMode
import com.rchdliang.copymanga_neo.domain.model.HomeRankPreferences
import com.rchdliang.copymanga_neo.domain.model.ReaderPreferences
import com.rchdliang.copymanga_neo.domain.model.SettingsBackup

/** 先校验完整文件，再允许写入；拒绝未知版本、缺失字段与非法枚举。 */
internal object SettingsBackupCodec {
    const val MAX_BYTES =
        com.rchdliang.copymanga_neo.domain.repository.SettingsBackupRepository.MAX_BYTES

    fun encode(value: SettingsBackup): String = JsonObject().apply {
        addProperty("format", "copymanga-neo-settings")
        addProperty("version", 2)
        add("settings", JsonObject().apply {
            addProperty("themeMode", value.settings.themeMode.name)
            addProperty("wifiOnlyDownloads", value.settings.wifiOnlyDownloads)
            addProperty(
                "blurNovelIllustrationPreviews",
                value.settings.blurNovelIllustrationPreviews
            )
            addProperty("comicImageConcurrency", value.settings.comicImageConcurrency)
            addProperty("comicDownloadLimitKiB", value.settings.comicDownloadLimitKiB)
        })
        add("readerDefaults", JsonObject().apply {
            addProperty("readingDirection", value.readerDefaults.readingDirection)
            addProperty("pageScale", value.readerDefaults.pageScale)
            addProperty("screenOrientation", value.readerDefaults.screenOrientation)
        })
        add("homeRank", JsonObject().apply {
            addProperty("audience", value.homeRank.audience)
            addProperty("period", value.homeRank.period)
        })
        add("localReading", JsonParser.parseString(value.localReading))
        add("readerTools", JsonParser.parseString(value.readerTools))
        add("comicPreferences", JsonObject().apply {
            value.comicPreferences.forEach { (path, preferences) ->
                require(path.isNotBlank()) { "漫画标识为空" }
                add(path, JsonParser.parseString(ComicPreferencesCodec.encode(preferences)))
            }
        })
    }.toString()
        .also { require(it.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "设置文件过大" } }

    fun decode(serialized: String): SettingsBackup {
        require(serialized.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "设置文件过大" }
        val root = JsonParser.parseString(serialized).asJsonObject
        require(root.string("format") == "copymanga-neo-settings") { "不是软件设置备份" }
        val version = root.get("version")
        require(
            version?.isJsonPrimitive == true && version.asJsonPrimitive.isNumber &&
                    version.asString == "2"
        ) { "不支持的备份版本" }
        val settings = root.getAsJsonObject("settings")
        val wifiOnly = settings.get("wifiOnlyDownloads")
        require(wifiOnly?.isJsonPrimitive == true && wifiOnly.asJsonPrimitive.isBoolean)
        val reader = root.getAsJsonObject("readerDefaults")
        val defaults = ReaderPreferences(
            reader.string("readingDirection"),
            reader.string("pageScale"),
            reader.string("screenOrientation")
        )
        require(defaults == defaults.normalized()) { "阅读设置无效" }
        val rank = root.getAsJsonObject("homeRank")
        val homeRank = HomeRankPreferences(rank.string("audience"), rank.string("period"))
        require(homeRank == homeRank.normalized()) { "排行榜设置无效" }
        return SettingsBackup(
            AppSettings(
                AppThemeMode.valueOf(settings.string("themeMode")), wifiOnly.asBoolean,
                blurNovelIllustrationPreviews = settings.get("blurNovelIllustrationPreviews")?.let {
                    require(it.isJsonPrimitive && it.asJsonPrimitive.isBoolean) { "插图设置无效" }
                    it.asBoolean
                } ?: false,
                comicImageConcurrency = settings.optionalInt("comicImageConcurrency", 3, 1..10),
                comicDownloadLimitKiB = settings.optionalInt(
                    "comicDownloadLimitKiB",
                    0,
                    0..Int.MAX_VALUE
                )
            ),
            defaults, homeRank,
            root.getAsJsonObject("localReading").toString().also { LocalReadingCodec.decode(it) },
            root.getAsJsonObject("readerTools").toString()
                .also { ReaderToolsBackupCodec.decode(it) },
            root.getAsJsonObject("comicPreferences").entrySet().associate { (path, value) ->
                require(path.isNotBlank()) { "漫画标识为空" }
                path to ComicPreferencesCodec.decode(value.toString())
            }
        )
    }

    private fun JsonObject.string(key: String): String {
        val value = get(key)
        require(value?.isJsonPrimitive == true && value.asJsonPrimitive.isString) { "备份缺少有效字段" }
        return value.asString
    }

    /** 兼容未包含下载偏好的旧备份；新字段存在时严格校验。 */
    private fun JsonObject.optionalInt(key: String, default: Int, range: IntRange): Int {
        val raw = get(key) ?: return default
        require(raw.isJsonPrimitive && raw.asJsonPrimitive.isNumber) { "下载设置无效" }
        val value = raw.asString.toIntOrNull()
        require(value != null && value in range) { "下载设置无效" }
        return value
    }
}
