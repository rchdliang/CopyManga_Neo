pluginManagement {
    repositories { mavenCentral(); google(); gradlePluginPortal() }
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id.startsWith("com.android.")) useModule("com.android.tools.build:gradle:${requested.version}")
        }
    }
}
plugins {
    // 热重载需要 JetBrains Runtime；允许 Gradle 按需解析并下载对应工具链。
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { mavenCentral(); google() }
}
rootProject.name = "CopyManga_Neo_Multiplatform"
include(":shared", ":desktopApp")
// Windows x64 与 macOS 共用 Nucleus/Tao；macOS 使用随包 JVM，Linux 保留 Compose/AWT。
val windowsX64 = System.getProperty("os.name").startsWith("Windows", true) &&
    System.getProperty("os.arch").lowercase() in setOf("amd64", "x86_64")
val macOS = System.getProperty("os.name").startsWith("Mac", true)
val desktopRuntime = providers.gradleProperty("desktopRuntime").getOrElse(if (windowsX64) "native" else "jvm")
require(desktopRuntime in setOf("native", "jvm")) { "desktopRuntime must be native or jvm" }
require(desktopRuntime != "native" || windowsX64) { "Native Image distribution currently requires Windows x64" }
if (desktopRuntime == "native") require(providers.gradleProperty("copymanga.desktopTarget").orNull in setOf(null, "windows-x64")) {
    "Native Image currently targets Windows x64; select desktopRuntime=jvm for another portable JAR target"
}
if (macOS) require(providers.gradleProperty("copymanga.desktopTarget").orNull == null) {
    "macOS Nucleus packages require a matching host JDK; do not select foreign desktop natives"
}
if (desktopRuntime == "jvm" && !macOS) project(":desktopApp").buildFileName = "build-jvm.gradle.kts"
// 默认包含 Android 应用；关闭此开关只排除 :app，shared 仍声明 Android 目标。
val enableAndroid = providers.gradleProperty("enableAndroid").map(String::toBooleanStrict).getOrElse(true)
if (enableAndroid) include(":app")
