package com.rchdliang.copymanga_neo.data.local

import android.content.Context
import android.util.Log
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.rchdliang.copymanga_neo.domain.model.ComicFilterDefaults
import com.rchdliang.copymanga_neo.domain.model.ComicFilterOption
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** 同步恢复标签快照；网络失败时仍显示最近一次的标签和数量。 */
@Singleton
class ComicThemeCatalogStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("comic_theme_catalog", Context.MODE_PRIVATE)

    @Volatile
    var themes: List<ComicFilterOption> = restore()
        private set

    private fun restore(): List<ComicFilterOption> = try {
        prefs.getString("themes", null)?.let { raw ->
            JsonParser.parseString(raw).asJsonArray.map { item ->
                val tag = item.asJsonObject
                ComicFilterOption(
                    tag.get("name").asString,
                    tag.get("path_word").asString,
                    tag.get("count").asInt
                )
                    .also { require(it.name.isNotBlank() && it.pathWord.isNotBlank() && it.count!! >= 0) }
            }.takeIf { it.isNotEmpty() }
        } ?: ComicFilterDefaults.themes
    } catch (_: Exception) {
        ComicFilterDefaults.themes
    }

    @Synchronized
    fun save(value: List<ComicFilterOption>) {
        val json = JsonArray().apply {
            value.forEach { tag ->
                add(JsonObject().apply {
                    addProperty("name", tag.name)
                    addProperty("path_word", tag.pathWord)
                    addProperty("count", tag.count)
                })
            }
        }
        themes = value
        if (!prefs.edit().putString("themes", json.toString()).commit()) {
            Log.w("ComicThemes", "标签缓存未能写入，当前会话仍使用最新标签")
        }
    }
}
