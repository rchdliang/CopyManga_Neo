package com.rchdliang.copymanga_neo.domain.repository

import com.rchdliang.copymanga_neo.domain.model.ReaderPreferences
import kotlinx.coroutines.flow.Flow

interface ReaderPreferencesRepository {
    val globalPreferences: Flow<ReaderPreferences>
    fun comicPreferences(comicPathWord: String): Flow<ReaderPreferences>
    suspend fun initializeComic(comicPathWord: String): Result<ReaderPreferences>
    suspend fun updateGlobal(transform: (ReaderPreferences) -> ReaderPreferences): Result<Unit>
    suspend fun updateComic(
        comicPathWord: String,
        transform: (ReaderPreferences) -> ReaderPreferences
    ): Result<Unit>

    suspend fun resetGlobal(): Result<Unit>
    suspend fun resetComic(comicPathWord: String): Result<Unit>
}
