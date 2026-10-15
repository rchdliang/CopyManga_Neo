pluginManagement {
    repositories { mavenCentral(); google(); gradlePluginPortal() }
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id.startsWith("com.android.")) useModule("com.android.tools.build:gradle:${requested.version}")
        }
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { mavenCentral(); google() }
}
rootProject.name = "CopyManga_Neo_Multiplatform"
include(":shared", ":desktopApp")
// Desktop/common builds need no Android SDK. Enable Android explicitly in CI/IDE.
if (providers.gradleProperty("enableAndroid").orNull != "false") include(":app")
