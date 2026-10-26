pluginManagement {
    repositories { mavenCentral(); google(); gradlePluginPortal() }
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id.startsWith("com.android.")) useModule("com.android.tools.build:gradle:${requested.version}")
        }
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { mavenCentral(); google() }
}
rootProject.name = "CopyManga_Neo_Multiplatform"
include(":shared", ":desktopApp")
// Windows x64 正式构建默认 Native Image。其他宿主保留原来的桌面方案。
val windowsX64 = System.getProperty("os.name").startsWith("Windows", true) &&
    System.getProperty("os.arch").lowercase() in setOf("amd64", "x86_64")
val desktopRuntime = providers.gradleProperty("desktopRuntime").getOrElse(if (windowsX64) "native" else "jvm")
require(desktopRuntime in setOf("native", "jvm")) { "desktopRuntime must be native or jvm" }
require(desktopRuntime != "native" || windowsX64) { "Native Image distribution currently requires Windows x64" }
if (desktopRuntime == "native") require(providers.gradleProperty("copymanga.desktopTarget").orNull in setOf(null, "windows-x64")) {
    "Native Image currently targets Windows x64; select desktopRuntime=jvm for another portable JAR target"
}
if (desktopRuntime == "jvm") project(":desktopApp").buildFileName = "build-jvm.gradle.kts"
// Desktop/common builds need no Android SDK. Enable Android explicitly in CI/IDE.
if (providers.gradleProperty("enableAndroid").orNull != "false") include(":app")
