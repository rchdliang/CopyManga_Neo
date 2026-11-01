package com.rchdliang.copymanga_neo

import com.rchdliang.copymanga_neo.multiplatform.app.portableReleaseBuildInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class DebugApplicationIdentityTest {
    @Test fun debugBuildUsesCmpInstallIdentity() {
        assertTrue(BuildConfig.DEBUG)
        assertEquals("com.rchdliang.copymanga_neo", BuildConfig.APPLICATION_ID)
        assertFalse(BuildConfig.VERSION_NAME.contains("migrationtest"))
    }

    @Test fun packageVersionUsesHashWhileAboutVersionUsesCommitCount() {
        assertTrue(BuildConfig.VERSION_NAME.matches(Regex("[0-9]+(?:\\.[0-9]+){2,3}-[0-9a-f]{7}")))
        val aboutVersion = portableReleaseBuildInfo.versionName
        assertTrue(aboutVersion.matches(Regex("[0-9]+(?:\\.[0-9]+){2,3}\\+[0-9]+")))
        assertEquals(BuildConfig.VERSION_NAME.substringBefore('-'), aboutVersion.substringBefore('+'))
        assertEquals(BuildConfig.VERSION_CODE, portableReleaseBuildInfo.versionCode)
    }
}
