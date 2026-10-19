import com.android.build.api.variant.FilterConfiguration.FilterType.ABI
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

configurations.all {
    resolutionStrategy {
        force(libs.kotlinMetadataJvm)
    }
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

// 仅标签发布任务显式开启，普通 CI 和本地构建默认生成通用包。
val enableAbiSplits = providers.gradleProperty("enableAbiSplits")
    .map(String::toBooleanStrict)
    .getOrElse(false)

android {
    namespace = "com.rchdliang.copymanga_neo"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.rchdliang.copymanga_neo.Android"
        minSdk = 31
        targetSdk = 37
        versionCode = 35
        versionName = "0.10.20"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

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

androidComponents {
    onVariants(selector().withBuildType("release")) { variant ->
        variant.outputs.forEach { output ->
            val abi = output.filters.firstOrNull { it.filterType == ABI }?.identifier
            val suffix = if (enableAbiSplits) "-${abi ?: "universal"}" else ""
            output.outputFileName.set(
                output.versionName.map { versionName ->
                    "CopyManga_Neo-Android-v$versionName$suffix-release.apk"
                }
            )
        }
    }
}

hilt {
    enableAggregatingTask = true
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.room.testing)
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


    // Hilt 依赖注入
    implementation(libs.hilt.android)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.kotlinx.collections.immutable)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    
    // Retrofit + OkHttp 网络请求
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp.logging)
    
    // Coil 图片加载
    implementation(libs.coil.compose)
    
    // Navigation
    implementation(libs.navigation.compose)
    
    // Lifecycle ViewModel
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    
    // DataStore
    implementation(libs.datastore.preferences)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)
    
    // Gson
    implementation(libs.gson)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Compose 预览工具
    debugImplementation(libs.androidx.compose.ui.tooling)


}
