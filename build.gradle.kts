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
    kotlin("multiplatform") version "2.4.20" apply false
    kotlin("jvm") version "2.4.20" apply false
    kotlin("plugin.serialization") version "2.4.20" apply false
    id("org.jetbrains.compose") version "1.12.1" apply false
    id("com.android.kotlin.multiplatform.library") version "9.4.1" apply false
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
extra["copyMangaVersionName"] = releaseVersionName
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
