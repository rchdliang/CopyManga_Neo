package com.rchdliang.copymanga_neo.multiplatform.ui.reader

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderViewportCorrectionTest {
    @Test fun navigationDuringInitialPositioningPreventsAnyCorrectionOrCommit() = runTest {
        val owner = ReaderViewportCorrectionOwner()
        val gate = CompletableDeferred<Unit>()
        val result = async {
            correctReaderViewport(owner, 0.5, { true }, { gate.await() },
                { ReaderViewportMeasurement(500, 2000) }, { fail("Superseded navigation") }, { fail("Stale anchor") })
        }
        runCurrent()
        owner.invalidate()
        gate.complete(Unit)
        assertFalse(result.await())
        assertFalse(owner.active)
    }

    @Test fun oldFinallyCannotFinishNewerCorrectionOwner() = runTest {
        val owner = ReaderViewportCorrectionOwner()
        val gate = CompletableDeferred<Unit>()
        val old = async {
            correctReaderViewport(owner, 0.5, { true }, { gate.await() },
                { ReaderViewportMeasurement(500, 2000) }, { fail("Stale scroll") }, { fail("Stale commit") })
        }
        runCurrent()
        owner.invalidate()
        val newer = owner.begin()
        gate.complete(Unit)
        assertFalse(old.await())
        assertTrue(owner.owns(newer))
        owner.finish(newer)
        assertFalse(owner.active)
    }

    @Test fun pendingJumpPauseOrUserInteractionDuringScrollPreventsAnchorCommit() = runTest {
        repeat(3) {
            val owner = ReaderViewportCorrectionOwner()
            var eligible = true
            val gate = CompletableDeferred<Unit>()
            val result = async {
                correctReaderViewport(owner, 0.5, { eligible }, {},
                    { ReaderViewportMeasurement(500, 2000) }, { gate.await() }, { fail("Eligibility changed while suspended") })
            }
            runCurrent()
            eligible = false // Call site covers pending jump, observable lifecycle and real input.
            gate.complete(Unit)
            assertFalse(result.await())
            assertFalse(owner.active)
        }
    }

    @Test fun unavailableTargetMeasurementReturnsWithoutWaitingOrCommitting() = runTest {
        val owner = ReaderViewportCorrectionOwner()
        assertFalse(correctReaderViewport(owner, 0.5, { true }, {}, { null },
            { fail("No measurement") }, { fail("No measurement") }))
        assertFalse(owner.active)
    }

    @Test fun secondWidthChangeDuringScrollRetainsOriginalFractionUntilSettled() = runTest {
        val owner = ReaderViewportCorrectionOwner()
        var measurement = ReaderViewportMeasurement(500, 2000)
        val offsets = mutableListOf<Int>()
        var commits = 0
        assertTrue(correctReaderViewport(owner, 0.5, { true }, {}, { measurement }, { offset ->
            offsets += offset
            if (offsets.size == 1) measurement = ReaderViewportMeasurement(250, 1000)
        }, { commits++ }))
        assertEquals(listOf(1000, 500), offsets)
        assertEquals(1, commits)
        assertFalse(owner.active)
    }

    @Test fun cancellationReleasesOnlyOwnCorrectionAndNeverCommits() = runTest {
        val owner = ReaderViewportCorrectionOwner()
        val running = launch {
            correctReaderViewport(owner, 0.5, { true }, { awaitCancellation() },
                { fail("Cancelled") }, { fail("Cancelled") }, { fail("Cancelled") })
        }
        runCurrent()
        assertTrue(owner.active)
        running.cancelAndJoin()
        assertFalse(owner.active)
    }
}
