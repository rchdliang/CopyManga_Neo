package com.rchdliang.copymanga_neo.data.remote

import com.rchdliang.copymanga_neo.domain.model.NetworkSettings
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class ThemeRequestSchedulerTest {
    @Test fun limitsConcurrencyAndSpacesStartsAcrossAllCallers() = runTest {
        val settings = MutableStateFlow(NetworkSettings(themeFetchConcurrency = 3, themeFetchIntervalMillis = 100))
        val scheduler = ThemeRequestScheduler(settings, { settings.value }, { testScheduler.currentTime })
        val starts = mutableListOf<Long>()
        var active = 0
        var peak = 0
        (0..9).map { launch {
            scheduler.request {
                starts += testScheduler.currentTime
                active++
                peak = maxOf(peak, active)
                try { delay(1000) } finally { active-- }
            }
        } }.joinAll()
        assertEquals(3, peak)
        assertTrue(starts.zipWithNext().all { (a, b) -> b - a >= 100 })
        assertEquals(10, starts.size)
        assertEquals(0, active)
    }

    @Test fun settingsChangesWakeQueueAndLowerLimitWaitsForExistingWork() = runTest {
        val settings = MutableStateFlow(NetworkSettings(themeFetchConcurrency = 1, themeFetchIntervalMillis = 5000))
        val scheduler = ThemeRequestScheduler(settings, { settings.value }, { testScheduler.currentTime })
        val finish = CompletableDeferred<Unit>()
        var active = 0
        val starts = mutableListOf<Int>()
        val jobs = (0..3).map { id -> launch {
            scheduler.request {
                starts += id; active++
                try { finish.await() } finally { active-- }
            }
        } }
        runCurrent()
        assertEquals(listOf(0), starts)
        settings.value = settings.value.copy(themeFetchConcurrency = 3, themeFetchIntervalMillis = 0)
        runCurrent()
        assertEquals(listOf(0, 1, 2), starts)
        settings.value = settings.value.copy(themeFetchConcurrency = 1)
        runCurrent()
        assertEquals(3, active)
        finish.complete(Unit)
        jobs.joinAll()
        assertEquals(listOf(0, 1, 2, 3), starts)
        assertEquals(0, active)
    }

    @Test fun cancellingQueueHeadDoesNotLeakPermitOrSameComicLock() = runTest {
        val settings = MutableStateFlow(NetworkSettings(themeFetchConcurrency = 1, themeFetchIntervalMillis = 0))
        val scheduler = ThemeRequestScheduler(settings, { settings.value }, { testScheduler.currentTime })
        val hold = CompletableDeferred<Unit>()
        val running = launch { scheduler.withKey("one") { scheduler.request { hold.await() } } }
        runCurrent()
        val waiting = launch { scheduler.withKey("two") { scheduler.request { error("cancelled waiter ran") } } }
        val duplicate = async { scheduler.withKey("one") { scheduler.request { "resumed" } } }
        runCurrent()
        waiting.cancelAndJoin()
        running.cancelAndJoin()
        assertEquals("resumed", duplicate.await())
        assertEquals("free", scheduler.withKey("two") { scheduler.request { "free" } })
    }

    @Test fun duplicateComicChecksCacheAgainInsteadOfFetchingTwice() = runTest {
        val settings = MutableStateFlow(NetworkSettings(themeFetchIntervalMillis = 0))
        val scheduler = ThemeRequestScheduler(settings, { settings.value }, { testScheduler.currentTime })
        var cached = false
        var requests = 0
        List(12) { async {
            scheduler.withKey("same") {
                if (!cached) scheduler.request { requests++; delay(200); cached = true }
            }
        } }.awaitAll()
        assertEquals(1, requests)
    }
}
