package com.rchdliang.copymanga_neo.ui.profile

import com.rchdliang.copymanga_neo.domain.model.AppRelease
import com.rchdliang.copymanga_neo.domain.model.AppReleaseChannel
import com.rchdliang.copymanga_neo.domain.repository.AppInfoRepository
import com.rchdliang.copymanga_neo.domain.repository.PublicReleaseUnavailableException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class AboutViewModelTest {
    @Before fun setup() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun cleanup() { Dispatchers.resetMain() }
    private fun model(block: suspend () -> Result<AppRelease>) = AboutViewModel(object : AppInfoRepository {
        override suspend fun latestRelease() = block()
    })

    @Test fun cmpMigrationIgnoresNativeVersionComparisonInBothEntryPoints() {
        val cmp = AppRelease("v0.10.29", "0.10.29", "cmp-page", "迁移说明\n正文", AppReleaseChannel.CMP)
        val manual = model { Result.success(cmp) }
        manual.checkForUpdate("99.0")
        assertEquals(cmp, manual.uiState.value.update)
        val automatic = model { Result.success(cmp) }
        automatic.checkAutomatically("99.0", true)
        assertEquals(cmp, automatic.uiState.value.update)
    }

    @Test fun androidUpdateStillRequiresANewerVersionAndKeepsNotes() {
        val native = AppRelease("android-v0.10.29", "0.10.29", "native-page", "旧版说明")
        val newer = model { Result.success(native) }
        newer.checkForUpdate("0.10.28")
        assertEquals(native, newer.uiState.value.update)
        val same = model { Result.success(native) }
        same.checkForUpdate("0.10.29")
        assertNull(same.uiState.value.update)
        assertEquals("已是最新版本", same.uiState.value.message)
    }

    @Test fun disabledAutomaticCheckingDoesNotRequestEitherChannel() {
        var calls = 0
        val model = model { calls++; error("unexpected request") }
        model.checkAutomatically("0.10.29", false)
        assertEquals(0, calls)
        assertNull(model.uiState.value.update)
    }

    @Test fun dismissedMigrationDoesNotRepeatDuringTheSameStartup() {
        var calls = 0
        val cmp = AppRelease("v0.10.29", "0.10.29", "cmp-page", channel = AppReleaseChannel.CMP)
        val model = model { calls++; Result.success(cmp) }
        model.checkAutomatically("99.0", true)
        model.dismissUpdate()
        model.checkAutomatically("99.0", true)
        assertEquals(1, calls)
        assertNull(model.uiState.value.update)
        model.checkForUpdate("99.0")
        assertEquals(2, calls)
        assertEquals(cmp, model.uiState.value.update)
    }

    @Test fun automaticFailuresStaySilentAndManualFailuresRemainVisible() {
        val failure = PublicReleaseUnavailableException()
        val automatic = model { Result.failure(failure) }
        automatic.checkAutomatically("0.10.29", true)
        assertNull(automatic.uiState.value.message)
        assertNull(automatic.uiState.value.update)
        val manual = model { Result.failure(failure) }
        manual.checkForUpdate("0.10.29")
        assertEquals(failure.message, manual.uiState.value.message)
        assertFalse(manual.uiState.value.checking)
    }
}
