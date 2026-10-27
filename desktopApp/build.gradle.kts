import dev.nucleusframework.desktop.application.dsl.NativeImageMarch
import dev.nucleusframework.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    alias(libs.plugins.nucleus)
}

kotlin {
    jvmToolchain(21)
    sourceSets.main {
        kotlin.srcDir("src/native/kotlin")
        kotlin.exclude("**/Main.kt")
    }
}
dependencies {
    implementation(project(":shared"))
    implementation(libs.compose.multiplatform.windows.x64)
    implementation("org.jetbrains.compose.material3:material3:1.12.0-alpha03")
    implementation("org.jetbrains.androidx.lifecycle:lifecycle-viewmodel:2.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.11.0")
    implementation(libs.nucleus.application)
    implementation(libs.nucleus.window.tao)
    implementation(libs.nucleus.taskbar.tao)
    implementation(libs.nucleus.tray)
    implementation(libs.nucleus.notifications)
}
tasks.processResources { from("icons/copymanga-neo.ico") }

nucleus.application {
    mainClass = "com.rchdliang.copymanga_neo.desktop.NativeMainKt"
    graalvm {
        isEnabled = true
        imageName = "CopyMangaNeo"
        toolchain { version = libs.versions.graalvm.get() }
        march = NativeImageMarch.COMPATIBILITY
        allCharsets = true
        buildArgs.addAll("-J-Xmx" + providers.gradleProperty("nativeBuildHeap").getOrElse("8g"), "--parallelism=2", "--enable-native-access=ALL-UNNAMED")
    }
    nativeDistributions {
        packageName = "CopyMangaNeo"
        packageVersion = rootProject.extra["copyMangaVersionName"] as String
        description = "CopyManga Neo multiplatform client"
        vendor = "rchdliang"
        targetFormats(TargetFormat.Portable)
        windows {
            packageVersion = rootProject.extra["copyMangaWindowsVersion"] as String
            iconFile.set(project.file("icons/copymanga-neo.ico"))
        }
    }
}

// 常用入口统一到 Native Image；JVM 对照版本必须显式选择 desktopRuntime=jvm。
tasks.matching { it.name in setOf("run", "runDistributable", "createDistributable",
    "runRelease", "runReleaseDistributable", "createReleaseDistributable",
    "packageDistributionForCurrentOS", "packageReleaseDistributionForCurrentOS") }.configureEach {
    val nativeTask = if (name.startsWith("create") || name.startsWith("package")) "createGraalvmNativeDistributable"
        else "runGraalvmNativeDistributable"
    enabled = false // 保留依赖入口，禁用插件注册的 JVM 执行动作。
    setActions(emptyList())
    setDependsOn(listOf(nativeTask))
    description = "Default Windows Native Image entry point: $nativeTask"
}
tasks.matching { it.name in setOf("createRuntimeImage", "createReleaseRuntimeImage") }.configureEach {
    enabled = false
}
tasks.named("assemble") { dependsOn("createGraalvmNativeDistributable") }
tasks.register<Exec>("packageInnoSetup") {
    group = "distribution"
    description = "Build the Windows x64 Native Image installer"
    dependsOn("createGraalvmNativeDistributable")
    workingDir(rootProject.projectDir)
    commandLine("powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File",
        rootProject.file(".github/scripts/build-windows-installer.ps1").absolutePath,
        "-AppImage", layout.buildDirectory.dir("compose/binaries/main/graalvm-app/CopyMangaNeo").get().asFile.absolutePath,
        "-AppVersion", rootProject.extra["copyMangaVersionName"] as String,
        "-Architecture", "x64", "-Runtime", "native",
        "-OutputDirectory", layout.buildDirectory.dir("installer").get().asFile.absolutePath)
}
