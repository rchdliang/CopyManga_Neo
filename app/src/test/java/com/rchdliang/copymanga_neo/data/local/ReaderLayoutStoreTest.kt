package com.rchdliang.copymanga_neo.data.local

import com.rchdliang.copymanga_neo.domain.model.ReaderPreferences
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withLock
import org.junit.Test
import org.junit.Assert.*

class ReaderLayoutStoreTest {
    // Android's FileStorage rename behavior is not portable to Windows. Test the repository
    // with the atomic DataStore interface; Android physical persistence still requires a device.
    private class MemoryStore(initial: androidx.datastore.preferences.core.Preferences = androidx.datastore.preferences.core.emptyPreferences()) : androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences> {
        val state = kotlinx.coroutines.flow.MutableStateFlow(initial)
        private val mutex = kotlinx.coroutines.sync.Mutex()
        override val data: kotlinx.coroutines.flow.Flow<androidx.datastore.preferences.core.Preferences> = state
        override suspend fun updateData(transform: suspend (androidx.datastore.preferences.core.Preferences) -> androidx.datastore.preferences.core.Preferences): androidx.datastore.preferences.core.Preferences = mutex.withLock {
            transform(state.value).also { state.value = it }
        }
    }
    @Test fun comicLayoutIsIsolatedPersistsAndBacksUpWithoutChangingOtherBook() = runBlocking {
        val data=MemoryStore()
        val first=ReaderPreferencesStore(data)
        first.updateComic("A") { it.copy(pageLayout="DOUBLE") }.getOrThrow()
        first.updateComic("B") { it.copy(pageLayout="AUTO") }.getOrThrow()
        val reopened=ReaderPreferencesStore(MemoryStore(data.state.value))
        assertEquals("DOUBLE",reopened.initializeComic("A").getOrThrow().pageLayout)
        assertEquals("AUTO",reopened.initializeComic("B").getOrThrow().pageLayout)
        reopened.resetComic("A").getOrThrow()
        assertEquals("SINGLE",reopened.initializeComic("A").getOrThrow().pageLayout)
        assertEquals("AUTO",reopened.initializeComic("B").getOrThrow().pageLayout)
        val saved=reopened.exportComicPreferences().getOrThrow()
        assertEquals(saved["B"],ComicPreferencesCodec.decode(ComicPreferencesCodec.encode(saved.getValue("B"))))
        val release=CompletableDeferred<Unit>()
        val pending=launch { release.await();reopened.updateComic("A") { it.copy(pageLayout="DOUBLE") }.getOrThrow() }
        reopened.updateComic("B") { it.copy(pageLayout="SINGLE") }.getOrThrow()
        release.complete(Unit);pending.join()
        assertEquals("DOUBLE",reopened.initializeComic("A").getOrThrow().pageLayout)
        assertEquals("SINGLE",reopened.initializeComic("B").getOrThrow().pageLayout)
    }
    @Test fun newFormatCodecRejectsInvalidLayoutAndRoundTripsSelectedLayout() {
        val value=ReaderPreferences(pageLayout="DOUBLE")
        assertEquals(value,ComicPreferencesCodec.decode(ComicPreferencesCodec.encode(value)))
        try { ComicPreferencesCodec.decode("""{"direction":"RIGHT_TO_LEFT","scale":"FIT_SCREEN","orientation":"SYSTEM_DEFAULT","layout":"broken"}""");fail("Invalid layout accepted") }
        catch (_: IllegalArgumentException) {}
    }
}
