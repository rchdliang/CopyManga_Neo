package com.rchdliang.copymanga_neo.data.local

import android.util.Log
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.rchdliang.copymanga_neo.data.local.db.ComicThemeCacheDao
import com.rchdliang.copymanga_neo.data.local.entity.ComicThemeCacheEntity
import com.rchdliang.copymanga_neo.domain.model.CachedComicThemes
import com.rchdliang.copymanga_neo.domain.model.Theme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import javax.inject.Inject

interface ComicThemeCache {
    val entries: Flow<Map<String, CachedComicThemes>>
    suspend fun save(pathWord: String, value: CachedComicThemes)
}

/** 无 Android 上下文的仓库调用可用内存实现；正式应用由 Hilt 注入 Room 实现。 */
class MemoryComicThemeCache : ComicThemeCache {
    override val entries = MutableStateFlow<Map<String, CachedComicThemes>>(emptyMap())
    override suspend fun save(pathWord: String, value: CachedComicThemes) {
        entries.update { it + (pathWord to value) }
    }
}

class PersistentComicThemeCache @Inject constructor(private val dao: ComicThemeCacheDao) :
    ComicThemeCache {
    private val recent = MemoryComicThemeCache()
    override val entries = combine(
        dao.observeAll().map { rows ->
            rows.mapNotNull { row ->
                try {
                    val themes = JsonParser.parseString(row.themesJson).asJsonArray.map { element ->
                        val item = element.asJsonObject
                        Theme(
                            item["name"].asString,
                            item["path_word"]?.takeUnless { it.isJsonNull }?.asString
                        )
                    }
                    row.pathWord to CachedComicThemes(themes, row.refreshAfterMillis)
                } catch (e: Exception) {
                    Log.w("ComicThemeCache", "忽略损坏的标签缓存：${row.pathWord}", e)
                    null
                }
            }.toMap()
        }.catch { error ->
            if (error is CancellationException) throw error
            Log.w("ComicThemeCache", "读取标签缓存失败", error)
            emit(emptyMap())
        }, recent.entries
    ) { stored, memory -> stored + memory }

    override suspend fun save(pathWord: String, value: CachedComicThemes) {
        recent.save(pathWord, value)
        val json = JsonArray().apply {
            value.themes.forEach { theme ->
                add(JsonObject().apply {
                    addProperty("name", theme.name)
                    addProperty("path_word", theme.pathWord)
                })
            }
        }.toString()
        try {
            dao.save(ComicThemeCacheEntity(pathWord, json, value.refreshAfterMillis))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // 磁盘暂不可写不能使已经成功的详情请求失败，当前会话仍可使用内存标签。
            Log.w("ComicThemeCache", "标签缓存暂时无法落盘", e)
        }
    }
}
