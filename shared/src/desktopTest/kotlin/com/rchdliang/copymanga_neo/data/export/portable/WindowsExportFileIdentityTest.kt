package com.rchdliang.copymanga_neo.data.export.portable

import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.WString
import java.io.IOException
import java.nio.file.Path
import java.time.Instant
import kotlin.test.*

class WindowsExportFileIdentityTest {
    private class FakeKernel : ExportFileKernel32 {
        var error = 5
        var handle: Pointer? = Pointer(123)
        var attributes = 0x80
        var metadataSucceeds = true
        var identitySucceeds = true
        var volume = 1L
        var lowId = 2L
        var highId = 3L
        var closed = 0
        override fun CreateFileW(path: WString, access: Int, sharing: Int, security: Pointer?, disposition: Int, flags: Int, template: Pointer?): Pointer? {
            assertEquals(0, access)
            assertEquals(7, sharing)
            assertEquals(3, disposition) // OPEN_EXISTING, never create/truncate.
            assertEquals(0x02200000, flags) // OPEN_REPARSE_POINT | BACKUP_SEMANTICS.
            assertTrue(path.toString().startsWith("\\\\?\\"))
            return handle
        }
        override fun GetFileInformationByHandle(handle: Pointer, information: Pointer): Boolean {
            information.setInt(0, attributes)
            information.setLong(4, 116444736000000001L)
            information.setLong(20, 116444736010000002L)
            information.setInt(32, 1)
            information.setInt(36, -1)
            return metadataSucceeds
        }
        override fun GetFileInformationByHandleEx(handle: Pointer, informationClass: Int, information: Pointer, size: Int): Boolean {
            assertEquals(18, informationClass)
            assertEquals(24, size)
            information.setLong(0, volume)
            information.setLong(8, lowId)
            information.setLong(16, highId)
            return identitySucceeds
        }
        override fun CloseHandle(handle: Pointer): Boolean { closed++; return true }
        override fun GetLastError() = error
    }
    private val path = Path.of("archive.zip")
    private val invalidHandle = Pointer(if (Native.POINTER_SIZE == 8) -1L else 0xffffffffL)

    @Test fun readsFullIdentityAndMetadataFromOneHandleAndClosesIt() {
        val api = FakeKernel()
        val first = assertNotNull(readWindowsExportFileIdentity(path, api))
        assertEquals(8589934591L, first.size)
        assertEquals(Instant.ofEpochSecond(0, 100), first.created.toInstant())
        assertEquals(Instant.ofEpochSecond(1, 200), first.modified.toInstant())
        assertEquals(first, readWindowsExportFileIdentity(path, api))
        api.lowId++
        assertNotEquals(first, readWindowsExportFileIdentity(path, api))
        api.lowId--
        api.highId++
        assertNotEquals(first, readWindowsExportFileIdentity(path, api))
        api.highId--
        api.volume++
        assertNotEquals(first, readWindowsExportFileIdentity(path, api))
        assertEquals(5, api.closed)
    }

    @Test fun onlyMissingPathsAreTreatedAsNewDestinations() {
        for (error in listOf(2, 3)) {
            val api = FakeKernel().apply { handle = invalidHandle; this.error = error }
            assertNull(readWindowsExportFileIdentity(path, api))
            assertEquals(0, api.closed)
        }
        for (handle in listOf(null, invalidHandle)) {
            for (error in listOf(5, 32)) { // ACCESS_DENIED / SHARING_VIOLATION are never absence.
                val denied = FakeKernel().apply { this.handle = handle; this.error = error }
                assertFailsWith<IOException> { readWindowsExportFileIdentity(path, denied) }
                assertEquals(0, denied.closed)
            }
        }
    }

    @Test fun unsupportedIdentityAndMetadataFailuresFailClosedAndReleaseHandle() {
        val noIdentity = FakeKernel().apply { identitySucceeds = false }
        assertFailsWith<IOException> { readWindowsExportFileIdentity(path, noIdentity) }
        assertEquals(1, noIdentity.closed)
        val noMetadata = FakeKernel().apply { metadataSucceeds = false }
        assertFailsWith<IOException> { readWindowsExportFileIdentity(path, noMetadata) }
        assertEquals(1, noMetadata.closed)
    }

    @Test fun directoriesAndReparsePointsCannotBeApproved() {
        for (attributes in listOf(0x10, 0x400, 0x410)) {
            val api = FakeKernel().apply { this.attributes = attributes }
            assertFailsWith<IOException> { readWindowsExportFileIdentity(path, api) }
            assertEquals(1, api.closed)
        }
    }
}
