package com.rchdliang.copymanga_neo.data.local.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.rchdliang.copymanga_neo.data.local.entity.DownloadQueueEntity

@Dao
interface DownloadQueueDao {
    @Query("SELECT * FROM download_queue ORDER BY createdAt ASC, comicPathWord ASC")
    suspend fun getAll(): List<DownloadQueueEntity>

    @Query("SELECT * FROM download_queue WHERE comicPathWord = :pathWord")
    suspend fun get(pathWord: String): DownloadQueueEntity?

    @Upsert
    suspend fun save(task: DownloadQueueEntity)

    @Query("DELETE FROM download_queue WHERE comicPathWord = :pathWord")
    suspend fun remove(pathWord: String)

    @Query("DELETE FROM download_queue")
    suspend fun clear()
}
