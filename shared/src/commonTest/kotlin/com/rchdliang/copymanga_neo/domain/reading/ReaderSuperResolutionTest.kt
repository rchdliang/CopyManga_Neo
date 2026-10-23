package com.rchdliang.copymanga_neo.domain.reading

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderSuperResolutionTest {
    private val model = SrModel("waifu2x-cunet", "test-v1", "b".repeat(64), "ncnn-test")
    private fun request(id: Int = 1) = SrRequest(SrSource("session-$id", "a".repeat(64), 100, 100), model)
    // Protocol fake only; never used by an application backend or labelled as AI output.
    private fun output(r: SrRequest): SrArtifact {
        val bytes = ByteArray(33)
        byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10).copyInto(bytes)
        bytes[11] = 13
        byteArrayOf(73, 72, 68, 82).copyInto(bytes, 12)
        fun putInt(offset: Int, value: Int) {
            repeat(4) { i -> bytes[offset + i] = (value ushr ((3 - i) * 8)).toByte() }
        }
        putInt(16, r.source.width * 2); putInt(20, r.source.height * 2)
        return SrArtifact(bytes, r.source.width * 2, r.source.height * 2)
    }
    private fun backend(block: suspend (SrRequest) -> SrArtifact) = object : ReaderSrBackend {
        override val unavailableReason: String? = null
        override val workingBytes = 1024L
        override suspend fun upscale(request: SrRequest) = block(request)
    }

    @Test fun keysSeparateSourceRevisionSessionModelParametersAndOrientation() {
        val r = request()
        val variants = listOf(r, r.copy(source = r.source.copy(contentSha256 = "c".repeat(64))),
            r.copy(source = r.source.copy(scope = "another-account")),
            r.copy(source = r.source.copy(orientation = 6)),
            r.copy(model = model.copy(version = "v2")), r.copy(model = model.copy(digest = "d".repeat(64))),
            r.copy(model = model.copy(runtimeVersion = "new-color-policy")),
            r.copy(parameters = SrParameters(noise = 0)), r.copy(parameters = SrParameters(tile = 64)),
            r.copy(parameters = SrParameters(tta = true)))
        assertEquals(variants.size, variants.map { it.cacheKey }.toSet().size)
        assertEquals(r.cacheKey, r.copy().cacheKey)
        assertEquals(64, r.cacheKey.length)
    }

    @Test fun defaultsAreUnavailableAndNeverPretendToEnhance() = runTest {
        val pipeline = ReaderSrCoordinator(backgroundScope)
        val r = request(); pipeline.submit(r); runCurrent()
        assertIs<SrStatus.Unavailable>(pipeline.states.value[r.cacheKey]); pipeline.close()
    }

    @Test fun currentFirstDedupAndAtMostTwoNeighbors() = runTest {
        val seen = mutableListOf<Int>()
        val p = ReaderSrCoordinator(backgroundScope, backend { r ->
            seen += r.source.scope.removePrefix("session-").toInt(); output(r)
        })
        p.submit(request(1), listOf(request(2), request(3), request(4)))
        runCurrent(); assertEquals(listOf(1, 2, 3), seen)
        p.submit(request(1), listOf(request(2), request(3))); runCurrent()
        assertEquals(3, seen.size)
        p.submit(request(2), listOf(request(2), request(1))); runCurrent()
        assertEquals(3, seen.size); p.close()
    }

    @Test fun rapidFlipsConflateQueueAndCancelOldSelection() = runTest {
        val seen = mutableListOf<String>(); val cancelled = mutableListOf<String>()
        val p = ReaderSrCoordinator(backgroundScope, backend { r ->
            seen += r.source.scope
            try { delay(1000); output(r) } finally { cancelled += r.source.scope }
        })
        p.submit(request(1), listOf(request(2))); runCurrent()
        p.submit(request(3)); p.submit(request(4)); p.submit(request(5)); runCurrent()
        advanceTimeBy(1001); runCurrent()
        assertEquals(listOf("session-1", "session-5"), seen)
        assertTrue("session-1" in cancelled)
        assertEquals(setOf(request(5).cacheKey), p.states.value.keys)
        assertIs<SrStatus.Ready>(p.states.value[request(5).cacheKey]); p.close()
    }

    @Test fun lateNativeResultNeverPublishesOrOverlapsNewInference() = runTest {
        var active = 0; var peak = 0; val seen = mutableListOf<String>()
        val p = ReaderSrCoordinator(backgroundScope, backend { r ->
            active++; peak = maxOf(peak, active); seen += r.source.scope
            try { withContext(NonCancellable) { delay(100) }; output(r) } finally { active-- }
        })
        p.submit(request(1)); runCurrent(); p.submit(request(2)); runCurrent()
        assertEquals(listOf("session-1"), seen)
        advanceTimeBy(101); runCurrent()
        assertEquals(1, peak)
        assertFalse(p.states.value.containsKey(request(1).cacheKey))
        advanceTimeBy(101); runCurrent()
        p.submit(request(1)); runCurrent()
        assertEquals(3, seen.size, "Cancelled output must not enter the cache")
        p.close(); advanceTimeBy(101); runCurrent()
        assertTrue(p.states.value.isEmpty())
    }

    @Test fun failureAndTimeoutKeepReadingAndDoNotRetryAutomatically() = runTest {
        var count = 0
        val p = ReaderSrCoordinator(backgroundScope, backend { r ->
            count++; if (r == request(1)) error("decode failed") else { delay(100); output(r) }
        }, timeoutMillis = 10)
        p.submit(request(1), listOf(request(2))); runCurrent(); advanceTimeBy(11); runCurrent()
        assertEquals(2, count)
        assertTrue(p.states.value.values.all { it is SrStatus.Failed }); p.close()
    }

    @Test fun longPagesAndHugeDimensionsAreRejectedBeforeBackendAllocation() = runTest {
        var calls = 0
        val p = ReaderSrCoordinator(backgroundScope, backend { calls++; output(it) }, pixelBufferBudget = 1024 * 1024)
        val long = request().copy(source = request().source.copy(height = 100_000))
        val huge = request().copy(source = request().source.copy(width = Int.MAX_VALUE, height = Int.MAX_VALUE))
        val normalButTooLarge = request().copy(source = request().source.copy(width = 1000, height = 1000))
        p.submit(long, listOf(huge, normalButTooLarge)); runCurrent()
        assertEquals(0, calls); assertTrue(p.states.value.values.all { it is SrStatus.Unavailable }); p.close()
    }

    @Test fun rejectsWrongOutputDimensionsAndInvalidPng() = runTest {
        val p = ReaderSrCoordinator(backgroundScope, backend { r ->
            if (r == request(1)) output(r).copy(width = 100) else output(r).copy(png = byteArrayOf(1))
        })
        p.submit(request(1), listOf(request(2))); runCurrent()
        assertTrue(p.states.value.values.all { it is SrStatus.Failed }); p.close()
    }

    @Test fun resultCacheUsesBytesAndLruCountIndependently() {
        val cache = SrResultCache(66)
        val value = output(request())
        cache.put("a", value); cache.put("b", value); assertNotNull(cache.get("a"))
        cache.put("c", value); assertNull(cache.get("b")); assertNotNull(cache.get("a"))
        cache.put("large", value.copy(png = ByteArray(67))); assertNull(cache.get("large"))
        cache.clear(); assertNull(cache.get("a"))
        val countBound = SrResultCache(200)
        repeat(4) { countBound.put(it.toString(), value) }
        assertNull(countBound.get("0")); assertNotNull(countBound.get("3"))
    }

    @Test fun disableCancelsAndCloseIsIdempotent() = runTest {
        var canceled = false
        val p = ReaderSrCoordinator(backgroundScope, backend { try { awaitCancellation() } finally { canceled = true } })
        p.submit(request()); runCurrent(); p.submit(null); runCurrent()
        assertTrue(canceled); assertTrue(p.states.value.isEmpty())
        p.close(); p.close(); assertFailsWith<IllegalStateException> { p.submit(request()) }
    }
}
