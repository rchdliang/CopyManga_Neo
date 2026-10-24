package com.rchdliang.copymanga_neo.multiplatform.sr

import com.rchdliang.copymanga_neo.domain.reading.*
import kotlinx.coroutines.*
import java.io.File
import java.nio.file.Files
import kotlin.test.*

class SrModelStoreTest {
    private suspend fun fixture(block: suspend (File, List<SrModelFile>) -> Unit) {
        val dir = Files.createTempDirectory("sr-model-test-").toFile()
        val specs = listOf("one", "two").map { SrModelFile(it, it.toByteArray().size.toLong(), it.toByteArray().sha256()) }
        try { block(File(dir, "model-v1"), specs) } finally { dir.deleteRecursively() }
    }
    private fun store(dir: File, specs: List<SrModelFile>) = SrModelStore(dir, specs) { spec, file, progress ->
        file.writeText(spec.name); progress(spec.bytes)
    }
    @Test fun noModelStartupDoesNotDownloadAndOfflineCacheSurvivesRecreation() = runBlocking { fixture { dir, specs ->
        var requests = 0
        val first = SrModelStore(dir,specs) { spec,file,_ -> requests++;file.writeText(spec.name) }
        first.refresh();assertEquals(SrModelState.Missing,first.state.value);assertEquals(0,requests)
        assertFailsWith<IllegalStateException> { first.requireReady() }
        first.download();assertEquals(SrModelState.Ready,first.state.value);assertEquals(2,requests)
        val offline = SrModelStore(dir,specs) { _,_,_ -> error("Network must not be used") }
        offline.refresh();offline.download();assertEquals(SrModelState.Ready,offline.state.value)
        offline.delete();assertEquals(SrModelState.Missing,offline.state.value);assertFalse(dir.exists())
        store(dir,specs).download();assertTrue(dir.isDirectory)
    } }
    @Test fun badHashNeverPublishesAndRetrySucceeds() = runBlocking { fixture { dir,specs ->
        val bad = SrModelStore(dir,specs) { _,file,_ -> file.writeText("bad") }
        bad.download();assertIs<SrModelState.Failed>(bad.state.value);assertFalse(dir.exists())
        store(dir,specs).download();assertEquals(SrModelState.Ready,bad.state.value)
    } }
    @Test fun failureAndCancellationLeaveNoPartialEnabledModel() = runBlocking { fixture { dir,specs ->
        val broken = SrModelStore(dir,specs) { _,_,_ -> throw java.io.IOException("offline") }
        broken.download();assertIs<SrModelState.Failed>(broken.state.value)
        val started = CompletableDeferred<Unit>()
        val slow = SrModelStore(dir,specs) { _,file,_ -> file.writeText("partial");started.complete(Unit);awaitCancellation() }
        val job = launch { slow.download() };started.await();job.cancelAndJoin()
        assertEquals(SrModelState.Cancelled,slow.state.value);assertFalse(dir.exists())
        assertFalse(dir.parentFile.listFiles()!!.any { it.name.startsWith("model-pending-") })
        store(dir,specs).download();assertEquals(SrModelState.Ready,slow.state.value)
    } }
    @Test fun concurrentWindowsOnlyDownloadOnceAndCorruptCacheRequiresRedownload() = runBlocking { fixture { dir,specs ->
        var calls=0
        fun instance() = SrModelStore(dir,specs) { spec,file,_ -> calls++;delay(30);file.writeText(spec.name) }
        val a=instance();val b=instance()
        coroutineScope { launch { a.download() };launch { b.download() } }
        assertEquals(2,calls);assertEquals(SrModelState.Ready,b.state.value)
        File(dir,"one").writeText("changed");a.refresh();assertEquals(SrModelState.Missing,a.state.value)
        b.download();assertEquals(4,calls);assertEquals(SrModelState.Ready,b.state.value)
    } }
    @Test fun inaccessibleStorageReportsFailureWithoutEscapingToUi() = runBlocking { fixture { dir,specs ->
        val blocked=File(dir.parentFile,"blocked").apply { writeText("file, not directory") }
        val model=store(File(blocked,"model"),specs)
        model.download();assertIs<SrModelState.Failed>(model.state.value)
    } }

    @Test fun confirmedDownloadOutlivesCallerAndCanBeCancelledFromReopenedSettings() = runBlocking { fixture { dir,specs ->
        val started=CompletableDeferred<Unit>()
        val first=SrModelStore(dir,specs) { _,file,_ -> file.writeText("partial");started.complete(Unit);awaitCancellation() }
        coroutineScope { launch { first.startDownload() } }
        started.await()
        val reopened=store(dir,specs)
        assertIs<SrModelState.Downloading>(reopened.state.value)
        reopened.cancelDownload()
        withTimeout(3000) { while (reopened.state.value !is SrModelState.Cancelled) delay(10) }
        assertFalse(dir.exists())
        reopened.startDownload()
        withTimeout(3000) { while (reopened.state.value != SrModelState.Ready) delay(10) }
    } }

}
