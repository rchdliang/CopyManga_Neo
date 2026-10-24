// Bundle paths are portable; build tasks verify/prepare before reading any resources.
val srBundle = providers.gradleProperty("readerSrBundle").orNull?.let { file(it) }
    ?: File(gradle.gradleUserHomeDir, "caches/copymanga-reader-sr/v1/bundle")
extra["readerSrBundleDirectory"] = srBundle
val srPython = providers.gradleProperty("readerSrPython").orElse("python")
listOf("android-arm64", "windows-x64").forEach { platform ->
    tasks.register<Exec>("prepareReaderSr" + if (platform == "android-arm64") "Android" else "Windows") {
        // Always validate, including cache hits: missing/tampered bytes must never ship silently.
        if (platform == "windows-x64") mustRunAfter("prepareReaderSrAndroid")
        workingDir(rootDir)
        commandLine(srPython.get(), "native/reader-sr/prepare_bundle.py", "--destination", srBundle.absolutePath,
            "--platform", platform)
    }
}
