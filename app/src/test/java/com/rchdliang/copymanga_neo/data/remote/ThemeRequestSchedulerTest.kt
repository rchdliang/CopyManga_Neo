package com.rchdliang.copymanga_neo.data.remote

import com.rchdliang.copymanga_neo.domain.model.NetworkSettings
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeRequestSchedulerTest {
    @Test
    fun interactiveRequestPrecedesQueuedPrefetch() = runBlocking {
        val settings = MutableStateFlow(
            NetworkSettings(
                themeFetchConcurrency = 1,
                themeFetchIntervalMillis = 0
            )
        )
        val scheduler = ThemeRequestScheduler(settings, { settings.value })
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val order = mutableListOf<String>()
        val first = launch { scheduler.request { started.complete(Unit); release.await() } }
        started.await()
        val normal = launch { scheduler.request { order.add("normal") } }
        yield()
        val urgent = launch { scheduler.request(priority = true) { order.add("urgent") } }
        yield()
        release.complete(Unit)
        joinAll(first, normal, urgent)
        assertEquals(listOf("urgent", "normal"), order)
    }
}
