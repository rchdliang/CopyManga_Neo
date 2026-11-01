package com.rchdliang.copymanga_neo.multiplatform.platform

import com.rchdliang.copymanga_neo.domain.model.AppReleaseTarget

/** 不将 ARM 或未知架构静默当作 x64，未提供对应包时回到发布页。 */
internal fun desktopAppReleaseTarget(osName: String, osArch: String): AppReleaseTarget.Windows? {
    if (!osName.startsWith("Windows", ignoreCase = true)) return null
    val architecture = when (osArch.lowercase()) {
        "amd64", "x86_64" -> "x64"
        "aarch64", "arm64" -> "arm64"
        else -> return null
    }
    return AppReleaseTarget.Windows(architecture)
}
