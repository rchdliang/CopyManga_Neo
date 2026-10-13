package com.rchdliang.copymanga_neo.data.novel

import android.content.Context
import androidx.core.content.edit
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.rchdliang.copymanga_neo.domain.model.NovelTag
import com.rchdliang.copymanga_neo.domain.model.NovelThemeDefaults
import com.rchdliang.copymanga_neo.domain.model.RequestMode
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** 公开题材数量按请求模式缓存，账号切换不会把网页与 App 目录混用。 */
@Singleton
class NovelThemeCatalogStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("novel_theme_catalog", Context.MODE_PRIVATE)
    private val catalogs = RequestMode.entries.associateWith { restore(it) }.toMutableMap()

    private fun restore(mode: RequestMode): List<NovelTag> = try {
        prefs.getString(mode.name, null)?.let { raw ->
            val result = JsonObject().apply { add("list", JsonParser.parseString(raw)) }
            NovelJson.themeCounts(result).takeIf { it.isNotEmpty() }
        } ?: NovelThemeDefaults.themes
    } catch (_: Exception) {
        NovelThemeDefaults.themes
    }

    @Synchronized
    fun get(mode: RequestMode): List<NovelTag> = catalogs.getValue(mode)

    @Synchronized
    fun save(mode: RequestMode, value: List<NovelTag>) {
        val json = JsonArray().apply {
            value.forEach { tag ->
                add(JsonObject().apply {
                    addProperty("name", tag.name)
                    addProperty("path_word", tag.path)
                    addProperty("count", tag.count)
                })
            }
        }
        catalogs[mode] = value
        prefs.edit(commit = true) {
            putString(mode.name, json.toString())
        }
    }
}
