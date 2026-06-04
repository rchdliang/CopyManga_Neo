package com.rchdliang.copymanga_neo.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReaderPreferencesStore @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    companion object {
        val KEY_READING_DIRECTION = stringPreferencesKey("reading_direction")
        val KEY_PAGE_SCALE = stringPreferencesKey("page_scale")
        val KEY_SCREEN_ORIENTATION = stringPreferencesKey("screen_orientation")
    }

    val readingDirection: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_READING_DIRECTION] ?: "RIGHT_TO_LEFT"
    }

    val pageScale: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_PAGE_SCALE] ?: "FIT_SCREEN"
    }

    val screenOrientation: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_SCREEN_ORIENTATION] ?: "SYSTEM_DEFAULT"
    }

    suspend fun setReadingDirection(value: String) {
        dataStore.edit { prefs -> prefs[KEY_READING_DIRECTION] = value }
    }

    suspend fun setPageScale(value: String) {
        dataStore.edit { prefs -> prefs[KEY_PAGE_SCALE] = value }
    }

    suspend fun setScreenOrientation(value: String) {
        dataStore.edit { prefs -> prefs[KEY_SCREEN_ORIENTATION] = value }
    }
}
