package com.rchdliang.copymanga_neo.data.repository.portable

import com.rchdliang.copymanga_neo.data.local.portable.DocumentStorage
import com.rchdliang.copymanga_neo.domain.model.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class PersistentThemeCatalogsTest {
    private class Storage:DocumentStorage {
        var value:String?=null
        var failWrite=false
        var writes=0
        override suspend fun read(key:String)=value
        override suspend fun write(key:String,value:String) { writes++;check(!failWrite);this.value=value }
        override suspend fun remove(key:String) { value=null }
    }
    @Test fun restartRestoresCatalogsCountsAndRefreshThrottle()=runTest {
        val storage=Storage();val first=PersistentThemeCatalogs(storage)
        first.initialize().getOrThrow()
        val comic=listOf(ComicFilterOption("Theme","theme",42))
        val web=listOf(NovelTag("Web","web",7));val app=listOf(NovelTag("App","app",8))
        val evidence=CachedComicThemes(listOf(Theme("Theme","theme")),1234567)
        first.save(comic);first.save(RequestMode.WEB,web);first.save(RequestMode.OFFICIAL_APP,app);first.save("comic",evidence)
        val restored=PersistentThemeCatalogs(storage);restored.initialize().getOrThrow()
        assertEquals(comic,restored.themes.value)
        assertEquals(app,restored.get(RequestMode.WEB));assertEquals(app,restored.get(RequestMode.OFFICIAL_APP))
        assertEquals(evidence,restored.entries.value["comic"])
    }
    @Test fun failedWriteStillPublishesCurrentSessionTags()=runTest {
        val storage=Storage();val catalogs=PersistentThemeCatalogs(storage);catalogs.initialize().getOrThrow()
        storage.failWrite=true
        val tags=listOf(ComicFilterOption("Theme","theme",42));catalogs.save(tags)
        assertEquals(tags,catalogs.themes.value);assertNotNull(catalogs.persistenceError.value)
    }
    @Test fun unreadableDocumentIsNotOverwrittenByLaterNetworkResult()=runTest {
        val storage=Storage().apply{value="broken original data"};val catalogs=PersistentThemeCatalogs(storage)
        assertTrue(catalogs.initialize().isFailure)
        val tags=listOf(NovelTag("New","new",3));catalogs.save(RequestMode.WEB,tags)
        assertEquals(tags,catalogs.get(RequestMode.WEB));assertEquals(0,storage.writes)
        assertEquals("broken original data",storage.value)
    }
}
