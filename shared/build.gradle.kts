import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.android.kotlin.multiplatform.library")
}

// Explicit portable-JAR target; native packaging still requires a matching host.
val portableDesktopTarget = providers.gradleProperty("copymanga.desktopTarget").orNull



kotlin {
    android {
        namespace = "com.rchdliang.copymanga_neo.shared"
        compileSdk = 37
        buildToolsVersion = "37.0.0"
        minSdk = 31
        androidResources { enable = true }
        compilerOptions { jvmTarget.set(JvmTarget.JVM_21) }
    }
    jvm("desktop") { compilerOptions { jvmTarget.set(JvmTarget.JVM_21) } }
    iosArm64()
    iosSimulatorArm64()
    targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget>().configureEach {
        binaries.framework { baseName = "CopyMangaShared"; isStatic = true }
    }
    wasmJs { browser { commonWebpackConfig { outputFileName = "copymanga-neo.js" } }; binaries.executable() }
    sourceSets {
        commonMain.dependencies {
            // Production shared NavHost; platform inputs use the standalone dispatcher bridge.
            implementation("org.jetbrains.androidx.navigation:navigation-compose:2.10.0-beta01")
            // NavHost exposes these only at runtime; the input bridge imports their public APIs.
            implementation("org.jetbrains.androidx.navigationevent:navigationevent-compose:1.1.0")
            implementation("androidx.navigationevent:navigationevent:1.1.1")
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation("org.jetbrains.compose.ui:ui-tooling-preview:1.12.1")
            implementation("org.jetbrains.compose.material3:material3:1.12.0-alpha03")
            implementation("org.jetbrains.compose.material:material-icons-extended:1.7.3")
            implementation("org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
            implementation("org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
            implementation("org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-savedstate:2.11.0")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
            implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.8.0")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
            implementation("org.jetbrains.kotlinx:kotlinx-collections-immutable:0.5.2")
            implementation("io.ktor:ktor-client-core:3.6.0")
            implementation("io.ktor:ktor-client-encoding:3.6.0")
            implementation("io.coil-kt.coil3:coil-compose:3.6.3")
            implementation("io.coil-kt.coil3:coil-network-ktor3:3.6.3")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("io.ktor:ktor-client-mock:3.6.0")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
        }
        androidMain.dependencies {
            // Compile shared Android UI against the same Material3 ABI packaged by :app.
            // CMP's transitive alpha22 has experimental methods removed in beta01 (ToggleButton).
            implementation(libs.androidx.compose.material3)
            implementation("io.ktor:ktor-client-okhttp:3.6.0")
            implementation("androidx.core:core-ktx:1.19.1")
        }
        getByName("desktopMain").dependencies {
            implementation(when (portableDesktopTarget) {
                null -> compose.desktop.currentOs
                "windows-x64" -> compose.desktop.windows_x64
                "windows-arm64" -> compose.desktop.windows_arm64
                else -> error("copymanga.desktopTarget must be windows-x64 or windows-arm64")
            })
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.11.0")
            implementation("io.ktor:ktor-client-cio:3.6.0")
            implementation("net.java.dev.jna:jna:5.19.1")
        }
        getByName("desktopTest").dependencies {
            implementation("org.jetbrains.compose.ui:ui-test:1.12.1")
        }
        iosMain.dependencies { implementation("io.ktor:ktor-client-darwin:3.6.0") }
        wasmJsMain.dependencies { implementation("io.ktor:ktor-client-js:3.6.0") }
    }
}

compose.resources {
    packageOfResClass = "com.rchdliang.copymanga_neo.multiplatform.resources"
    publicResClass = true
}

// Keep native test extraction in the build directory, including read-only-home CI workers.
tasks.withType<Test>().configureEach {
    systemProperty("skiko.data.path", layout.buildDirectory.dir("skiko-test-cache").get().asFile.absolutePath)
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
