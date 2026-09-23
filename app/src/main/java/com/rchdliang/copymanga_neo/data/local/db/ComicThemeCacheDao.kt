package com.rchdliang.copymanga_neo.data.local.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.rchdliang.copymanga_neo.data.local.entity.ComicThemeCacheEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ComicThemeCacheDao {
    @Query("SELECT * FROM comic_theme_cache")
    fun observeAll(): Flow<List<ComicThemeCacheEntity>>

    @Upsert
    suspend fun save(entry: ComicThemeCacheEntity)
}
