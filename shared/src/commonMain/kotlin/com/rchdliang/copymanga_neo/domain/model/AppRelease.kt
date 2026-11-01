package com.rchdliang.copymanga_neo.domain.model

data class AppRelease(
    val tag: String,
    val version: String,
    val pageUrl: String,
    val releaseNotes: String = "",
    val assets: List<AppReleaseAsset> = emptyList()
)

data class AppReleaseAsset(val name: String, val downloadUrl: String)

sealed interface AppReleaseTarget {
    data class Android(val supportedAbis: List<String>) : AppReleaseTarget
    data class Windows(val architecture: String, val packageType: WindowsReleasePackage = WindowsReleasePackage.INSTALLER) : AppReleaseTarget
}

enum class WindowsReleasePackage(val suffix: String) {
    INSTALLER("setup.exe"), PORTABLE("portable.zip")
}

/** 严格匹配本次版本的附件；缺少兼容包时由界面回退到发布页。 */
fun AppRelease.downloadUrlFor(target: AppReleaseTarget?): String? {
    fun find(pattern: String) = assets.firstOrNull { Regex(pattern).matches(it.name) }?.downloadUrl
    val releaseVersion = Regex.escape(version)
    return when (target) {
        is AppReleaseTarget.Android -> {
            val supported = target.supportedAbis.filter { it in setOf("arm64-v8a", "armeabi-v7a", "x86_64") }.distinct()
            if (supported.isEmpty()) null else (supported + "universal").firstNotNullOfOrNull { abi ->
                find("CopyMangaNeo_android_${releaseVersion}-[0-9a-f]{7}\\+[0-9]+_${Regex.escape(abi)}\\.apk")
            }
        }
        is AppReleaseTarget.Windows -> if (target.architecture !in setOf("x64", "arm64")) null else
            find("CopyMangaNeo_windows_${releaseVersion}\\+[0-9]+_${target.architecture}_${Regex.escape(target.packageType.suffix)}")
        null -> null
    }
}

object AppVersion {
    fun isNewer(latest: String, current: String): Boolean {
        // 提交短哈希只标识源码，不代表预发布或版本先后；兼容旧的 +构建号。
        fun withoutBuildSuffix(value: String) = value.trim().substringBefore('+')
            .replace(Regex("-[0-9a-fA-F]{7}$"), "")

        fun numbers(value: String): List<Int> {
            val match = Regex("^(?:ver\\.|v)?([0-9]+(?:\\.[0-9]+)*)").find(value.trim())
                ?: error("应用版本格式无效")
            return match.groupValues[1].split('.').map { it.toInt() }
        }

        val latestVersion = withoutBuildSuffix(latest)
        val currentVersion = withoutBuildSuffix(current)
        val left = numbers(latestVersion)
        val right = numbers(currentVersion)
        for (index in 0 until maxOf(left.size, right.size)) {
            val comparison = (left.getOrElse(index) { 0 }).compareTo(right.getOrElse(index) { 0 })
            if (comparison != 0) return comparison > 0
        }
        return '-' in currentVersion && '-' !in latestVersion
    }
}

object ChineseLanguageDefault {
    /** 显式字形优先；缺少字形时按中文地区推断，未知中文地区使用默认简体字形。 */
    fun enabled(languageTag: String): Boolean {
        val parts = languageTag.replace('_', '-').lowercase().split('-')
        if (parts.firstOrNull() != "zh") return false
        if ("hans" in parts) return true
        if ("hant" in parts) return false
        return parts.none { it in setOf("tw", "hk", "mo") }
    }
}
