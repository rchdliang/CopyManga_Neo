package com.rchdliang.copymanga_neo.data.local.portable

import com.rchdliang.copymanga_neo.domain.model.*
import com.rchdliang.copymanga_neo.domain.repository.SettingsBackupRepository
import kotlinx.serialization.json.*

/** Keeps the Android version-2 backup interchange schema, independent of platform storage. */
object PortableBackupCodec {
    const val MAX_READING_BYTES = 8 * 1024 * 1024
    private fun JsonObject.string(key: String): String = (this[key] as? JsonPrimitive)?.also { require(it.isString) }?.content ?: error("备份缺少字段：$key")
    private fun JsonObject.integer(key: String): Int = (this[key] as? JsonPrimitive)?.also { require(!it.isString) }?.intOrNull ?: error("备份整数字段无效：$key")
    private fun JsonObject.boolean(key: String): Boolean = (this[key] as? JsonPrimitive)?.also { require(!it.isString) }?.booleanOrNull ?: error("备份布尔字段无效：$key")
    private fun JsonObject.long(key: String): Long = (this[key] as? JsonPrimitive)?.also { require(!it.isString) }?.longOrNull ?: error("备份时间无效：$key")
    fun reading(snapshot: LocalReadingSnapshot): JsonObject = buildJsonObject {
        put("version", 1)
        fun record(r: LocalReadingRecord): JsonElement {
            val encoded = storageJson.encodeToJsonElement(LocalReadingRecord.serializer(), r).jsonObject
            return JsonObject(encoded - "updatedAt" + ("at" to JsonPrimitive(r.updatedAt)))
        }
        put("records", JsonArray(snapshot.records.map(::record)))
        put("bookmarks", JsonArray(snapshot.bookmarks.map(::record)))
        put("deleted", JsonObject(snapshot.deleted.mapValues { JsonPrimitive(it.value) }))
        put("clearedOwners", JsonObject(snapshot.clearedOwners.mapValues { JsonPrimitive(it.value) }))
        put("deletedBooks", JsonObject(snapshot.deletedBooks.mapValues { JsonPrimitive(it.value) }))
    }
    fun reading(raw: JsonObject): LocalReadingSnapshot {
        require(raw.toString().encodeToByteArray().size <= MAX_READING_BYTES) { "阅读备份过大" }
        require(raw.integer("version") == 1) { "不支持的阅读数据版本" }
        fun record(element: JsonElement): LocalReadingRecord {
            val o = element.jsonObject
            val fraction = (o["fraction"] as? JsonPrimitive)?.also { require(!it.isString) }?.doubleOrNull ?: error("阅读位置无效")
            val r = LocalReadingRecord(o.string("owner"), o.string("kind"), o.string("path"), o.string("chapter"), o.string("name"),
                o.string("group"), o.string("title"), o.integer("page"), fraction, o.string("section"), o.integer("line"), o.integer("character"),
                o.boolean("completed"), o.long("at"), o.string("cover"), o.string("authors"),
                atChapterEnd = if ("atChapterEnd" in o) o.boolean("atChapterEnd") else false)
            return r
        }
        fun times(key: String) = raw.getValue(key).jsonObject.mapValues { (_, value) ->
            val primitive = value.jsonPrimitive
            require(!primitive.isString)
            (primitive.longOrNull ?: error("阅读删除标记无效")).also { require(it in 0..253402300799999L) }
        }
        val records = raw.getValue("records").jsonArray.map(::record)
        val marks = raw.getValue("bookmarks").jsonArray.map(::record)
        return LocalReadingSnapshot(records, marks, times("deleted"), times("clearedOwners"), times("deletedBooks")).also(::validateReading)
    }
    /** Shared by imported backups and the native reading document before publication. */
    fun validateReading(snapshot: LocalReadingSnapshot) {
        val records = snapshot.records
        val marks = snapshot.bookmarks
        require(records.size + marks.size <= 50000 && records.distinctBy { it.key }.size == records.size)
        require(marks.distinctBy { listOf(it.key, it.page, it.section, it.line, it.character) }.size == marks.size)
        (records + marks).forEach { r ->
            require(r.owner.isNotBlank() && r.path.isNotBlank() && r.chapter.isNotBlank())
            require(r.kind in setOf("comic", "novel") && r.page >= 0 && r.line >= 0 && r.character >= 0)
            require(r.fraction.isFinite() && r.fraction in 0.0..1.0 && r.updatedAt in 0..253402300799999L)
            require(listOf(r.owner, r.path, r.chapter, r.name, r.title, r.section, r.group, r.cover, r.authors).all { it.length <= 8192 })
        }
        listOf(snapshot.deleted, snapshot.clearedOwners, snapshot.deletedBooks).forEach { times ->
            require(times.values.all { it in 0..253402300799999L }) { "阅读删除标记无效" }
        }
    }
    fun tools(value: ReaderToolsSettings): JsonObject = buildJsonObject {
        put("inlineComments", value.inlineComments); put("continuousChapters", value.continuousChapters); put("volumeKeys", value.volumeKeys)
        put("autoInterval", value.autoIntervalSeconds); put("imageRetries", value.imageRetries); put("imageTimeout", value.imageTimeoutSeconds); put("preload", value.preloadPages)
    }
    fun tools(value: JsonObject): ReaderToolsSettings = ReaderToolsSettings(value.boolean("inlineComments"), value.boolean("continuousChapters"), value.boolean("volumeKeys"),
        value.integer("autoInterval"), value.integer("imageRetries"), value.integer("imageTimeout"), value.integer("preload")).also { require(it == it.normalized()) }
    fun encode(value: SettingsBackup): String = buildJsonObject {
        put("format", "copymanga-neo-settings"); put("version", 2)
        put("settings", buildJsonObject { put("themeMode", value.settings.themeMode.name); put("wifiOnlyDownloads", value.settings.wifiOnlyDownloads); put("comicImageConcurrency", value.settings.comicImageConcurrency); put("comicDownloadLimitKiB", value.settings.comicDownloadLimitKiB) })
        put("readerDefaults", storageJson.encodeToJsonElement(ReaderPreferences.serializer(), value.readerDefaults))
        put("homeRank", storageJson.encodeToJsonElement(HomeRankPreferences.serializer(), value.homeRank))
        put("localReading", storageJson.parseToJsonElement(value.localReading))
        put("readerTools", storageJson.parseToJsonElement(value.readerTools))
        put("comicPreferences", buildJsonObject { value.comicPreferences.forEach { (path, p) ->
            require(path.isNotBlank() && p == p.normalized())
            put(path, buildJsonObject { put("direction", p.readingDirection); put("scale", p.pageScale); put("orientation", p.screenOrientation) })
        } })
    }.toString().also { require(it.encodeToByteArray().size <= SettingsBackupRepository.MAX_BYTES) { "设置文件过大" } }
    fun decode(raw: String): SettingsBackup {
        require(raw.encodeToByteArray().size <= SettingsBackupRepository.MAX_BYTES) { "设置文件过大" }
        val o = storageJson.parseToJsonElement(raw).jsonObject
        require(o.string("format") == "copymanga-neo-settings" && o.integer("version") == 2) { "不支持的设置备份" }
        val app = o.getValue("settings").jsonObject
        val reader = o.getValue("readerDefaults").jsonObject.let { ReaderPreferences(it.string("readingDirection"), it.string("pageScale"), it.string("screenOrientation")) }
        require(reader == reader.normalized())
        val rank = o.getValue("homeRank").jsonObject.let { HomeRankPreferences(it.string("audience"), it.string("period")) }
        require(rank == rank.normalized())
        val local = o.getValue("localReading").jsonObject.also { reading(it) }
        val tools = o.getValue("readerTools").jsonObject.also { tools(it) }
        val comics = o.getValue("comicPreferences").jsonObject.mapValues { (path, v) ->
            require(path.isNotBlank())
            v.jsonObject.let { ReaderPreferences(it.string("direction"), it.string("scale"), it.string("orientation")) }.also { require(it == it.normalized()) }
        }
        val concurrency = if ("comicImageConcurrency" in app) app.integer("comicImageConcurrency") else 3
        val limit = if ("comicDownloadLimitKiB" in app) app.integer("comicDownloadLimitKiB") else 0
        require(concurrency in 1..10 && limit >= 0) { "下载设置无效" }
        return SettingsBackup(AppSettings(AppThemeMode.valueOf(app.string("themeMode")), app.boolean("wifiOnlyDownloads"), comicImageConcurrency = concurrency, comicDownloadLimitKiB = limit), reader, rank, local.toString(), tools.toString(), comics)
    }
}
