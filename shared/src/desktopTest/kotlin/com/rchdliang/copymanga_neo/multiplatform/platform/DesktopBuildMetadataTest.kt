package com.rchdliang.copymanga_neo.multiplatform.platform

import com.rchdliang.copymanga_neo.multiplatform.app.portableReleaseBuildInfo
import kotlin.test.*

class DesktopBuildMetadataTest {
    @Test fun portableAndDesktopDefaultsUseConfiguredReleaseIdentity() {
        assertFalse(portableReleaseBuildInfo.debug)
        assertEquals(portableReleaseBuildInfo, desktopBuildInfo(null))
        assertEquals(System.getProperty("copymanga.expectedVersionName"), portableReleaseBuildInfo.versionName)
        assertEquals(System.getProperty("copymanga.expectedVersionCode").toInt(), portableReleaseBuildInfo.versionCode)
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
