package com.rchdliang.copymanga_neo.multiplatform.ui.reader

import com.rchdliang.copymanga_neo.domain.reading.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderSpreadSrIntegrationTest {
    @Test fun switchingToTwoLeavesCancelsInferenceAndReturningToSingleResumesRawPage() = runTest {
        var active = 0
        var maximum = 0
        val requested = mutableListOf<String>()
        val backend = object : ReaderSrPlatform {
            override val budgetBytes = 96L * 1024 * 1024
            override val workingBytes = 0L
            override val unavailableReason: String? = null
            override val detail = MutableStateFlow("test")
            override suspend fun source(page: SrPage) =
                SrSource(page.id, "a".repeat(64), 16, 16, location = page.source)
            override suspend fun upscale(request: SrRequest): SrArtifact {
                requested += request.source.scope
                active++; maximum = maxOf(maximum, active)
                try { awaitCancellation() } finally { active-- }
            }
        }
        val session = ReaderSrSession(backgroundScope, backend)
        val rawPage = SrPage("chapter:image:7", "file-7")
        fun select(continuous: Boolean, leaves: Int) {
            session.select(rawPage.takeIf { readerAllowsSuperResolution(continuous, leaves) })
        }
        try {
            session.settings.value = SrUserSettings(enabled = true)
            select(false, 1); runCurrent()
            assertEquals(0, active, "Original must appear before inference")
            session.originalReady(rawPage.id); runCurrent()
            assertEquals(1, active)
            select(false, 2); runCurrent()
            assertEquals(0, active)
            assertNull(session.display.value)
            session.originalReady("chapter:image:8"); runCurrent()
            assertEquals(listOf(rawPage.id), requested, "Second leaf must not schedule inference")
            select(false, 1); runCurrent()
            assertEquals(listOf(rawPage.id, rawPage.id), requested)
            assertEquals(1, active)
            select(false, 0); runCurrent(); assertEquals(0, active)
            select(true, 2); runCurrent(); assertEquals(1, active)
            assertEquals(1, maximum)
        } finally { session.close() }
        runCurrent(); assertEquals(0, active)
    }
}
