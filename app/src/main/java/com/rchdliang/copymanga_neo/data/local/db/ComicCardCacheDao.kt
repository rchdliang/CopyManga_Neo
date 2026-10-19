package com.rchdliang.copymanga_neo.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.rchdliang.copymanga_neo.data.local.entity.ComicCardCacheEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ComicCardCacheDao {
    @Query("SELECT * FROM comic_card_cache")
    fun observeAll(): Flow<List<ComicCardCacheEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(entry: ComicCardCacheEntity)
}
