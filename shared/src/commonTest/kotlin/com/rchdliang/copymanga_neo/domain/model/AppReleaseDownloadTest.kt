package com.rchdliang.copymanga_neo.domain.model

import kotlin.test.*

class AppReleaseDownloadTest {
    private fun release(vararg names: String) = AppRelease("v1.2.3", "1.2.3", "https://example.invalid/release",
        assets = names.map { AppReleaseAsset(it, "https://example.invalid/$it") })
    private val android = "CopyMangaNeo_android_1.2.3-1a2b3c4+234_"
    private val windows = "CopyMangaNeo_windows_1.2.3+234_"

    @Test fun androidUsesDeviceAbiOrderRatherThanAssetOrder() {
        val release = release(android + "armeabi-v7a.apk", android + "universal.apk", android + "arm64-v8a.apk")
        assertTrue(release.downloadUrlFor(AppReleaseTarget.Android(listOf("arm64-v8a", "armeabi-v7a")))!!.endsWith("arm64-v8a.apk"))
        assertTrue(release.downloadUrlFor(AppReleaseTarget.Android(listOf("armeabi-v7a")))!!.endsWith("armeabi-v7a.apk"))
    }

    @Test fun androidFallsBackToUniversalButNeverAnotherArchitecture() {
        val release = release(android + "arm64-v8a.apk", android + "universal.apk")
        assertTrue(release.downloadUrlFor(AppReleaseTarget.Android(listOf("x86_64")))!!.endsWith("universal.apk"))
        assertNull(release(android + "arm64-v8a.apk").downloadUrlFor(AppReleaseTarget.Android(listOf("x86_64"))))
        assertNull(release.downloadUrlFor(AppReleaseTarget.Android(listOf("riscv64"))))
    }

    @Test fun checksumsOtherVersionsAndOtherProductsAreNotInstallers() {
        val release = release(android + "arm64-v8a.apk.sha256",
            "CopyMangaNeo_android_1.2.30-1a2b3c4+234_arm64-v8a.apk",
            "OtherApp_android_1.2.3-1a2b3c4+234_arm64-v8a.apk")
        assertNull(release.downloadUrlFor(AppReleaseTarget.Android(listOf("arm64-v8a"))))
    }

    @Test fun windowsSelectsRequestedFormatAndExactArchitecture() {
        val release = release(windows + "arm64_setup.exe", windows + "x64_portable.zip", windows + "x64_setup.exe")
        assertTrue(release.downloadUrlFor(AppReleaseTarget.Windows("x64"))!!.endsWith("x64_setup.exe"))
        assertTrue(release.downloadUrlFor(AppReleaseTarget.Windows("x64", WindowsReleasePackage.PORTABLE))!!.endsWith("x64_portable.zip"))
        assertTrue(release.downloadUrlFor(AppReleaseTarget.Windows("arm64"))!!.endsWith("arm64_setup.exe"))
        assertNull(release.downloadUrlFor(AppReleaseTarget.Windows("arm64", WindowsReleasePackage.PORTABLE)))
        assertNull(release.downloadUrlFor(AppReleaseTarget.Windows("x86")))
    }

    @Test fun missingAssetsAndUnsupportedPlatformsLeaveTheReleasePageAvailable() {
        assertNull(release().downloadUrlFor(AppReleaseTarget.Windows("x64")))
        assertNull(release(android + "universal.apk").downloadUrlFor(null))
    }
}
