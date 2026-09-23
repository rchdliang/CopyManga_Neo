package com.rchdliang.copymanga_neo.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.rchdliang.copymanga_neo.domain.model.Theme
import com.rchdliang.copymanga_neo.domain.model.matches
import com.rchdliang.copymanga_neo.domain.repository.ThemeBlacklistRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.themeBlacklistDataStore by preferencesDataStore(name = "theme_blacklist")

@Singleton
class ThemeBlacklistRepositoryImpl internal constructor(
    private val dataStore: DataStore<Preferences>
) : ThemeBlacklistRepository {
    @Inject
    constructor(@ApplicationContext context: Context) : this(context.themeBlacklistDataStore)

    private val themesKey = stringPreferencesKey("blocked_themes")
    private val enabledKey = booleanPreferencesKey("enabled")

    // 未设置时沿用旧版本的启用状态；关闭开关不会删除保存的标签。
    override val isEnabled = dataStore.data.map { it[enabledKey] ?: true }
        .distinctUntilChanged()

    override suspend fun setEnabled(enabled: Boolean): Result<Unit> = try {
        dataStore.edit { it[enabledKey] = enabled }
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    override val blockedThemes = dataStore.data.map { preferences ->
        decode(preferences[themesKey])
    }.distinctUntilChanged()

    override suspend fun setBlocked(theme: Theme, blocked: Boolean): Result<Unit> {
        return try {
            require(theme.name.isNotBlank()) { "标签名称不能为空" }
            dataStore.edit { preferences ->
                val themes = decode(preferences[themesKey]).filterNot { it.matches(theme) }
                preferences[themesKey] = encode(if (blocked) themes + theme else themes)
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // 使用固定 JSON 字段名，避免 release 混淆改变领域模型字段名后丢失黑名单。
    private fun encode(themes: List<Theme>): String = JsonArray().apply {
        themes.forEach { theme ->
            add(JsonObject().apply {
                addProperty("name", theme.name)
                addProperty("path_word", theme.pathWord)
            })
        }
    }.toString()

    private fun decode(value: String?): List<Theme> =
        value?.let { json ->
            JsonParser.parseString(json).asJsonArray.map { element ->
                val item = element.asJsonObject
                Theme(
                    name = item.get("name").asString,
                    pathWord = item.get("path_word")?.takeUnless { it.isJsonNull }?.asString
                )
            }
        }.orEmpty()
}
