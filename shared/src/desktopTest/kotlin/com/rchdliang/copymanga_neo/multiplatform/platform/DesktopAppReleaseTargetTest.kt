package com.rchdliang.copymanga_neo.multiplatform.platform

import kotlin.test.*

class DesktopAppReleaseTargetTest {
    @Test fun windowsArchitectureAliasesAreNormalizedWithoutGuessing() {
        for (arch in listOf("amd64", "x86_64")) assertEquals("x64", desktopAppReleaseTarget("Windows 11", arch)?.architecture)
        for (arch in listOf("aarch64", "arm64")) assertEquals("arm64", desktopAppReleaseTarget("Windows 11", arch)?.architecture)
        assertNull(desktopAppReleaseTarget("Windows 11", "x86"))
        assertNull(desktopAppReleaseTarget("Linux", "amd64"))
        assertNull(desktopAppReleaseTarget("Mac OS X", "aarch64"))
    }
}
