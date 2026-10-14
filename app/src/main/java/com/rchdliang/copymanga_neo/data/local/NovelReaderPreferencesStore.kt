package com.rchdliang.copymanga_neo.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.rchdliang.copymanga_neo.domain.model.NovelReaderPreferences
import com.rchdliang.copymanga_neo.domain.repository.NovelReaderPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.novelReaderDataStore by preferencesDataStore("novel_reader_preferences")

@Singleton
class NovelReaderPreferencesStore @Inject constructor(
    @ApplicationContext context: Context
) : NovelReaderPreferencesRepository {
    private val dataStore = context.novelReaderDataStore
    private val size = floatPreferencesKey("font_size")
    private val height = floatPreferencesKey("line_height")
    private val spacing = floatPreferencesKey("paragraph_spacing")
    private val margin = floatPreferencesKey("margin")
    private val font = stringPreferencesKey("font")
    private val mode = stringPreferencesKey("mode")
    private val columns = stringPreferencesKey("columns")
    private val theme = stringPreferencesKey("theme")
    private val awake = booleanPreferencesKey("keep_screen_on")
    override val settings = dataStore.data.catch {
        if (it is IOException) emit(emptyPreferences()) else throw it
    }.map {
        NovelReaderPreferences(
            it[size] ?: 18f, it[height] ?: 1.65f, it[spacing] ?: 8f, it[margin] ?: 16f,
            it[font] ?: "SYSTEM", it[mode] ?: "SCROLL", it[columns] ?: "AUTO",
            it[theme] ?: "SYSTEM", it[awake] ?: true
        ).normalized()
    }

    override suspend fun update(value: NovelReaderPreferences): Result<Unit> = try {
        val normalized = value.normalized()
        dataStore.edit {
            it[size] = normalized.fontSize
            it[height] = normalized.lineHeight
            it[spacing] = normalized.paragraphSpacing
            it[margin] = normalized.margin
            it[font] = normalized.font
            it[mode] = normalized.mode
            it[columns] = normalized.columns
            it[theme] = normalized.theme
            it[awake] = normalized.keepScreenOn
        }
        Result.success(Unit)
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        Result.failure(error)
    }
}
