package com.rchdliang.copymanga_neo.multiplatform.platform

import com.rchdliang.copymanga_neo.multiplatform.app.portableReleaseBuildInfo
import com.rchdliang.copymanga_neo.multiplatform.ui.profile.AppBuildInfo

/** Explicit developer override. Missing/invalid values remain release-safe, including packaged apps. */
internal fun desktopBuildInfo(
    debugProperty: String? = System.getProperty("copymanga.debug")
): AppBuildInfo = portableReleaseBuildInfo.copy(
    debug = debugProperty?.trim()?.equals("true", ignoreCase = true) == true
)
