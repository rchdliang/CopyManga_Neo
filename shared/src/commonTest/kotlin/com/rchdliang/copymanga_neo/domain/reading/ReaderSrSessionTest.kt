package com.rchdliang.copymanga_neo.domain.reading

import kotlin.test.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderSrSessionTest {
    @Test fun defaultsOffWaitsForOriginalAndKeepsRealPageIdentityAcrossSelection() = runTest {
        val requested = mutableListOf<SrRequest>()
        val inspected = mutableListOf<SrPage>()
        val platform = object : ReaderSrPlatform {
            override val budgetBytes = 96L*1024*1024
            override val workingBytes = 0L
            override val unavailableReason: String? = null
            override val detail = MutableStateFlow("test fixture, not AI")
            override suspend fun source(page: SrPage): SrSource {
                inspected += page
                return SrSource(page.id, "a".repeat(64), 16, 16, location = page.source)
            }
            override suspend fun upscale(request: SrRequest): SrArtifact {
                requested += request
                awaitCancellation()
            }
        }
        val session = ReaderSrSession(backgroundScope, platform)
        val first = SrPage("chapter:image:7", "file-7")
        session.select(first); session.originalReady(first.id); runCurrent()
        assertTrue(requested.isEmpty())
        session.settings.value = SrUserSettings(enabled = true); runCurrent()
        assertEquals(first.id, requested.single().source.scope)
        session.originalReady("chapter:image:8"); runCurrent()
        assertEquals(1, requested.size) // Loading a neighbor must not cancel/restart the current page.
        val second = SrPage("chapter:image:9", "file-9")
        session.select(second); runCurrent(); assertEquals(1, requested.size)
        session.originalReady(second.id); runCurrent()
        assertEquals(second.id, requested.last().source.scope)
        session.select(null); runCurrent(); assertNull(session.display.value)
        session.close()
    }
    @Test fun executionModeAndFallbackPolicySeparateCacheEntries() {
        val source = SrSource("real-page-7", "a".repeat(64), 20, 30)
        val base = SrRequest(source, ReaderSrModel.model)
        assertNotEquals(base.cacheKey, base.copy(parameters = SrParameters(mode = SrExecutionMode.CPU)).cacheKey)
        assertNotEquals(base.cacheKey, base.copy(parameters = SrParameters(cpuFallback = false)).cacheKey)
        assertNotEquals(base.cacheKey, base.copy(source = source.copy(scope = "real-page-8")).cacheKey)
    }
}
