package com.rchdliang.copymanga_neo.multiplatform.platform

import java.nio.file.*
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.*

class DesktopInstanceGuardTest {
    private fun inDirectory(block: (Path) -> Unit) {
        val directory = Files.createTempDirectory("desktop-instance-test")
        try { block(directory) }
        finally { Files.walk(directory).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) } }
    }

    @Test fun canonicalDirectoryHasOneWriterAndReleasePermitsNextOwner() = inDirectory { dir ->
        DesktopInstanceGuard.acquire(dir).use { primary ->
            assertTrue(primary.primary)
            DesktopInstanceGuard.acquire(dir.resolve(".")).use { secondary ->
                assertFalse(secondary.primary)
                assertEquals(primary.directory, secondary.directory)
            }
        }
        DesktopInstanceGuard.acquire(dir).use { assertTrue(it.primary) }
    }

    @Test fun secondaryWaitsForPrimaryStartupAndReopensOnlyAfterAcknowledgement() = inDirectory { dir ->
        DesktopInstanceGuard.acquire(dir).use { primary ->
            DesktopInstanceGuard.acquire(dir).use { secondary ->
                val worker = Executors.newSingleThreadExecutor()
                try {
                    val result = worker.submit<Boolean> { secondary.requestOpen(2_000) }
                    var opens = 0
                    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2)
                    while (!result.isDone && System.nanoTime() < deadline) {
                        primary.pollOpenRequests { opens++ }
                        Thread.sleep(10)
                    }
                    assertTrue(result.get(1, TimeUnit.SECONDS))
                    assertEquals(1, opens)
                    primary.pollOpenRequests { opens++ }
                    assertEquals(1, opens)
                } finally { worker.shutdownNow() }
            }
        }
    }

    @Test fun missingAcknowledgementFailsClosedAndCleansRequest() = inDirectory { dir ->
        DesktopInstanceGuard.acquire(dir).use {
            DesktopInstanceGuard.acquire(dir).use { secondary ->
                assertFalse(secondary.primary)
                assertFalse(secondary.requestOpen(60))
                Files.newDirectoryStream(dir.resolve(".desktop-instance"), "request-*").use { assertFalse(it.iterator().hasNext()) }
            }
        }
    }

    @Test fun staleRequestsDoNotOpenNewSession() = inDirectory { dir ->
        DesktopInstanceGuard.acquire(dir).close()
        val control = dir.resolve(".desktop-instance")
        val stale = control.resolve("request-${UUID.randomUUID()}-${UUID.randomUUID()}")
        Files.copy(control.resolve("lock"), stale, StandardCopyOption.COPY_ATTRIBUTES)
        DesktopInstanceGuard.acquire(dir).use { primary ->
            primary.pollOpenRequests { fail("A stale session cannot reopen or acknowledge") }
            assertFalse(Files.exists(stale))
        }
    }

    @Test fun callbackFailureDoesNotAcknowledgeRequest() = inDirectory { dir ->
        DesktopInstanceGuard.acquire(dir).use { primary ->
            DesktopInstanceGuard.acquire(dir).use { secondary ->
                val worker = Executors.newSingleThreadExecutor()
                try {
                    val result = worker.submit<Boolean> { secondary.requestOpen(200) }
                    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1)
                    var attempted = false
                    // Once the callback has failed, only the secondary timeout is under test.
                    // Further polls race Windows deletion of its expired request.
                    while (!attempted && !result.isDone && System.nanoTime() < deadline) {
                        try { primary.pollOpenRequests { attempted = true; error("cannot reopen") } }
                        catch (_: IllegalStateException) { }
                        Thread.sleep(10)
                    }
                    assertTrue(attempted)
                    assertFalse(result.get(1, TimeUnit.SECONDS))
                } finally { worker.shutdownNow() }
            }
        }
    }

    @Test fun invalidCoordinationPathNeverCreatesUnlockedWriter() = inDirectory { dir ->
        Files.writeString(dir.resolve(".desktop-instance"), "not a directory")
        assertFailsWith<IllegalArgumentException> { DesktopInstanceGuard.acquire(dir) }
    }
    @Test fun unsafeExistingCoordinationPermissionsAreNotRepaired() = inDirectory { dir ->
        if (!Files.getFileStore(dir).supportsFileAttributeView("posix")) return@inDirectory
        val control = Files.createDirectory(dir.resolve(".desktop-instance"))
        val permissions = java.nio.file.attribute.PosixFilePermissions.fromString("rwxrwxrwx")
        Files.setPosixFilePermissions(control, permissions)
        val library = dir.resolve("reading.json")
        Files.writeString(library, "existing library")
        assertFailsWith<IllegalArgumentException> { DesktopInstanceGuard.acquire(dir) }
        assertEquals(permissions, Files.getPosixFilePermissions(control))
        assertEquals("existing library", Files.readString(library))
    }

}
