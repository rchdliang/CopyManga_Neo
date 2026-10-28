package com.rchdliang.copymanga_neo.data.local.portable

import com.rchdliang.copymanga_neo.data.backup.*
import com.rchdliang.copymanga_neo.domain.model.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.*
import kotlinx.serialization.json.*

/** CMP 各平台共用的个人数据格式；不包含网络配置、身份、凭据、下载内容或临时任务。 */
class PortableUserDataBackup(private val storage: DocumentStorage) {
    private val strict = Json { encodeDefaults = true }
    private val files = object : UserDataFiles {
        override suspend fun read(key: String) = storage.read(key)
        override suspend fun write(key: String, value: String?) {
            if (value == null) storage.remove(key) else storage.write(key, value)
        }

        override suspend fun readJournal(): UserDataRestoreJournal? =
            storage.read(JOURNAL)?.let { raw ->
                check(raw.encodeToByteArray().size <= UserDataRestore.MAX_ARCHIVE_BYTES * 3)
                val root = strict.parseToJsonElement(raw).jsonObject
                require(root.keys == setOf("format", "version", "phase", "after", "before"))
                require(root["format"] == JsonPrimitive("copymanga.restore.portable"))
                require(root["version"] == JsonPrimitive(1))
                UserDataRestoreJournal(
                    root.getValue("phase").jsonPrimitive.content,
                    decodeEntries(root.getValue("after")),
                    root.getValue("before").takeUnless { it == JsonNull }?.let(::decodeEntries)
                )
            }

        override suspend fun writeJournal(journal: UserDataRestoreJournal?) {
            if (journal == null) storage.remove(JOURNAL)
            else storage.write(JOURNAL, buildJsonObject {
                put("format", "copymanga.restore.portable"); put("version", 1)
                put("phase", journal.phase)
                put("after", entries(journal.after))
                put("before", journal.before?.let(::entries) ?: JsonNull)
            }.toString())
        }
    }
    private val restore = UserDataRestore(files, serializers.keys, ::validate)
    suspend fun export(): String {
        val values = restore.snapshot()
        return buildJsonObject {
            put("format", "copymanga-neo-user-data"); put("version", 1)
            put("storage", "portable-v1")
            put("entries", entries(values))
        }.toString().also(::checkArchiveSize)
    }

    suspend fun prepare(raw: String) {
        checkArchiveSize(raw)
        val root = strict.parseToJsonElement(raw).jsonObject
        require(root.keys == setOf("format", "version", "storage", "entries"))
        require(
            root["format"] == JsonPrimitive("copymanga-neo-user-data") && root["version"] == JsonPrimitive(
                1
            )
        ) { "不支持的用户备份版本" }
        require(root["storage"] == JsonPrimitive("portable-v1")) { "请选择 CMP 版本导出的用户备份；跨分支可使用设置备份" }
        restore.prepare(decodeEntries(root.getValue("entries")))
    }

    suspend fun recover(): String? = restore.recover()
    suspend fun cancel() = restore.cancel()
    suspend fun isPending() = restore.isPending()

    private fun checkArchiveSize(raw: String) {
        require(raw.encodeToByteArray().size <= UserDataRestore.MAX_ARCHIVE_BYTES) { "用户备份过大" }
    }

    private fun entries(values: Map<String, String?>) = buildJsonObject {
        values.forEach { (key, value) -> put(key, value?.let(::JsonPrimitive) ?: JsonNull) }
    }

    private fun decodeEntries(value: JsonElement): Map<String, String?> {
        val result = value.jsonObject.mapValues { (_, raw) ->
            if (raw == JsonNull) null else (raw as? JsonPrimitive)?.also { require(it.isString) }?.content
                ?: error("备份条目无效")
        }
        val legacyKeys = serializers.keys - "shelf-reading.json"
        require(result.keys == serializers.keys || result.keys == legacyKeys) { "备份数据清单不受支持" }
        // 老备份没有追平证据；恢复时清除当前机器的追更基线，不从最后阅读位置猜测。
        return if (result.keys == legacyKeys) result + ("shelf-reading.json" to null) else result
    }

    private fun validate(values: Map<String, String?>) {
        values.forEach { (key, raw) ->
            if (raw != null) {
                val limit = if (key == "reading-library.json") PortableBackupCodec.MAX_READING_BYTES else 16 * 1024 * 1024
                require(raw.encodeToByteArray().size <= limit) { "单项用户数据过大" }
                val decoded = DocumentEnvelope.decode(raw, "copymanga.document/$key",
                    if (key == "shelf-reading.json") PortableShelfReading.SCHEMA_VERSION else 1,
                    if (key == "shelf-reading.json") PortableShelfReading.migrations else emptyMap())
                val model = strict.decodeFromJsonElement(serializers.getValue(key), decoded.payload)
                when (key) {
                    "comic-reader-settings.json" -> {
                        val preferences = model as ComicPreferenceDocument
                        require(preferences.global == preferences.global.normalized())
                        require(preferences.comics.all { (path, value) -> path.isNotBlank() && value == value.normalized() })
                    }
                    "reading-library.json" -> PortableBackupCodec.validateReading(model as LocalReadingSnapshot)
                    "shelf-reading.json" -> require((model as ShelfReadingSnapshot).records.all { (id, record) ->
                        id == record.key.id && record.key.kind in setOf("comic", "novel") &&
                            record.key.owner.isNotBlank() && record.key.path.isNotBlank() &&
                            record.catalog.distinct().size == record.catalog.size
                    })
                    "novel-reader-settings.json" -> require((model as NovelReaderPreferences) == model.normalized())
                    "reader-tools.json" -> require((model as ReaderToolsSettings) == model.normalized())
                    "novel-annotations.json" -> (model as List<*>).forEach { entry ->
                        val annotation = entry as NovelAnnotation
                        require(
                            annotation.id.isNotBlank() && annotation.owner.isNotBlank() &&
                                annotation.path.isNotBlank() && annotation.volume.isNotBlank() &&
                                annotation.mode in RequestMode.entries.map { it.name } &&
                                annotation.range.valid && annotation.text.isNotBlank() &&
                                annotation.style in setOf("PRIMARY", "SECONDARY", "TERTIARY"))
                    }
                }
            }
        }
    }

    companion object {
        const val JOURNAL = "user-data-restore.json"
        private val serializers: Map<String, KSerializer<*>> = linkedMapOf(
            "app-settings.json" to AppPreferenceDocument.serializer(),
            "comic-reader-settings.json" to ComicPreferenceDocument.serializer(),
            "novel-reader-settings.json" to NovelReaderPreferences.serializer(),
            "novel-illustration-blur.json" to MapSerializer(
                String.serializer(),
                Boolean.serializer()
            ),
            "novel-annotations.json" to ListSerializer(NovelAnnotation.serializer()),
            "reading-library.json" to LocalReadingSnapshot.serializer(),
            "reading-progress.json" to ProgressDocument.serializer(),
            "shelf-reading.json" to ShelfReadingSnapshot.serializer(),
            "reader-tools.json" to ReaderToolsSettings.serializer(),
            "theme-blacklist.json" to BlacklistDocument.serializer()
        )
    }
}
