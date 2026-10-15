package com.rchdliang.copymanga_neo.multiplatform.app

import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class GraphShutdownTest {
    @Test fun waitsForNonCancellableExportCleanupBeforeClosingIo() = runTest {
        val lifetime = SupervisorJob()
        val scope = CoroutineScope(coroutineContext + lifetime)
        val cleanupGate = CompletableDeferred<Unit>()
        val started = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        scope.launch {
            try { started.complete(Unit); awaitCancellation() }
            finally { withContext(NonCancellable) {
                events += "rollback"
                cleanupGate.await()
                events += "persist terminal state"
            } }
        }
        started.await()
        val shutdown = launch { drainGraphLifetime(lifetime) { events += "close IO" } }
        yield()
        assertFalse(shutdown.isCompleted)
        assertFalse("close IO" in events)
        cleanupGate.complete(Unit)
        shutdown.join()
        assertEquals(listOf("rollback", "persist terminal state", "close IO"), events)
        assertTrue(lifetime.isCompleted)
    }
}
