package com.rchdliang.copymanga_neo.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.rchdliang.copymanga_neo.data.local.entity.ComicThemeCacheEntity
import com.rchdliang.copymanga_neo.data.local.entity.DownloadQueueEntity
import com.rchdliang.copymanga_neo.data.local.entity.DownloadedChapterCommentsEntity
import com.rchdliang.copymanga_neo.data.local.entity.DownloadedChapterEntity
import com.rchdliang.copymanga_neo.data.local.entity.DownloadedComicEntity

@Database(
    entities = [
        DownloadedComicEntity::class,
        DownloadedChapterEntity::class,
        DownloadQueueEntity::class,
        ComicThemeCacheEntity::class,
        DownloadedChapterCommentsEntity::class,
        com.rchdliang.copymanga_neo.data.local.entity.ComicCardCacheEntity::class
    ],
    version = 6,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    companion object {
        /** 展示缓存独立建表，升级保留下载、评论及旧标签缓存。 */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS comic_card_cache (cacheKey TEXT NOT NULL PRIMARY KEY, metadataJson TEXT NOT NULL)")
            }
        }

        /** 增加章节评论快照，升级时保留现有图片、章节和下载队列。 */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS downloaded_chapter_comments (chapterUuid TEXT NOT NULL PRIMARY KEY, commentsJson TEXT NOT NULL, total INTEGER NOT NULL, complete INTEGER NOT NULL, closed INTEGER NOT NULL, savedAt INTEGER NOT NULL, FOREIGN KEY(chapterUuid) REFERENCES downloaded_chapters(uuid) ON UPDATE NO ACTION ON DELETE CASCADE)")
            }
        }

        /** 标签长期缓存单独建表，不改动已有下载和队列。 */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS comic_theme_cache (pathWord TEXT NOT NULL PRIMARY KEY, themesJson TEXT NOT NULL, refreshAfterMillis INTEGER NOT NULL)")
            }
        }

        /** 增加队列表，保留已有漫画与章节缓存。 */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS download_queue (comicPathWord TEXT NOT NULL PRIMARY KEY, taskJson TEXT NOT NULL, createdAt INTEGER NOT NULL)")
            }
        }
    }

    abstract fun downloadQueueDao(): DownloadQueueDao
    abstract fun comicCardCacheDao(): ComicCardCacheDao
    abstract fun comicThemeCacheDao(): ComicThemeCacheDao
    abstract fun downloadedComicDao(): DownloadedComicDao
    abstract fun downloadedChapterDao(): DownloadedChapterDao
    abstract fun downloadedChapterCommentsDao(): DownloadedChapterCommentsDao
}
