import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.util.Properties
import org.gradle.work.DisableCachingByDefault

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.android.kotlin.multiplatform.library)
}

// Explicit portable-JAR target; native packaging still requires a matching host.
val portableDesktopTarget = providers.gradleProperty("copymanga.desktopTarget").orNull

/** 由版本配置生成各平台共同使用的常量，产物仅位于 build 目录。 */
@CacheableTask
abstract class GenerateReleaseVersion : DefaultTask() {
    @get:Input abstract val releaseName: Property<String>
    @get:Input abstract val releaseCode: Property<Int>
    @get:OutputDirectory abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        val target = outputDirectory.file("com/rchdliang/copymanga_neo/multiplatform/app/ReleaseVersion.kt").get().asFile
        target.parentFile.mkdirs()
        target.writeText("""
            package com.rchdliang.copymanga_neo.multiplatform.app

            // Generated from version.properties. Do not edit.
            internal object ReleaseVersion {
                const val NAME = "${releaseName.get()}"
                const val CODE = ${releaseCode.get()}
            }
        """.trimIndent() + "\n")
    }
}

val generateReleaseVersion = tasks.register<GenerateReleaseVersion>("generateReleaseVersion") {
    releaseName.set(rootProject.extra["copyMangaVersionName"] as String)
    releaseCode.set(rootProject.extra["copyMangaVersionCode"] as Int)
    outputDirectory.set(layout.buildDirectory.dir("generated/releaseVersion/commonMain"))
}

/** 桌面安装包内置协议配置，生成文件不进入源码仓库或浏览器源码集。 */
@DisableCachingByDefault(because = "Generated source contains private build configuration")
abstract class GenerateOfficialAppConfig : DefaultTask() {
    @get:Input abstract val tapString: Property<String>
    @get:Input abstract val requireConfigured: Property<Boolean>
    @get:OutputDirectory abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        val value = tapString.get().trim()
        require(value.isEmpty() || value.matches(Regex("[0-9a-fA-F]{32}"))) {
            "COPYMANGA_TAP_STRING must contain 32 hexadecimal characters"
        }
        require(!requireConfigured.get() || value.isNotEmpty()) {
            "COPYMANGA_TAP_STRING is required for this build"
        }
        val target = outputDirectory.file("com/rchdliang/copymanga_neo/multiplatform/app/OfficialAppBuildConfig.kt").get().asFile
        target.parentFile.mkdirs()
        target.writeText("""
            package com.rchdliang.copymanga_neo.multiplatform.app

            // 编译时生成；不要将此文件提交到 Git。
            object OfficialAppBuildConfig {
                const val TAP_STRING = "$value"
            }
        """.trimIndent() + "\n")
    }
}

val localOfficialAppTapString = providers.fileContents(rootProject.layout.projectDirectory.file("local.properties"))
    .asText.orElse("").map { text ->
        Properties().apply { text.reader().use { load(it) } }
            .getProperty("COPYMANGA_TAP_STRING").orEmpty().trim()
    }
val generateOfficialAppConfig = tasks.register<GenerateOfficialAppConfig>("generateOfficialAppConfig") {
    tapString.set(providers.environmentVariable("COPYMANGA_TAP_STRING").map(String::trim)
        .filter { it.isNotEmpty() }.orElse(localOfficialAppTapString))
    requireConfigured.set(providers.gradleProperty("requireOfficialAppConfig").map(String::toBooleanStrict).orElse(false))
    outputDirectory.set(layout.buildDirectory.dir("generated/officialAppConfig/desktopMain"))
}

kotlin {
    // 平台异常类型需要 expect/actual 类；显式确认使用该 Beta 语言特性。
    compilerOptions { freeCompilerArgs.add("-Xexpect-actual-classes") }
    android {
        namespace = "com.rchdliang.copymanga_neo.shared"
        compileSdk = libs.versions.androidCompileSdk.get().toInt()
        buildToolsVersion = libs.versions.androidBuildTools.get()
        minSdk = libs.versions.androidMinSdk.get().toInt()
        androidResources { enable = true }
        withHostTest {}
        compilerOptions { jvmTarget.set(JvmTarget.JVM_21) }
    }
    jvm("desktop") { compilerOptions { jvmTarget.set(JvmTarget.JVM_21) } }
    iosArm64()
    iosSimulatorArm64()
    targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget>().configureEach {
        binaries.framework { baseName = "CopyMangaShared"; isStatic = true }
    }
    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs { browser { commonWebpackConfig { outputFileName = "copymanga-neo.js" } }; binaries.executable() }
    sourceSets {
        commonMain {
            kotlin.srcDir(generateReleaseVersion.flatMap { it.outputDirectory })
        }
        named("desktopMain") { kotlin.srcDir(generateOfficialAppConfig.flatMap { it.outputDirectory }) }
        commonMain.dependencies {
            // Production shared NavHost; platform inputs use the standalone dispatcher bridge.
            implementation(libs.multiplatform.navigation.compose)
            // NavHost exposes these only at runtime; the input bridge imports their public APIs.
            implementation(libs.multiplatform.navigationevent.compose)
            implementation(libs.androidx.navigationevent)
            implementation(libs.compose.multiplatform.runtime)
            implementation(libs.compose.multiplatform.foundation)
            implementation(libs.compose.multiplatform.ui)
            implementation(libs.compose.multiplatform.resources)
            implementation(libs.compose.multiplatform.ui.tooling.preview)
            implementation(libs.compose.multiplatform.material3)
            implementation(libs.compose.multiplatform.material.icons.extended)
            implementation(libs.multiplatform.lifecycle.viewmodel.compose)
            implementation(libs.multiplatform.lifecycle.runtime.compose)
            implementation(libs.multiplatform.lifecycle.viewmodel.savedstate)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.collections.immutable)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.encoding)
            implementation(libs.coil.compose)
            implementation(libs.coil.network.ktor3)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.ktor.client.mock)
            implementation(libs.kotlinx.coroutines.test)
        }
        getByName("androidMain").kotlin.srcDir("src/jvmMain/kotlin")
        getByName("desktopMain").kotlin.srcDir("src/jvmMain/kotlin")
        androidMain.dependencies {
            implementation(libs.androidx.exifinterface)
            // Compile shared Android UI against the same Material3 ABI packaged by :app.
            // CMP's transitive alpha22 has experimental methods removed in beta01 (ToggleButton).
            implementation(libs.androidx.compose.material3)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.okhttp)
            implementation(libs.androidx.core.ktx)
        }
        getByName("desktopMain").dependencies {
            implementation(when (portableDesktopTarget) {
                null -> compose.desktop.currentOs
                "windows-x64" -> libs.compose.multiplatform.windows.x64.get()
                "windows-arm64" -> libs.compose.multiplatform.windows.arm64.get()
                else -> error("copymanga.desktopTarget must be windows-x64 or windows-arm64")
            })
            implementation(libs.kotlinx.coroutines.swing)
            implementation(libs.ktor.client.cio)
            implementation(libs.jna)
        }
        getByName("desktopMain").resources.srcDir(rootProject.extra["readerRestoreModelsDirectory"] as File)
        getByName("desktopMain").resources.srcDir(rootProject.extra["readerRestoreDesktopDirectory"] as File)
        getByName("desktopTest").dependencies {
            implementation(libs.compose.multiplatform.ui.test)
        }
        iosMain.dependencies { implementation(libs.ktor.client.darwin) }
        wasmJsMain.dependencies { implementation(libs.ktor.client.js) }
    }
}

dependencies {
    // Common-code previews render through the shared Android target, not :app's
    // debug classpath. The Android KMP plugin has no debugImplementation variant.
    androidRuntimeClasspath(libs.compose.multiplatform.ui.tooling)
}

compose.resources {
    packageOfResClass = "com.rchdliang.copymanga_neo.multiplatform.resources"
    publicResClass = true
}

// 桌面资源由这两个任务生成，资源处理必须先等待生成完成。
tasks.matching { it.name == "desktopProcessResources" }.configureEach {
    dependsOn(rootProject.tasks.named("prepareReaderRestoreModels"))
    if (rootProject.extra["readerRestoreDesktopSupported"] as Boolean) dependsOn(rootProject.tasks.named("prepareReaderRestoreDesktop"))
}

// Keep native test extraction in the build directory, including read-only-home CI workers.
tasks.withType<Test>().configureEach {
    systemProperty("skiko.data.path", layout.buildDirectory.dir("skiko-test-cache").get().asFile.absolutePath)
    systemProperty("copymanga.expectedVersionName", rootProject.extra["copyMangaVersionName"] as String)
    systemProperty("copymanga.expectedVersionCode", rootProject.extra["copyMangaVersionCode"] as Int)
}

// Selecting foreign natives is for packaging, never for executing host JVM tests.
if (portableDesktopTarget != null) {
    val selectedTarget = portableDesktopTarget
    val hostArch = when (System.getProperty("os.arch").lowercase()) {
        "amd64", "x86_64" -> "x64"
        "aarch64", "arm64" -> "arm64"
        else -> "unsupported"
    }
    val matchesHost = System.getProperty("os.name").lowercase().startsWith("windows") &&
        selectedTarget == "windows-$hostArch"
    tasks.withType<Test>().configureEach {
        if (!matchesHost) {
            doFirst {
                error("$selectedTarget natives cannot execute shared desktop tests on this host; remove copymanga.desktopTarget for host tests")
            }
        }
    }
}
