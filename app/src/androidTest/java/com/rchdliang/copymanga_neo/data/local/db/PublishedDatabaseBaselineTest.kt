package com.rchdliang.copymanga_neo.data.local.db

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** 首个公开结构的固定样本；后续迁移必须继续能读取本测试保存的数据。 */
class PublishedDatabaseBaselineTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java
    )
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun publishedSchemaPreservesDownloadsQueueAndComments() {
        val name = "published-baseline.db"
        context.deleteDatabase(name)
        helper.createDatabase(name, 6).apply {
            execSQL("INSERT INTO downloaded_comics VALUES ('book','Title',NULL,'Author','ongoing',NULL,NULL,NULL,1,1,123)")
            execSQL("INSERT INTO downloaded_chapters (uuid,comicPathWord,name,`index`,imageCount,imagePaths,status,downloadAt,groupPathWord,prevChapter,nextChapter) VALUES ('chapter','book','Chapter',0,1,'page.jpg','downloaded',123,'default',NULL,NULL)")
            execSQL("INSERT INTO downloaded_chapter_comments VALUES ('chapter','[]',0,1,0,123)")
            execSQL("INSERT INTO download_queue VALUES ('book','{}',123)")
            close()
        }
        val database = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(*AppDatabase.MIGRATIONS).build()
        try {
            val db = database.openHelper.writableDatabase
            for (table in listOf(
                "downloaded_comics",
                "downloaded_chapters",
                "downloaded_chapter_comments",
                "download_queue"
            )) {
                db.query("SELECT COUNT(*) FROM $table")
                    .use { assertTrue(it.moveToFirst()); assertEquals(1, it.getInt(0)) }
            }
        } finally {
            database.close(); context.deleteDatabase(name)
        }
    }

    @Test
    fun newerDatabaseIsRejectedWithoutDeletingItsData() {
        val name = "published-future.db"
        context.deleteDatabase(name)
        helper.createDatabase(name, 6).apply {
            execSQL("INSERT INTO download_queue VALUES ('preserve','{}',123)")
            version = AppDatabase.VERSION + 1
            close()
        }
        val database = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(*AppDatabase.MIGRATIONS).build()
        try {
            assertThrows(IllegalStateException::class.java) { database.openHelper.writableDatabase }
        } finally {
            database.close()
        }
        android.database.sqlite.SQLiteDatabase.openDatabase(
            context.getDatabasePath(name).path, null,
            android.database.sqlite.SQLiteDatabase.OPEN_READONLY
        ).use { db ->
            db.rawQuery("SELECT comicPathWord FROM download_queue", null).use {
                assertTrue(it.moveToFirst()); assertEquals("preserve", it.getString(0))
            }
        }
        context.deleteDatabase(name)
    }
}
