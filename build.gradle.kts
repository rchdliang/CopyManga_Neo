@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.targets.wasm.nodejs.WasmNodeJsEnvSpec
import org.jetbrains.kotlin.gradle.targets.wasm.binaryen.BinaryenEnvSpec
import org.jetbrains.kotlin.gradle.targets.wasm.binaryen.BinaryenPlugin
import org.jetbrains.kotlin.gradle.targets.wasm.nodejs.WasmNodeJsPlugin
import org.jetbrains.kotlin.gradle.targets.wasm.nodejs.WasmNodeJsRootPlugin
import org.jetbrains.kotlin.gradle.targets.wasm.yarn.WasmYarnPlugin
import org.jetbrains.kotlin.gradle.targets.wasm.yarn.WasmYarnRootEnvSpec
import java.util.Properties

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    // 覆盖 Compose 自动带入的 1.2.0，使用已修复 Gradle 9.8 弃用 API 的版本。
    alias(libs.plugins.compose.hot.reload) apply false
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
}


apply(from = "gradle/reader-restore.gradle.kts")

// 同一文件也由 iOS xcconfig 直接包含；所有发布版本只在此文件中维护。
val releaseProperties = providers.fileContents(layout.projectDirectory.file("version.properties"))
    .asText.map { text -> Properties().apply { text.reader().use { load(it) } } }.get()
val releaseVersionBase = releaseProperties.getProperty("COPYMANGA_VERSION_BASE")?.trim().orEmpty()
val releaseVersionHotfix = releaseProperties.getProperty("COPYMANGA_VERSION_HOTFIX")?.trim().orEmpty()
val releaseVersionName = releaseVersionBase + releaseVersionHotfix.takeIf { it.isNotEmpty() }?.let { ".$it" }.orEmpty()
val releaseVersionCode = releaseProperties.getProperty("COPYMANGA_VERSION_CODE")?.trim()?.toIntOrNull()
require(releaseVersionBase.matches(Regex("[0-9]+\\.[0-9]+\\.[0-9]+"))) {
    "version.properties: COPYMANGA_VERSION_BASE 必须是三段数值版本号"
}
require(releaseVersionHotfix.isEmpty() || releaseVersionHotfix.matches(Regex("[0-9]+"))) {
    "version.properties: COPYMANGA_VERSION_HOTFIX 必须为空或数值修订号"
}
require(releaseVersionCode != null && releaseVersionCode in 1..2_100_000_000) {
    "version.properties: COPYMANGA_VERSION_CODE 必须是有效的正整数构建号"
}
// 关于页继续使用完整历史的提交数；Provider 会跟踪 Git 输出并使配置缓存失效。
val shallowRepository = providers.exec {
    workingDir(rootDir)
    commandLine("git", "rev-parse", "--is-shallow-repository")
}.standardOutput.asText.get().trim()
require(shallowRepository == "false") {
    "构建版本需要完整 Git 历史；请执行 git fetch --unshallow，CI checkout 使用 fetch-depth: 0"
}
val gitCommitCount = providers.exec {
    workingDir(rootDir)
    commandLine("git", "rev-list", "--count", "HEAD")
}.standardOutput.asText.get().trim()
require(gitCommitCount.toLongOrNull()?.let { it > 0 } == true) { "无法读取有效的 Git 提交数量" }

// 系统包管理器版本使用提交哈希的前七位。
val gitCommitHash = providers.exec {
    workingDir(rootDir)
    commandLine("git", "rev-parse", "--verify", "HEAD")
}.standardOutput.asText.get().trim()
require(gitCommitHash.matches(Regex("[0-9a-f]{40}|[0-9a-f]{64}"))) { "无法读取有效的 Git 提交哈希" }

// 分别维护数字发布版本、应用内显示版本和 Android 系统安装包版本。
extra["copyMangaVersionName"] = releaseVersionName
extra["copyMangaBuildVersionName"] = "$releaseVersionName+$gitCommitCount"
extra["copyMangaPackageVersionName"] = "$releaseVersionName-${gitCommitHash.take(7)}"
extra["copyMangaAndroidArtifactVersion"] = "$releaseVersionName-${gitCommitHash.take(7)}+$gitCommitCount"
extra["copyMangaVersionCode"] = releaseVersionCode
// Windows 安装包只支持三段数值，使用递增构建号保证修订版本仍能升级。
extra["copyMangaWindowsVersion"] = releaseVersionBase.substringBeforeLast('.') + ".$releaseVersionCode"

// Use developer/CI-installed tools so Kotlin does not add Ivy distribution repositories
// forbidden by settings.gradle.kts's FAIL_ON_PROJECT_REPOS policy. No machine paths
// are committed: override copymanga.nodeCommand, copymanga.yarnCommand or
// copymanga.binaryenCommand with -P when the tools are not on PATH.
val configuredNodeCommand = providers.gradleProperty("copymanga.nodeCommand").orElse("node")
val configuredYarnCommand = providers.gradleProperty("copymanga.yarnCommand").orElse("yarn")
val configuredBinaryenCommand = providers.gradleProperty("copymanga.binaryenCommand").orElse("wasm-opt")

plugins.withType<WasmNodeJsRootPlugin> {
    rootProject.extensions.configure<WasmNodeJsEnvSpec> {
        download.set(false)
        command.set(configuredNodeCommand)
    }
}
// Webpack reads the compilation project's environment, while npm installation uses
// the root environment. Configure both because subproject defaults come from the
// legacy root extension rather than inheriting the root environment spec.
allprojects {
    // Production optimization needs wasm-opt; preserve FAIL_ON_PROJECT_REPOS here too.
    plugins.withType<BinaryenPlugin> {
        extensions.configure<BinaryenEnvSpec> {
            download.set(false)
            command.set(configuredBinaryenCommand)
        }
    }
    plugins.withType<WasmNodeJsPlugin> {
        extensions.configure<WasmNodeJsEnvSpec> {
            download.set(false)
            command.set(configuredNodeCommand)
        }
    }
}
plugins.withType<WasmYarnPlugin> {
    rootProject.extensions.configure<WasmYarnRootEnvSpec> {
        download.set(false)
        command.set(configuredYarnCommand)
    }
}
