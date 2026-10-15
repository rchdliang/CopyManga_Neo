package com.rchdliang.copymanga_neo.multiplatform.platform

import com.rchdliang.copymanga_neo.multiplatform.app.portableReleaseBuildInfo
import kotlin.test.*

class DesktopBuildMetadataTest {
    @Test fun portableAndDesktopDefaultsUseUnchangedReleaseIdentity() {
        assertFalse(portableReleaseBuildInfo.debug)
        assertEquals(portableReleaseBuildInfo, desktopBuildInfo(null))
        assertEquals("0.10.14", portableReleaseBuildInfo.versionName)
        assertEquals(29, portableReleaseBuildInfo.versionCode)
        assertEquals("com.rchdliang.copymanga_neo", portableReleaseBuildInfo.applicationId)
    }

    @Test fun onlyExplicitTrueEnablesDevelopmentFeatures() {
        assertTrue(desktopBuildInfo(" TRUE ").debug)
        listOf("", "false", "1", "yes", "debug").forEach { value ->
            assertFalse(desktopBuildInfo(value).debug)
        }
        assertEquals(portableReleaseBuildInfo.copy(debug = true), desktopBuildInfo("true"))
    }
}
