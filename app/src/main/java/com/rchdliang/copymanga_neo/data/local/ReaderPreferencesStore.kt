package com.rchdliang.copymanga_neo.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.rchdliang.copymanga_neo.domain.model.ReaderPreferences
import com.rchdliang.copymanga_neo.domain.repository.ReaderPreferencesRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReaderPreferencesStore @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : ReaderPreferencesRepository {
    companion object {
        val KEY_READING_DIRECTION = stringPreferencesKey("reading_direction")
        val KEY_PAGE_SCALE = stringPreferencesKey("page_scale")
        val KEY_SCREEN_ORIENTATION = stringPreferencesKey("screen_orientation")
    }

    // 沿用原有三个 key，旧版本的阅读偏好自然成为全局默认值。
    override val globalPreferences = dataStore.data.map(::readGlobal).distinctUntilChanged()

    override fun comicPreferences(comicPathWord: String) = dataStore.data.map { prefs ->
        readComic(prefs, comicPathWord) ?: readGlobal(prefs)
    }.distinctUntilChanged()

    override suspend fun initializeComic(comicPathWord: String): Result<ReaderPreferences> =
        result {
            require(comicPathWord.isNotBlank())
            val saved = dataStore.edit { prefs ->
                if (prefs[comicKey(comicPathWord)] == null) {
                    prefs[comicKey(comicPathWord)] = encode(readGlobal(prefs))
                }
            }
            readComic(saved, comicPathWord) ?: readGlobal(saved)
    }

    override suspend fun updateGlobal(transform: (ReaderPreferences) -> ReaderPreferences): Result<Unit> =
        result {
            dataStore.edit { prefs ->
                val updated = transform(readGlobal(prefs)).normalized()
                prefs[KEY_READING_DIRECTION] = updated.readingDirection
                prefs[KEY_PAGE_SCALE] = updated.pageScale
                prefs[KEY_SCREEN_ORIENTATION] = updated.screenOrientation
            }
    }

    override suspend fun updateComic(
        comicPathWord: String,
        transform: (ReaderPreferences) -> ReaderPreferences
    ): Result<Unit> = result {
        require(comicPathWord.isNotBlank())
        dataStore.edit { prefs ->
            val current = readComic(prefs, comicPathWord) ?: readGlobal(prefs)
            prefs[comicKey(comicPathWord)] = encode(transform(current).normalized())
        }
    }

    override suspend fun resetGlobal(): Result<Unit> = updateGlobal { ReaderPreferences() }

    /** 恢复的是当前全局默认值的快照，之后仍与其他漫画互相独立。 */
    override suspend fun resetComic(comicPathWord: String): Result<Unit> = result {
        require(comicPathWord.isNotBlank())
        dataStore.edit { prefs -> prefs[comicKey(comicPathWord)] = encode(readGlobal(prefs)) }
    }

    override suspend fun exportComicPreferences(): Result<Map<String, ReaderPreferences>> = result {
        dataStore.data.first()
            .asMap().entries.filter { it.key.name.startsWith("comic_preferences:") }
            .associate { (key, raw) ->
                val path = key.name.removePrefix("comic_preferences:")
                require(path.isNotBlank() && raw is String) { "逐漫画阅读设置损坏，原数据已保留" }
                path to ComicPreferencesCodec.decode(raw)
            }
    }

    override suspend fun mergeComicPreferences(values: Map<String, ReaderPreferences>): Result<com.rchdliang.copymanga_neo.domain.model.ComicPreferencesChange> =
        result {
            val applied = values.mapValues { (path, value) ->
                require(path.isNotBlank()); ComicPreferencesCodec.encode(value)
            }
            val before = linkedMapOf<String, String>()
            dataStore.edit { prefs ->
                applied.forEach { (path, raw) ->
                    prefs[comicKey(path)]?.let { before[path] = it }
                    prefs[comicKey(path)] = raw
                }
            }
            com.rchdliang.copymanga_neo.domain.model.ComicPreferencesChange(before, applied)
        }

    override suspend fun rollbackComicPreferences(change: com.rchdliang.copymanga_neo.domain.model.ComicPreferencesChange): Result<Unit> =
        result {
            dataStore.edit { prefs ->
                val current =
                    change.applied.keys.mapNotNull { path -> prefs[comicKey(path)]?.let { path to it } }
                        .toMap()
                val restored =
                    com.rchdliang.copymanga_neo.domain.reading.ImportRollbackPolicy.restore(
                        change.before,
                        change.applied,
                        current
                    )
                change.applied.keys.forEach { path ->
                    val raw = restored[path]
                    if (raw == null) prefs.remove(comicKey(path)) else prefs[comicKey(path)] = raw
                }
            }
        }

    private fun readGlobal(prefs: Preferences) = ReaderPreferences(
        prefs[KEY_READING_DIRECTION] ?: "RIGHT_TO_LEFT",
        prefs[KEY_PAGE_SCALE] ?: "FIT_SCREEN",
        prefs[KEY_SCREEN_ORIENTATION] ?: "SYSTEM_DEFAULT"
    ).normalized()

    private fun comicKey(pathWord: String) = stringPreferencesKey("comic_preferences:$pathWord")

    private fun readComic(prefs: Preferences, pathWord: String): ReaderPreferences? {
        val stored = prefs[comicKey(pathWord)] ?: return null
        val json = JsonParser.parseString(stored).asJsonObject
        return ReaderPreferences(
            json["direction"]?.asString ?: "RIGHT_TO_LEFT",
            json["scale"]?.asString ?: "FIT_SCREEN",
            json["orientation"]?.asString ?: "SYSTEM_DEFAULT"
        ).normalized()
    }

    private fun encode(value: ReaderPreferences) = JsonObject().apply {
        addProperty("direction", value.readingDirection)
        addProperty("scale", value.pageScale)
        addProperty("orientation", value.screenOrientation)
    }.toString()

    private suspend fun <T> result(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }
}
