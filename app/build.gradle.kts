import com.android.build.api.variant.FilterConfiguration.FilterType.ABI
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val keystoreProperties = Properties()
val keystorePropertiesFile = rootProject.file("keystore.properties")
if (keystorePropertiesFile.exists()) {
    keystorePropertiesFile.inputStream().use { keystoreProperties.load(it) }
}

val hasReleaseSigning =
    listOf("storeFile", "storePassword", "keyAlias", "keyPassword").all { key ->
        !keystoreProperties.getProperty(key).isNullOrBlank()
    }

// 手动 CI 可显式使用发布签名；配置不完整时禁止回退到调试密钥。
val useReleaseSigningForTest = providers.gradleProperty("useReleaseSigningForTest")
    .map(String::toBooleanStrict)
    .getOrElse(false)
if (useReleaseSigningForTest) {
    require(hasReleaseSigning) { "CI test signing requires complete release signing configuration" }
    require(rootProject.file(keystoreProperties.getProperty("storeFile")).isFile) {
        "CI test signing requires an existing release keystore"
    }
}

val localAppTapString =
    providers.fileContents(rootProject.layout.projectDirectory.file("local.properties"))
        .asText.orElse("").map { text ->
            Properties().apply { text.reader().use { load(it) } }
                .getProperty("COPYMANGA_TAP_STRING").orEmpty().trim()
        }
val officialAppTapString =
    providers.environmentVariable("COPYMANGA_TAP_STRING").orNull?.trim()?.takeIf { it.isNotEmpty() }
        ?: localAppTapString.get()
require(officialAppTapString.isEmpty() || officialAppTapString.matches(Regex("[0-9a-fA-F]{32}"))) {
    "COPYMANGA_TAP_STRING 必须是 32 位十六进制字符"
}
require(!providers.gradleProperty("requireOfficialAppConfig").map(String::toBooleanStrict).orElse(false).get() || officialAppTapString.isNotEmpty()) {
    "COPYMANGA_TAP_STRING is required for this build"
}

// 仅标签发布任务显式开启，普通 CI 和本地构建默认生成通用包。
val enableAbiSplits = providers.gradleProperty("enableAbiSplits")
    .map(String::toBooleanStrict)
    .getOrElse(false)

// 仅手动诊断构建启用；普通 Debug / Release 不编入计时与日志实现。
val enableStartupProbes = providers.gradleProperty("enableStartupProbes")
    .map(String::toBooleanStrict)
    .getOrElse(false)


android {
    sourceSets.getByName("main").assets.directories.add(
        (rootProject.extra["readerRestoreModelsDirectory"] as File).absolutePath
    )
    sourceSets.getByName("main").kotlin.directories.add(
        if (enableStartupProbes) "src/startupProbes/java" else "src/startupNoop/java"
    )
    namespace = "com.rchdliang.copymanga_neo"
    compileSdk = 37
    buildToolsVersion = "37.0.0"
    ndkVersion = libs.versions.ndk.get()
    externalNativeBuild {
        cmake { path = rootProject.file("native/reader-restore/CMakeLists.txt"); version = "3.22.1" }
    }

    defaultConfig {
        applicationId = "com.rchdliang.copymanga_neo"
        minSdk = 31
        targetSdk = 37
        externalNativeBuild { cmake { arguments += "-DANDROID_STL=c++_static" } }
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        versionCode = rootProject.extra["copyMangaVersionCode"] as Int
        versionName = rootProject.extra["copyMangaVersionName"] as String

        buildConfigField("String", "OFFICIAL_APP_TAP_STRING", "\"$officialAppTapString\"")
    }

    if (hasReleaseSigning) {
        signingConfigs {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            // 与 Release 使用相同的 CMP 安装标识；原 Android 分支使用 .Android。
            if (useReleaseSigningForTest) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    splits {
        abi {
            isEnable = enableAbiSplits
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86_64")
            isUniversalApk = true
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    compileSdkMinor = 0
}

// 模型 assets 同时被资源合并及 Lint 的模型生成、分析任务读取。
// 显式关联生产任务，避免 Release 完整任务图中的隐式依赖校验失败。
tasks.matching {
    it.name.matches(Regex("merge.*Assets")) || it.name.contains("lint", ignoreCase = true)
}.configureEach {
    dependsOn(rootProject.tasks.named("prepareReaderRestoreModels"))
}


androidComponents {
    onVariants(selector().withBuildType("release")) { variant ->
        variant.outputs.forEach { output ->
            val abi = output.filters.firstOrNull { it.filterType == ABI }?.identifier
            val suffix = if (enableAbiSplits) "-${abi ?: "universal"}" else ""
            output.outputFileName.set(
                output.versionName.map { versionName ->
                    "CopyManga_Neo-v$versionName$suffix-release.apk"
                }
            )
        }
    }
}

dependencies {
    implementation(project(":shared"))
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.runner)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.kotlinx.serialization.json)
    val composeBom = platform(libs.androidx.compose.bom)

    implementation(composeBom)
    implementation(libs.androidx.compose.runtime)
    // AndroidX Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    
    // Compose
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.adaptive.navigation.suite)

    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.kotlinx.collections.immutable)

    // Coil 图片加载
    implementation(libs.coil3)
    
    // Navigation
    implementation(libs.navigation.compose)
    
    // Lifecycle ViewModel
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)


    // Compose 预览工具
    debugImplementation(libs.androidx.compose.ui.tooling)

}
