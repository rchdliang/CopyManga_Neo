package com.rchdliang.copymanga_neo.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.rchdliang.copymanga_neo.data.local.entity.DownloadedChapterEntity
import com.rchdliang.copymanga_neo.data.local.entity.DownloadedComicEntity

@Database(
    entities = [
        DownloadedComicEntity::class,
        DownloadedChapterEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun downloadedComicDao(): DownloadedComicDao
    abstract fun downloadedChapterDao(): DownloadedChapterDao
}
