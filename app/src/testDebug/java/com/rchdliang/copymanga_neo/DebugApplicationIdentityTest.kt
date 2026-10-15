package com.rchdliang.copymanga_neo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugApplicationIdentityTest {
    @Test fun debugBuildHasIndependentInstallIdentity() {
        assertTrue(BuildConfig.DEBUG)
        assertEquals("com.rchdliang.copymanga_neo.migrationtest", BuildConfig.APPLICATION_ID)
        assertTrue(BuildConfig.VERSION_NAME.endsWith("-migrationtest"))
    }
}
