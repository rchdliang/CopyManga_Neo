package com.rchdliang.copymanga_neo.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.rchdliang.copymanga_neo.data.local.entity.DownloadedChapterEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadedChapterDao {

    @Query("SELECT * FROM downloaded_chapters")
    fun getAllChapters(): Flow<List<DownloadedChapterEntity>>

    @Query(
        """UPDATE downloaded_chapters SET name = :name, `index` = :index,
        groupPathWord = :group, prevChapter = :prev, nextChapter = :next
        WHERE comicPathWord = :comicPathWord AND uuid = :uuid"""
    )
    suspend fun updateMetadata(
        comicPathWord: String, uuid: String, name: String, index: Int,
        group: String, prev: String?, next: String?
    )

    @Query("SELECT * FROM downloaded_chapters WHERE comicPathWord = :comicPathWord ORDER BY `index` ASC")
    fun getChapters(comicPathWord: String): Flow<List<DownloadedChapterEntity>>

    @Query("SELECT * FROM downloaded_chapters WHERE comicPathWord = :comicPathWord ORDER BY `index` ASC")
    suspend fun getChaptersOnce(comicPathWord: String): List<DownloadedChapterEntity>

    @Query("SELECT * FROM downloaded_chapters WHERE uuid = :uuid")
    suspend fun getChapter(uuid: String): DownloadedChapterEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChapter(chapter: DownloadedChapterEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChapters(chapters: List<DownloadedChapterEntity>)

    @Query("DELETE FROM downloaded_chapters WHERE comicPathWord = :comicPathWord")
    suspend fun deleteChaptersByComic(comicPathWord: String)

    @Query("DELETE FROM downloaded_chapters WHERE uuid = :uuid")
    suspend fun deleteChapter(uuid: String)

    @Query("SELECT uuid FROM downloaded_chapters WHERE comicPathWord = :comicPathWord")
    suspend fun getChapterUuids(comicPathWord: String): List<String>

    @Query("DELETE FROM downloaded_chapters WHERE uuid IN (:uuids)")
    suspend fun deleteChaptersByUuids(uuids: List<String>)

    @Query("SELECT imagePaths FROM downloaded_chapters WHERE uuid IN (:uuids)")
    suspend fun getImagePaths(uuids: List<String>): List<String>
}
