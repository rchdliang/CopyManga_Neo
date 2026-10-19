package com.rchdliang.copymanga_neo.data.backup

import android.content.Context
import android.content.ContextWrapper
import androidx.datastore.preferences.core.PreferencesFileSerializer
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

/** 使用隔离目录和偏好名称，不读写被测应用的真实个人数据。 */
class AndroidUserDataBackupTest {
    private class IsolatedContext(base: Context) : ContextWrapper(base) {
        private val prefix = "backup-test-" + UUID.randomUUID()
        private val root = File(base.cacheDir, prefix).apply { mkdirs() }
        override fun getFilesDir() = File(root, "files").apply { mkdirs() }
        override fun getNoBackupFilesDir() = File(root, "private").apply { mkdirs() }
        override fun getSharedPreferences(name: String, mode: Int) =
            baseContext.getSharedPreferences(prefix + name, mode)
        fun cleanup() {
            for (name in listOf("local_reading_library", "reader_tools")) baseContext.deleteSharedPreferences(prefix + name)
            check(root.parentFile?.canonicalFile == baseContext.cacheDir.canonicalFile)
            root.deleteRecursively()
        }
    }

    @Test fun annotationsAndPreferencesRoundTripWithoutCredentialsOrDownloads() = runBlocking {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val source = IsolatedContext(base)
        val target = IsolatedContext(base)
        try {
            val annotations = """[{"id":"n","owner":"owner","mode":"WEB","path":"book","volume":"v","volume_name":"V","text":"text","range":{"start_line":0,"start_character":0,"end_line":0,"end_character":2},"style":"PRIMARY","note":"personal note","created_at":1,"updated_at":1}]"""
            val annotationFile = File(source.filesDir, "datastore/novel_reader_annotations_v1.preferences_pb")
            annotationFile.parentFile!!.mkdirs()
            annotationFile.outputStream().use { output ->
                PreferencesFileSerializer.writeTo(mutablePreferencesOf(stringPreferencesKey("annotations") to annotations), output)
            }
            source.getSharedPreferences("reader_tools", Context.MODE_PRIVATE).edit().putInt("auto_interval", 13).commit()
            File(source.noBackupFilesDir, "auth-v1.preferences_pb").writeText("never export this")
            val backup = AndroidUserDataBackup(source).export()
            assertFalse(backup.contains("never export this"))
            val secret = File(target.noBackupFilesDir, "auth-v1.preferences_pb").apply { writeText("keep secret") }
            val download = File(target.filesDir, "downloads/page.jpg").apply { parentFile!!.mkdirs(); writeText("keep image") }
            val restorer = AndroidUserDataBackup(target)
            restorer.prepare(backup)
            assertTrue(restorer.isPending())
            val restoredFile = File(target.filesDir, "datastore/novel_reader_annotations_v1.preferences_pb")
            assertFalse(restoredFile.exists())
            restorer.recover()
            val restored = restoredFile.inputStream().use { PreferencesFileSerializer.readFrom(it) }
            assertEquals(annotations, restored[stringPreferencesKey("annotations")])
            assertEquals(13, target.getSharedPreferences("reader_tools", Context.MODE_PRIVATE).getInt("auto_interval", 0))
            assertEquals("keep secret", secret.readText())
            assertEquals("keep image", download.readText())
        } finally { source.cleanup(); target.cleanup() }
    }
}
