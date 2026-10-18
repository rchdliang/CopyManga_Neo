package com.rchdliang.copymanga_neo.multiplatform

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ForegroundServiceGateTest {
    @Test fun noTransferBeforeServiceAcknowledgement() = runTest {
        var starts = 0
        val gate = ForegroundServiceGate({ starts++ })
        val waiting = async { gate.awaitReady() }
        runCurrent()
        assertEquals(1, starts); assertFalse(waiting.isCompleted)
        gate.started(Any())
        waiting.await()
    }
    @Test fun failedOrTimedOutServiceRejectsWork() = runTest {
        val gate = ForegroundServiceGate({}, 100)
        val waiting = async { runCatching { gate.awaitReady() } }
        runCurrent(); gate.failed()
        assertTrue(waiting.await().isFailure)
        val timeout = async { runCatching { gate.awaitReady() } }
        advanceUntilIdle()
        assertTrue(timeout.await().isFailure)
    }
    @Test fun destroyedOldServiceCannotRevokeReplacement() = runTest {
        val gate = ForegroundServiceGate({})
        val first = Any(); val second = Any()
        gate.started(first); gate.stopped(first)
        val waiting = async { gate.awaitReady() }
        runCurrent(); assertFalse(waiting.isCompleted)
        gate.started(second); gate.stopped(first)
        waiting.await()
        gate.awaitReady()
        gate.stopped(second)
        val next = async { gate.awaitReady() }
        runCurrent(); assertFalse(next.isCompleted)
        next.cancelAndJoin()
    }
}
