import org.jetbrains.kotlin.gradle.targets.wasm.nodejs.WasmNodeJsEnvSpec
import org.jetbrains.kotlin.gradle.targets.wasm.binaryen.BinaryenEnvSpec
import org.jetbrains.kotlin.gradle.targets.wasm.binaryen.BinaryenPlugin
import org.jetbrains.kotlin.gradle.targets.wasm.nodejs.WasmNodeJsPlugin
import org.jetbrains.kotlin.gradle.targets.wasm.nodejs.WasmNodeJsRootPlugin
import org.jetbrains.kotlin.gradle.targets.wasm.yarn.WasmYarnPlugin
import org.jetbrains.kotlin.gradle.targets.wasm.yarn.WasmYarnRootEnvSpec

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
    kotlin("multiplatform") version "2.4.20" apply false
    kotlin("jvm") version "2.4.20" apply false
    kotlin("plugin.serialization") version "2.4.20" apply false
    id("org.jetbrains.compose") version "1.12.1" apply false
    id("com.android.kotlin.multiplatform.library") version "9.4.1" apply false
}

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
