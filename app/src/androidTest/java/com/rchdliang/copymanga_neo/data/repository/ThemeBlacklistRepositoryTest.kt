package com.rchdliang.copymanga_neo.data.repository

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.rchdliang.copymanga_neo.domain.model.Theme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ThemeBlacklistRepositoryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder(
        InstrumentationRegistry.getInstrumentation().targetContext.cacheDir
    )

    @Test
    fun savesDeduplicatesAndRestoresBlacklistAcrossStoreRestart() = runBlocking {
        val file = File(temporaryFolder.root, "themes.preferences_pb")
        val job = SupervisorJob()
        val store =
            PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job)) { file }
        val repository = ThemeBlacklistRepositoryImpl(store)
        val original = Theme("校园", "xiaoyuan")
        val renamed = Theme("校園", "xiaoyuan")
        try {
            assertTrue(repository.blockedThemes.first().isEmpty())
            assertTrue(repository.isEnabled.first())
            repository.setBlocked(original, true).fold({}, { throw AssertionError("保存失败", it) })
            repository.setBlocked(renamed, true).fold({}, { throw AssertionError("更新失败", it) })
            assertEquals(listOf(renamed), repository.blockedThemes.first())
            repository.setEnabled(false).fold({}, { throw AssertionError("停用失败", it) })
            assertFalse(repository.isEnabled.first())
            assertEquals(listOf(renamed), repository.blockedThemes.first())
        } finally {
            job.cancelAndJoin()
        }

        val reopenedJob = SupervisorJob()
        val reopenedStore = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(Dispatchers.IO + reopenedJob)
        ) { file }
        val reopened = ThemeBlacklistRepositoryImpl(reopenedStore)
        try {
            assertFalse(reopened.isEnabled.first())
            assertEquals(listOf(renamed), reopened.blockedThemes.first())
            reopened.setEnabled(true).fold({}, { throw AssertionError("启用失败", it) })
            assertTrue(reopened.isEnabled.first())
            assertEquals(listOf(renamed), reopened.blockedThemes.first())
            reopened.setBlocked(original, false).fold({}, { throw AssertionError("移除失败", it) })
            assertTrue(reopened.blockedThemes.first().isEmpty())
        } finally {
            reopenedJob.cancelAndJoin()
        }
    }
}
