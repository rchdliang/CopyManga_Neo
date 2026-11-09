import dev.nucleusframework.desktop.application.dsl.NativeImageMarch
import dev.nucleusframework.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.nucleus)
}

val hostIsMacOS = System.getProperty("os.name").startsWith("Mac", true)

kotlin {
    jvmToolchain(21)
    sourceSets.main {
        kotlin.srcDir("src/native/kotlin")
        kotlin.exclude("**/Main.kt")
    }
}
dependencies {
    implementation(project(":shared"))
    implementation(compose.desktop.currentOs)
    implementation(libs.compose.multiplatform.material3)
    implementation(libs.multiplatform.lifecycle.viewmodel)
    implementation(libs.kotlinx.coroutines.swing)
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
        isEnabled = !hostIsMacOS
        imageName = "CopyMangaNeo"
        toolchain { version = libs.versions.graalvm.get() }
        march = NativeImageMarch.COMPATIBILITY
        allCharsets = true
        buildArgs.addAll(
            "-J-Xmx" + providers.gradleProperty("nativeBuildHeap").getOrElse("8g"),
            "--parallelism=2",
            "--enable-native-access=ALL-UNNAMED"
        )
    }
    nativeDistributions {
        // JVM 分发包保留动态加载的字符集和桌面运行所需模块。
        modules("java.instrument", "java.management", "jdk.unsupported", "jdk.charsets")
        packageName = "CopyMangaNeo"
        packageVersion = rootProject.extra["copyMangaVersionName"] as String
        description = "CopyManga Neo multiplatform client"
        vendor = "rchdliang"
        if (hostIsMacOS) targetFormats(TargetFormat.Dmg, TargetFormat.Zip)
        else targetFormats(TargetFormat.Portable)
        macOS {
            bundleID = "com.rchdliang.copymanga_neo.desktop"
            dockName = "CopyManga Neo"
            appCategory = "public.app-category.entertainment"
            minimumSystemVersion = "12.0"
            // macOS 安装版本只能有三段；修订号继续通过构建号区分。
            packageVersion = rootProject.extra["copyMangaWindowsVersion"] as String
            packageBuildVersion = (rootProject.extra["copyMangaVersionCode"] as Int).toString()
            iconFile.set(project.file("icons/copymanga-neo.icns"))
        }
        windows {
            packageVersion = rootProject.extra["copyMangaWindowsVersion"] as String
            iconFile.set(project.file("icons/copymanga-neo.ico"))
        }
    }
}

// 保留插件的 JVM 开发/分发任务；原生编译只由 Graalvm 专用任务或安装包入口触发。
// 两种运行方式使用同一个 NativeMainKt 与 Nucleus Tao 窗口，无需切换项目模型。
tasks.matching { it.name == "run" }.configureEach {
    description = "Run the Nucleus/Tao desktop app on the JVM (no Native Image build)"
}
if (!hostIsMacOS) {
    tasks.register<Exec>("packageInnoSetup") {
        group = "distribution"
        description = "Build the Windows x64 Native Image installer"
        dependsOn("createGraalvmNativeDistributable")
        workingDir(rootProject.projectDir)
        commandLine(
            "powershell.exe",
            "-NoProfile",
            "-ExecutionPolicy",
            "Bypass",
            "-File",
            rootProject.file(".github/scripts/build-windows-installer.ps1").absolutePath,
            "-AppImage",
            layout.buildDirectory.dir("compose/binaries/main/graalvm-app/CopyMangaNeo").get().asFile.absolutePath,
            "-AppVersion",
            rootProject.extra["copyMangaVersionName"] as String,
            "-ArtifactVersion",
            rootProject.extra["copyMangaBuildVersionName"] as String,
            "-Architecture",
            "x64",
            "-Runtime",
            "native",
            "-OutputDirectory",
            layout.buildDirectory.dir("installer").get().asFile.absolutePath
        )
    }

    // 与安装程序复用同一次 Native Image 构建，完整保留运行库和资源。
    tasks.register<Exec>("packageWindowsPortableZip") {
        group = "distribution"
        description = "Build the Windows x64 native portable ZIP"
        dependsOn("createGraalvmNativeDistributable")
        workingDir(rootProject.projectDir)
        commandLine(
            "powershell.exe",
            "-NoProfile",
            "-ExecutionPolicy",
            "Bypass",
            "-File",
            rootProject.file(".github/scripts/build-windows-portable.ps1").absolutePath,
            "-AppImage",
            layout.buildDirectory.dir("compose/binaries/main/graalvm-app/CopyMangaNeo").get().asFile.absolutePath,
            "-AppVersion",
            rootProject.extra["copyMangaVersionName"] as String,
            "-ArtifactVersion",
            rootProject.extra["copyMangaBuildVersionName"] as String,
            "-Architecture",
            "x64",
            "-OutputDirectory",
            layout.buildDirectory.dir("installer").get().asFile.absolutePath
        )
    }
}
