package com.rchdliang.copymanga_neo.multiplatform.ui.components

import com.rchdliang.copymanga_neo.domain.model.UserActionCancelled
import com.rchdliang.copymanga_neo.domain.model.asUserActionResult
import com.rchdliang.copymanga_neo.domain.model.nativeShareResult
import kotlinx.coroutines.CancellationException
import kotlin.test.*

class ShareOutcomeTest {
    @Test fun dismissedShareIsTypedAndQuietButNotSuccessful() {
        val browser = Result.success<String?>(null).asUserActionResult()
        val native = nativeShareResult(false, null)
        for (result in listOf(browser, native)) {
            assertFalse(result.isSuccess)
            assertIs<UserActionCancelled>(result.exceptionOrNull())
            assertNull(shareFailureMessage(result.exceptionOrNull()!!))
        }
        assertTrue(Result.success<String?>("").asUserActionResult().isSuccess)
        assertTrue(nativeShareResult(true, null).isSuccess)
    }

    @Test fun failuresRemainVisibleAndCoroutineCancellationPropagates() {
        assertEquals("native failure", shareFailureMessage(nativeShareResult(false, "native failure").exceptionOrNull()!!))
        assertTrue(nativeShareResult(true, "native failure").isFailure)
        val cancelled = CancellationException("disposed")
        assertSame(cancelled, Result.failure<String?>(cancelled).asUserActionResult().exceptionOrNull())
        assertSame(cancelled, assertFailsWith<CancellationException> { shareFailureMessage(cancelled) })
    }
}
