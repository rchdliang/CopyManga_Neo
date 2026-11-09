package com.rchdliang.copymanga_neo.multiplatform.platform

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MacDesktopPlatformTest {
    @Test fun macDataUsesApplicationSupportAndIgnoresXdg() {
        assertEquals(Path.of("user-home", "Library", "Application Support", "CopyMangaNeo"),
            desktopDataDirectory("Mac OS X", "user-home", null) { "xdg-data" })
        assertEquals(Path.of("custom-library"),
            desktopDataDirectory("Mac OS X", "user-home", "custom-library") { null })
    }

    @Test fun otherDesktopDirectoriesKeepTheirExistingLocations() {
        assertEquals(Path.of("local-data", "CopyMangaNeo"),
            desktopDataDirectory("Windows 11", "user-home", null) { if (it == "LOCALAPPDATA") "local-data" else null })
        assertEquals(Path.of("xdg-data", "copymanga-neo"),
            desktopDataDirectory("Linux", "user-home", null) { if (it == "XDG_DATA_HOME") "xdg-data" else null })
    }

    @Test fun missingKeychainItemIsDistinctFromLockedOrDeniedItem() {
        val api = FakeKeychainApi()
        val keychain = MacKeychain("test-service", api)
        api.status = MacKeychain.ITEM_NOT_FOUND
        assertNull(keychain.read("sessions-v2"))
        for (status in listOf(-25308, -25293, -128)) {
            api.status = status
            val failure = assertFailsWith<IllegalStateException> { keychain.read("sessions-v2") }
            assertTrue(failure.message.orEmpty().contains(status.toString()))
            assertFalse(failure.message.orEmpty().contains("private-session"))
        }
    }

    @Test fun keychainUsesSameServiceAndAccountAndClearsTransportBuffers() {
        val api = FakeKeychainApi()
        val keychain = MacKeychain("test-service", api)
        api.readBytes = "private-session-中文".toByteArray()
        assertEquals("private-session-中文", keychain.read("sessions-v2"))
        assertTrue(api.readBytes.all { it == 0.toByte() })
        keychain.write("sessions-v2", "private-session-中文")
        assertEquals(Triple("test-service", "sessions-v2", "private-session-中文"), api.written)
        assertTrue(requireNotNull(api.writeBytes).all { it == 0.toByte() })
        api.status = -25293
        assertFailsWith<IllegalStateException> { keychain.write("sessions-v2", "new-session") }
        assertTrue(requireNotNull(api.writeBytes).all { it == 0.toByte() })
    }
}

private class FakeKeychainApi : MacKeychainApi {
    var status = 0
    var readBytes = byteArrayOf()
    var writeBytes: ByteArray? = null
    var written: Triple<String, String, String>? = null
    override fun find(service: String, account: String) = MacKeychainRead(status, readBytes)
    override fun upsert(service: String, account: String, value: ByteArray): Int {
        writeBytes = value
        written = Triple(service, account, value.toString(Charsets.UTF_8))
        return status
    }
}
