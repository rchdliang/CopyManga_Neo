package com.rchdliang.copymanga_neo.multiplatform.platform

import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class DesktopKeepAwakePolicyTest {
    @Test fun onlyEnabledResumedReaderCanHoldInhibitor() {
        Lifecycle.State.entries.forEach { state ->
            assertEquals(state == Lifecycle.State.RESUMED, desktopKeepAwakeAllowed(true, state))
            assertFalse(desktopKeepAwakeAllowed(false, state))
        }
    }

    @Test fun pauseReleasesLeaseAndDisposalReleasesReacquiredLease() = runTest {
        val state = MutableStateFlow(Lifecycle.State.STARTED)
        var acquired = 0
        var released = 0
        val collector = launch {
            state.collectLatest {
                if (desktopKeepAwakeAllowed(true, it)) holdDesktopKeepAwakeLease(
                    { acquired++; Result.success(AutoCloseable { released++ }) }, { fail(it) })
            }
        }
        runCurrent()
        assertEquals(0, acquired)
        state.value = Lifecycle.State.RESUMED
        runCurrent()
        assertEquals(1, acquired)
        state.value = Lifecycle.State.STARTED
        runCurrent()
        assertEquals(1, released)
        state.value = Lifecycle.State.RESUMED
        runCurrent()
        assertEquals(2, acquired)
        collector.cancelAndJoin()
        assertEquals(2, released)
    }

    @Test fun failedAcquisitionReportsWithoutInventingLease() = runTest {
        val warnings = mutableListOf<String>()
        holdDesktopKeepAwakeLease({ Result.failure(IllegalStateException("unavailable")) }, warnings::add)
        assertEquals(listOf("unavailable"), warnings)
    }
}
