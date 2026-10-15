package com.rchdliang.copymanga_neo.domain.repository

import com.rchdliang.copymanga_neo.domain.model.NovelReaderPreferences
import kotlinx.coroutines.flow.Flow

interface NovelReaderPreferencesRepository {
    val settings: Flow<NovelReaderPreferences>
    suspend fun update(value: NovelReaderPreferences): Result<Unit>
}
