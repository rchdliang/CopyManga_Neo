package com.rchdliang.copymanga_neo.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.rchdliang.copymanga_neo.data.local.entity.DownloadedComicEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadedComicDao {

    @Query("SELECT * FROM downloaded_comics ORDER BY downloadAt DESC")
    fun getAllComics(): Flow<List<DownloadedComicEntity>>

    @Query("SELECT * FROM downloaded_comics WHERE pathWord = :pathWord")
    suspend fun getComic(pathWord: String): DownloadedComicEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertComic(comic: DownloadedComicEntity)

    @Query("UPDATE downloaded_comics SET downloadedChapters = :downloadedChapters WHERE pathWord = :pathWord")
    suspend fun updateChapterCount(pathWord: String, downloadedChapters: Int)

    @Query("DELETE FROM downloaded_comics WHERE pathWord = :pathWord")
    suspend fun deleteComic(pathWord: String)

    @Query("SELECT pathWord FROM downloaded_comics")
    suspend fun getAllPathWords(): List<String>
}
