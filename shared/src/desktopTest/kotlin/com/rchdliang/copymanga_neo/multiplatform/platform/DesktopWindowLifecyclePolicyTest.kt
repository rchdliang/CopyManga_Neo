package com.rchdliang.copymanga_neo.multiplatform.platform

import com.rchdliang.copymanga_neo.domain.model.DownloadTaskStatus
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class DesktopWindowLifecyclePolicyTest {
    @Test fun onlyRunnableTasksRequireCloseChoice() {
        val active = setOf(DownloadTaskStatus.WAITING, DownloadTaskStatus.WAITING_FOR_WIFI,
            DownloadTaskStatus.REQUESTING_PAGE, DownloadTaskStatus.DOWNLOADING)
        DownloadTaskStatus.entries.forEach { status ->
            assertEquals(status in active, desktopHasActiveTransfers(listOf(status), false), status.name)
        }
        assertFalse(desktopHasActiveTransfers(emptyList(), false))
        assertTrue(desktopHasActiveTransfers(emptyList(), true))
        assertEquals(DesktopWindowPhase.FLUSH_QUIT, desktopCloseRequest(false))
        assertEquals(DesktopWindowPhase.CONFIRM_CLOSE, desktopCloseRequest(true))
    }

    @Test fun onlyVerifiedTrayAllowsHiddenWindow() {
        listOf(DesktopNotificationStatus.Starting, DesktopNotificationStatus.Closed,
            DesktopNotificationStatus.Unavailable("test")).forEach { tray ->
            assertEquals(DesktopWindowPhase.OPEN, desktopBackgroundAfterFlush(tray))
            assertEquals(DesktopWindowPhase.OPEN, desktopRecoverTrayLoss(DesktopWindowPhase.BACKGROUND, tray))
        }
        assertEquals(DesktopWindowPhase.BACKGROUND, desktopBackgroundAfterFlush(DesktopNotificationStatus.Available))
        assertEquals(DesktopWindowPhase.BACKGROUND,
            desktopRecoverTrayLoss(DesktopWindowPhase.BACKGROUND, DesktopNotificationStatus.Available))
        assertFalse(DesktopWindowPhase.BACKGROUND.visible)
        assertFalse(DesktopWindowPhase.BACKGROUND.showApplication)
        assertTrue(DesktopWindowPhase.CONFIRM_CLOSE.showApplication)
        assertTrue(DesktopWindowPhase.FLUSH_BACKGROUND.visible)
        assertFalse(DesktopWindowPhase.FLUSH_BACKGROUND.showApplication)
    }

    @Test fun pendingFlushCannotHideAndReadsLatestTrayAfterFlush() = runTest {
        val flushed = CompletableDeferred<Unit>()
        var tray: DesktopNotificationStatus = DesktopNotificationStatus.Available
        val transition = async {
            desktopFlushWindowTransition(DesktopWindowPhase.FLUSH_BACKGROUND,
                { flushed.await(); Result.success(Unit) }, { tray }, { fail("Background must not quit") })
        }
        yield()
        assertFalse(transition.isCompleted)
        tray = DesktopNotificationStatus.Unavailable("lost during flush")
        flushed.complete(Unit)
        assertEquals(DesktopWindowPhase.OPEN, transition.await().getOrThrow())
    }

    @Test fun failedFlushDoesNotQuitOrReadTray() = runTest {
        for (phase in listOf(DesktopWindowPhase.FLUSH_QUIT, DesktopWindowPhase.FLUSH_BACKGROUND)) {
            val failure = IllegalStateException("write failed")
            val result = desktopFlushWindowTransition(phase, { Result.failure(failure) },
                { fail("Must retain visible UI") }, { fail("Must retain graph and queue") })
            assertSame(failure, result.exceptionOrNull())
        }
    }

    @Test fun quitWaitsForFlushAndShutdown() = runTest {
        val events = mutableListOf<String>()
        val drained = CompletableDeferred<Unit>()
        val transition = async {
            desktopFlushWindowTransition(DesktopWindowPhase.FLUSH_QUIT,
                { events += "flush"; Result.success(Unit) }, { fail("Quit needs no tray") },
                { events += "shutdown"; drained.await(); events += "exit" })
        }
        yield()
        assertEquals(listOf("flush", "shutdown"), events)
        assertFalse(transition.isCompleted)
        drained.complete(Unit)
        transition.await().getOrThrow()
        assertEquals(listOf("flush", "shutdown", "exit"), events)
    }
    @Test fun failedQuitNeverDiscardsWithoutExplicitChoice() = runTest {
        var exits = 0
        repeat(2) {
            val result = desktopFlushWindowTransition(DesktopWindowPhase.FLUSH_QUIT,
                { Result.failure(IllegalStateException("unwritable")) },
                { fail("Quit needs no tray") }, { exits++ })
            assertTrue(result.isFailure)
        }
        assertEquals(0, exits)
    }

    @Test fun explicitDiscardSkipsLibraryAccessAndStillAwaitsShutdown() = runTest {
        val directory = java.nio.file.Files.createTempDirectory("quit-library-test")
        val library = directory.resolve("reading.json")
        val original = "damaged existing library bytes"
        java.nio.file.Files.writeString(library, original)
        try {
            val drain = CompletableDeferred<Unit>()
            val transition = async {
                desktopFlushWindowTransition(DesktopWindowPhase.FLUSH_QUIT,
                    { fail("Explicit discard must not write or reset the library") },
                    { fail("Quit needs no tray") }, { drain.await() },
                    recovery = DesktopQuitRecovery.QUIT_WITHOUT_SAVING,
                    retryRead = { fail("Explicit discard must not reread/reset the library") })
            }
            yield()
            assertFalse(transition.isCompleted)
            assertEquals(original, java.nio.file.Files.readString(library))
            drain.complete(Unit)
            assertEquals(DesktopWindowPhase.FLUSH_QUIT, transition.await().getOrThrow())
            assertEquals(original, java.nio.file.Files.readString(library))
        } finally {
            java.nio.file.Files.deleteIfExists(library)
            java.nio.file.Files.deleteIfExists(directory)
        }
    }

    @Test fun retryReloadsBeforeSavingAndDoesNotQuitWhenReloadFails() = runTest {
        val events = mutableListOf<String>()
        var readable = false
        suspend fun attempt() = desktopFlushWindowTransition(DesktopWindowPhase.FLUSH_QUIT,
            { events += "save"; Result.success(Unit) }, { fail("Quit needs no tray") },
            { events += "shutdown" }, recovery = DesktopQuitRecovery.RETRY_READ_AND_SAVE,
            retryRead = { events += "read"; if (readable) Result.success(Unit) else Result.failure(IllegalStateException("damaged")) })
        assertTrue(attempt().isFailure)
        assertEquals(listOf("read"), events)
        readable = true
        assertTrue(attempt().isSuccess)
        assertEquals(listOf("read", "read", "save", "shutdown"), events)
    }

    @Test fun backgroundFailureCannotOptIntoDiscard() = runTest {
        assertFailsWith<IllegalArgumentException> {
            desktopFlushWindowTransition(DesktopWindowPhase.FLUSH_BACKGROUND,
                { fail("Invalid recovery") }, { fail("Invalid recovery") }, { fail("Must not quit") },
                recovery = DesktopQuitRecovery.QUIT_WITHOUT_SAVING)
        }
    }

}
