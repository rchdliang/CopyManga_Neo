package com.rchdliang.copymanga_neo.multiplatform.ui.components

import com.rchdliang.copymanga_neo.domain.model.ShareTarget
import com.rchdliang.copymanga_neo.domain.repository.ShareLinkRepository
import io.ktor.http.Url
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ShareViewModelTest {
    @Test fun resolvesOnlyOnClickAndEncodesEachPathSegmentAndQuery() = runTest {
        var requests = 0
        val viewModel = ShareViewModel(object : ShareLinkRepository {
            override suspend fun baseUrl(): Result<String> {
                requests++
                return Result.success("https://example.invalid/base/")
            }
        })
        assertEquals(0, requests)
        val result = viewModel.link(ShareTarget("title", listOf("comic", "a/b ?"), mapOf("q" to "雪 & 火")))
        val url = result.getOrThrow()
        assertEquals(1, requests)
        assertTrue(url.contains("a%2Fb%20%3F"), url)
        assertEquals("雪 & 火", Url(url).parameters["q"])
    }

    @Test fun linkLookupFailureIsPreserved() = runTest {
        val error = IllegalStateException("upstream unavailable")
        val viewModel = ShareViewModel(object : ShareLinkRepository {
            override suspend fun baseUrl(): Result<String> = Result.failure(error)
        })
        assertSame(error, viewModel.link(ShareTarget.page("newest", "最新上架")).exceptionOrNull())
    }
}
