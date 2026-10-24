package com.rchdliang.copymanga_neo.data.local.portable
import com.rchdliang.copymanga_neo.domain.model.*
import kotlinx.coroutines.*
import kotlin.test.*
class ReaderComicLayoutStorageTest {
    private class Memory : DocumentStorage {
        val values=mutableMapOf<String,String>();var fail=false
        override suspend fun read(key:String)=values[key]
        override suspend fun write(key:String,value:String) { check(!fail);values[key]=value }
        override suspend fun remove(key:String) { values.remove(key) }
    }
    @Test fun comicLayoutPersistsIndependentlyAcrossRestartResetAndBackup() = runBlocking {
        val storage=Memory();val prefs=PortableReaderPreferences(storage)
        prefs.updateComic("A") { it.copy(pageLayout="DOUBLE") }.getOrThrow()
        prefs.updateComic("B") { it.copy(pageLayout="AUTO") }.getOrThrow()
        val reopened=PortableReaderPreferences(storage)
        assertEquals("DOUBLE",reopened.initializeComic("A").getOrThrow().pageLayout)
        assertEquals("AUTO",reopened.initializeComic("B").getOrThrow().pageLayout)
        reopened.resetComic("A").getOrThrow()
        assertEquals("SINGLE",reopened.initializeComic("A").getOrThrow().pageLayout)
        assertEquals("AUTO",reopened.initializeComic("B").getOrThrow().pageLayout)
        val target=PortableReaderPreferences(Memory())
        target.mergeComicPreferences(reopened.exportComicPreferences().getOrThrow()).getOrThrow()
        assertEquals("AUTO",target.initializeComic("B").getOrThrow().pageLayout)
    }
    @Test fun writeFailureIsReportedAndRetryKeepsCurrentTarget() = runBlocking {
        val storage=Memory();val prefs=PortableReaderPreferences(storage)
        prefs.initializeComic("A").getOrThrow();prefs.initializeComic("B").getOrThrow()
        storage.fail=true;assertTrue(prefs.updateComic("A") { it.copy(pageLayout="DOUBLE") }.isFailure)
        storage.fail=false
        val release=CompletableDeferred<Unit>()
        val pending=launch { release.await();prefs.updateComic("A") { it.copy(pageLayout="DOUBLE") }.getOrThrow() }
        prefs.updateComic("B") { it.copy(pageLayout="AUTO") }.getOrThrow();release.complete(Unit);pending.join()
        assertEquals("DOUBLE",prefs.initializeComic("A").getOrThrow().pageLayout)
        assertEquals("AUTO",prefs.initializeComic("B").getOrThrow().pageLayout)
    }
}
