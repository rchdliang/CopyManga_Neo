package com.rchdliang.copymanga_neo.multiplatform.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopNotificationSettingsTest {
    private class FakeApi(val init: Int = 0, val code: Long = 33, val dispatchError: Throwable? = null) : WindowsSettingsApi {
        val calls = mutableListOf<String>()
        override fun initializeCom(): Int { calls += "init"; return init }
        override fun open(uri: String): Long { calls += uri; dispatchError?.let { throw it }; return code }
        override fun uninitializeCom() { calls += "cleanup" }
    }

    @Test fun windowsUsesFixedSettingsProtocolWithoutBrowserCapability() {
        assertNull(desktopNotificationSettingsUnavailableReason("Windows 11", false))
        val api = FakeApi()
        openDesktopNotificationSettings("Windows 11", false) { api }
        assertEquals(listOf("init", "ms-settings:notifications", "cleanup"), api.calls)
    }

    @Test fun unsupportedDesktopAndHeadlessNeverLoadNativeLibraries() {
        for ((os, headless) in listOf("Linux" to false, "Mac OS X" to false, "Windows 11" to true)) {
            assertNotNull(desktopNotificationSettingsUnavailableReason(os, headless))
            assertFailsWith<IllegalStateException> {
                openDesktopNotificationSettings(os, headless) { error("must not load native API") }
            }.also { assertTrue(it.message != "must not load native API") }
        }
    }

    @Test fun shellFailureCodesAreNotReportedAsSuccessAndAlwaysCleanup() {
        for (code in listOf(0L, 2L, 5L, 31L, 32L)) {
            val api = FakeApi(code = code)
            val error = assertFailsWith<IllegalStateException> {
                openDesktopNotificationSettings("Windows 10", false) { api }
            }
            assertTrue(error.message!!.contains("错误码 $code"))
            assertEquals("cleanup", api.calls.last())
        }
    }

    @Test fun alreadyInitializedComStillBalancesCleanup() {
        val api = FakeApi(init = 1)
        openDesktopNotificationSettings("Windows 11", false) { api }
        assertEquals("cleanup", api.calls.last())
    }

    @Test fun failedComInitializationDoesNotDispatchOrUninitialize() {
        val api = FakeApi(init = -2147417850) // RPC_E_CHANGED_MODE
        assertFailsWith<IllegalStateException> { openDesktopNotificationSettings("Windows 11", false) { api } }
        assertEquals(listOf("init"), api.calls)
    }

    @Test fun unavailableNativeLibraryHasManualSettingsGuidance() {
        val error = assertFailsWith<IllegalStateException> {
            openDesktopNotificationSettings("Windows 11", false) { throw UnsatisfiedLinkError("synthetic") }
        }
        assertTrue(error.message!!.contains("手动"))
    }

    @Test fun nativeDispatchErrorCleansUpWithoutBrowserFallback() {
        val api = FakeApi(dispatchError = UnsatisfiedLinkError("synthetic"))
        assertFailsWith<IllegalStateException> { openDesktopNotificationSettings("Windows 11", false) { api } }
        assertEquals(listOf("init", "ms-settings:notifications", "cleanup"), api.calls)
    }
}
