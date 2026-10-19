package com.rchdliang.copymanga_neo.data.backup

import android.content.Context
import android.util.AtomicFile
import androidx.datastore.preferences.core.PreferencesFileSerializer
import com.google.gson.*
import com.rchdliang.copymanga_neo.data.local.*
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

/** Android 个人资料备份；恢复入口必须在 Hilt/DataStore/SharedPreferences 消费者创建之前运行。 */
@Singleton
class AndroidUserDataBackup @Inject constructor(@ApplicationContext private val context: Context) {
    private val journal = AtomicFile(File(context.noBackupFilesDir, "user-data-restore-v1.json"))
    private val files = object : UserDataFiles {
        override suspend fun read(key: String): String? {
            require(key in KEYS)
            if (key in SHARED) {
                val values = context.getSharedPreferences(key, Context.MODE_PRIVATE).all
                if (values.isEmpty()) return null
                return JsonObject().apply {
                    values.forEach { (name, value) ->
                        when (value) {
                            is String -> addProperty(name, value)
                            is Boolean -> addProperty(name, value)
                            is Int -> addProperty(name, value)
                            else -> error("不支持的用户偏好类型")
                        }
                    }
                }.toString()
            }
            val file = dataFile(key)
            if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) return null
            return Base64.getEncoder().encodeToString(readBounded(file, ITEM_LIMIT))
        }

        override suspend fun write(key: String, value: String?) {
            require(key in KEYS)
            if (key in SHARED) {
                val editor = context.getSharedPreferences(key, Context.MODE_PRIVATE).edit().clear()
                value?.let {
                    JsonParser.parseString(it).asJsonObject.entrySet().forEach { (name, item) ->
                        val primitive = item.asJsonPrimitive
                        when {
                            primitive.isString -> editor.putString(name, primitive.asString)
                            primitive.isBoolean -> editor.putBoolean(name, primitive.asBoolean)
                            else -> editor.putInt(name, primitive.asInt)
                        }
                    }
                }
                check(editor.commit()) { "恢复用户偏好失败" }
            } else {
                val file = dataFile(key)
                if (value == null) {
                    file.delete()
                    check(!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists())
                } else atomicWrite(file, Base64.getDecoder().decode(value))
            }
        }

        override suspend fun readJournal(): UserDataRestoreJournal? {
            if (!journal.baseFile.exists() && !File(journal.baseFile.path + ".bak").exists()) return null
            val root = JsonParser.parseString(
                readBounded(
                    journal,
                    UserDataRestore.MAX_ARCHIVE_BYTES * 3
                ).toString(Charsets.UTF_8)
            ).asJsonObject
            require(root.keySet() == setOf("format", "version", "phase", "after", "before"))
            require(
                root["format"] == JsonPrimitive("copymanga.restore.android") && root["version"] == JsonPrimitive(
                    1
                )
            )
            return UserDataRestoreJournal(
                root["phase"].asString, decodeEntries(root["after"]),
                root["before"].takeUnless { it.isJsonNull }?.let(::decodeEntries)
            )
        }

        override suspend fun writeJournal(value: UserDataRestoreJournal?) {
            if (value == null) {
                journal.delete()
                check(!journal.baseFile.exists() && !File(journal.baseFile.path + ".bak").exists())
            } else atomicWrite(journal, JsonObject().apply {
                addProperty("format", "copymanga.restore.android"); addProperty("version", 1)
                addProperty("phase", value.phase)
                add("after", entries(value.after)); add(
                "before",
                value.before?.let(::entries) ?: JsonNull.INSTANCE
            )
            }.toString().toByteArray(Charsets.UTF_8))
        }
    }
    private val restore = UserDataRestore(files, KEYS, ::validate)
    suspend fun export(): String = JsonObject().apply {
        addProperty("format", "copymanga-neo-user-data"); addProperty("version", 1)
        addProperty("storage", "android-v1"); add("entries", entries(restore.snapshot()))
    }.toString().also(::checkSize)

    suspend fun prepare(raw: String) {
        checkSize(raw)
        val root = JsonParser.parseString(raw).asJsonObject
        require(root.keySet() == setOf("format", "version", "storage", "entries"))
        require(
            root["format"] == JsonPrimitive("copymanga-neo-user-data") && root["version"] == JsonPrimitive(
                1
            )
        ) { "不支持的用户备份版本" }
        require(root["storage"] == JsonPrimitive("android-v1")) { "请选择 Android 原版导出的用户备份；跨分支可使用设置备份" }
        restore.prepare(decodeEntries(root["entries"]))
    }

    suspend fun recover(): String? = restore.recover()
    suspend fun cancel() = restore.cancel()
    suspend fun isPending() = restore.isPending()

    private fun dataFile(key: String) =
        AtomicFile(File(context.filesDir, "datastore/$key.preferences_pb"))

    private fun checkSize(raw: String) =
        require(raw.toByteArray(Charsets.UTF_8).size <= UserDataRestore.MAX_ARCHIVE_BYTES) { "用户备份过大" }

    private fun entries(values: Map<String, String?>) = JsonObject().apply {
        values.forEach { (key, raw) -> add(key, raw?.let(::JsonPrimitive) ?: JsonNull.INSTANCE) }
    }

    private fun decodeEntries(raw: JsonElement): Map<String, String?> {
        val result = raw.asJsonObject.entrySet().associate { (key, value) ->
            key to if (value.isJsonNull) null else {
                require(value.isJsonPrimitive && value.asJsonPrimitive.isString)
                value.asString
            }
        }
        require(result.keys == KEYS) { "备份数据清单不受支持" }
        return result
    }

    private suspend fun validate(values: Map<String, String?>) {
        values.forEach { (file, raw) ->
            if (raw == null) return@forEach
            require(raw.toByteArray(Charsets.UTF_8).size <= ITEM_LIMIT * 2)
            if (file in SHARED) {
                val fields = JsonParser.parseString(raw).asJsonObject
                if (file == "local_reading_library") {
                    require(fields.keySet().all { it == "data" })
                    fields["data"]?.let {
                        require(it.isJsonPrimitive && it.asJsonPrimitive.isString); LocalReadingCodec.decode(
                        it.asString
                    )
                    }
                } else fields.entrySet().forEach { (name, value) ->
                    require(value.isJsonPrimitive)
                    if (name in TOOL_BOOLEANS) require(value.asJsonPrimitive.isBoolean)
                    else {
                        require(name in TOOL_INTS && value.asJsonPrimitive.isNumber)
                        require(value.asString.toIntOrNull() != null)
                    }
                }
            } else {
                val bytes = Base64.getDecoder().decode(raw)
                require(bytes.size <= ITEM_LIMIT)
                val prefs = PreferencesFileSerializer.readFrom(bytes.inputStream())
                prefs.asMap().forEach { (key, value) -> validatePreference(file, key.name, value) }
            }
        }
    }

    private fun validatePreference(file: String, name: String, value: Any) {
        when (file) {
            "app_settings" -> when (name) {
                in APP_STRINGS -> require(value is String)
                in APP_INTS -> require(value is Int)
                in APP_BOOLEANS -> require(value is Boolean)
                else -> error("用户偏好版本不受支持")
            }

            "reader_prefs" -> {
                require(value is String)
                if (name.startsWith("comic_preferences:")) ComicPreferencesCodec.decode(value)
                else require(name in setOf("reading_direction", "page_scale", "screen_orientation"))
            }

            "novel_reader_preferences" -> when {
                name in setOf("font_size", "line_height", "paragraph_spacing", "margin") -> require(
                    value is Float && value.isFinite()
                )

                name in setOf("font", "mode", "columns", "theme") -> require(value is String)
                name == "keep_screen_on" || name.startsWith("illustration_blur:") -> require(value is Boolean)
                else -> error("小说偏好版本不受支持")
            }

            "novel_reader_annotations_v1" -> {
                require(name == "annotations" && value is String)
                NovelAnnotationCodec().decode(value)
            }

            "theme_blacklist" -> if (name == "enabled") require(value is Boolean) else {
                require(name == "blocked_themes" && value is String)
                JsonParser.parseString(value).asJsonArray.forEach { entry ->
                    require(entry.asJsonObject["name"].asJsonPrimitive.isString)
                    entry.asJsonObject["path_word"]?.takeUnless { it.isJsonNull }
                        ?.let { require(it.asJsonPrimitive.isString) }
                }
            }

            "reading_progress" -> if (name.startsWith("cleared:")) require(value is Long) else {
                require(name.startsWith("owner:") && value is String)
                JsonParser.parseString(value).asJsonObject.entrySet().forEach { (_, item) ->
                    val entry = item.asJsonObject
                    require(entry["chapter"].asJsonPrimitive.isString && entry["name"].asJsonPrimitive.isString)
                    require(entry["pending"].asJsonPrimitive.isBoolean)
                    require(entry["version"].asJsonPrimitive.isNumber && entry["version"].asString.toLongOrNull() != null)
                    listOf(
                        "group",
                        "last_read_group"
                    ).forEach { k ->
                        entry[k]?.takeUnless { it.isJsonNull }
                            ?.let { require(it.asJsonPrimitive.isString) }
                    }
                }
            }
        }
    }

    companion object {
        private const val ITEM_LIMIT = 16 * 1024 * 1024
        private val SHARED = setOf("local_reading_library", "reader_tools")
        private val KEYS = linkedSetOf(
            "app_settings",
            "reader_prefs",
            "novel_reader_preferences",
            "novel_reader_annotations_v1",
            "reading_progress",
            "theme_blacklist",
            "local_reading_library",
            "reader_tools"
        )
        private val TOOL_BOOLEANS = setOf("inline_comments", "continuous_chapters", "volume_keys")
        private val TOOL_INTS =
            setOf("auto_interval", "image_retries", "image_timeout", "preload_pages")
        private val APP_STRINGS = setOf("theme_mode", "home_rank_audience", "home_rank_period")
        private val APP_INTS = setOf("comic_image_concurrency", "comic_download_limit_kib")
        private val APP_BOOLEANS = setOf(
            "wifi_only_downloads",
            "blur_novel_illustration_previews",
            "include_password_in_credential_export",
            "simplify_chinese",
            "chinese_default_initialized",
            "search_compact_layout",
            "discovery_compact_layout"
        )

        private fun readBounded(file: AtomicFile, limit: Int): ByteArray = file.openRead().use {
            require(it.channel.size() <= limit) { "用户数据文件过大" }
            it.readBytes().also { bytes -> require(bytes.size <= limit) }
        }

        private fun atomicWrite(file: AtomicFile, bytes: ByteArray) {
            val output = file.startWrite()
            try {
                output.write(bytes); output.fd.sync(); file.finishWrite(output)
            } catch (failure: Exception) {
                file.failWrite(output); throw failure
            }
            check(readBounded(file, bytes.size).contentEquals(bytes)) { "用户数据提交未确认" }
        }
    }
}
