package com.rchdliang.copymanga_neo

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
}
