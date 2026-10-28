package com.rchdliang.copymanga_neo.multiplatform.ui.profile

import com.rchdliang.copymanga_neo.domain.model.AppRelease
import com.rchdliang.copymanga_neo.domain.repository.AppInfoRepository
import com.rchdliang.copymanga_neo.domain.repository.PublicReleaseUnavailableException
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class AboutViewModelTest {
    @BeforeTest fun mainDispatcher() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @AfterTest fun resetDispatcher() { Dispatchers.resetMain() }
    private fun model(block: suspend () -> Result<AppRelease>) = AboutViewModel(object : AppInfoRepository {
        override suspend fun latestRelease() = block()
    })
    @Test fun newerReleaseOffersItsPage() {
        val release = AppRelease("v0.10.4", "0.10.4", "https://github.com/rchdliang/CopyManga_Neo/releases/tag/v0.10.4", "修复问题\n优化阅读")
        val model = model { Result.success(release) }
        model.checkForUpdate("0.10.3")
        assertEquals(release, model.uiState.value.update)
        assertFalse(model.uiState.value.checking)
    }
    @Test fun automaticCheckPreservesNotesAndDoesNotRepeatAfterDismissal() {
        val release = AppRelease("v0.10.4", "0.10.4", "page", "更新说明\n第二行")
        var calls = 0
        val model = model { calls++; Result.success(release) }
        model.checkAutomatically("0.10.3", enabled = true)
        assertEquals(release, model.uiState.value.update)
        model.dismissUpdate()
        model.checkAutomatically("0.10.3", enabled = true)
        assertNull(model.uiState.value.update)
        assertEquals(1, calls)
    }
    @Test fun inaccessiblePublicReleaseHasAnExplicitMessage() {
        val failure = PublicReleaseUnavailableException()
        val model = model { Result.failure(failure) }
        model.checkForUpdate("0.10.3")
        assertEquals(failure.message, model.uiState.value.message)
        assertNull(model.uiState.value.update)
        assertFalse(model.uiState.value.checking)
    }
    @Test fun equalVersionReportsLatest() {
        val model = model { Result.success(AppRelease("v0.10.3", "0.10.3", "page")) }
        model.checkForUpdate("0.10.3.0")
        assertEquals("已是最新版本", model.uiState.value.message)
        assertNull(model.uiState.value.update)
    }
    @Test fun cancellationDoesNotLeaveCheckingStuck() {
        val model = model { throw CancellationException("network changed") }
        model.checkForUpdate("0.10.3")
        assertFalse(model.uiState.value.checking)
    }
}
