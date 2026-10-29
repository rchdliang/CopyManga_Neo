import org.gradle.api.tasks.Exec

val restoreRoot = rootProject.layout.projectDirectory
val restoreModelsOutput = rootProject.layout.buildDirectory.dir("reader-restore/generated-resources")
val restoreHostOs = when {
    System.getProperty("os.name").startsWith("Windows") -> "windows"
    System.getProperty("os.name").startsWith("Linux") -> "linux"
    else -> "unsupported"
}
val restoreHostArch = if (System.getProperty("os.arch").lowercase() in setOf("amd64", "x86_64")) "x64" else "arm64"
val restoreDesktopTarget = providers.gradleProperty("copymanga.desktopTarget").getOrElse("$restoreHostOs-$restoreHostArch")
val restoreDesktopOutput = rootProject.layout.buildDirectory.dir("reader-restore/$restoreDesktopTarget-resources")
val restorePython = providers.environmentVariable("PYTHON").getOrElse(if (restoreHostOs == "windows") "python" else "python3")
val restorePrebuilt = providers.gradleProperty("readerRestoreNativeLibrary").orNull
val restoreGlslang = providers.environmentVariable("RESTORE_GLSLANG").orNull?.takeIf { it.isNotBlank() }

extra["readerRestoreModelsDirectory"] = restoreModelsOutput.get().asFile
extra["readerRestoreDesktopDirectory"] = restoreDesktopOutput.get().asFile
extra["readerRestoreDesktopSupported"] = restoreDesktopTarget.startsWith("windows-") || restoreDesktopTarget.startsWith("linux-")

tasks.register<Exec>("prepareReaderRestoreModels") {
    group = "build"
    description = "Compile pinned Anime4K models to Vulkan SPIR-V packs"
    workingDir(restoreRoot)
    inputs.dir(restoreRoot.dir("native/reader-restore/models"))
    inputs.files(fileTree(restoreRoot.dir("native/reader-restore/vendor/vulkan")) {
        include("LICENSE.md", "LICENSES/*.txt", "NOTICE.txt")
    })
    inputs.files(restoreRoot.file("native/reader-restore/compile_models.py"), restoreRoot.file("native/reader-restore/prepare_models.py"), restoreRoot.file("gradle/libs.versions.toml"))
    // 自定义编译器变化时重新生成模型，避免误用上一次工具链的输出。
    inputs.property("compilerOverride", restoreGlslang.orEmpty())
    restoreGlslang?.let { inputs.file(it) }
    outputs.dir(restoreModelsOutput)
    commandLine(restorePython, restoreRoot.file("native/reader-restore/prepare_models.py").asFile.absolutePath,
        "--root", restoreRoot.asFile.absolutePath, "--output", restoreModelsOutput.get().asFile.absolutePath)
}
tasks.register<Exec>("prepareReaderRestoreDesktop") {
    group = "build"
    description = "Build the Windows/Linux Vulkan JNI library"
    workingDir(restoreRoot)
    inputs.files(fileTree(restoreRoot.dir("native/reader-restore")) {
        include("*.cpp", "*.h", "CMakeLists.txt", "build_native.py", "vendor/**")
    })
    inputs.property("target", restoreDesktopTarget)
    inputs.property("prebuilt", restorePrebuilt.orEmpty())
    restorePrebuilt?.let { inputs.file(it) }
    outputs.dir(restoreDesktopOutput)
    val args = mutableListOf(restorePython, restoreRoot.file("native/reader-restore/build_native.py").asFile.absolutePath,
        "--root", restoreRoot.asFile.absolutePath, "--target", restoreDesktopTarget, "--output", restoreDesktopOutput.get().asFile.absolutePath)
    restorePrebuilt?.let { args.addAll(listOf("--prebuilt", it)) }
    commandLine(args)
}
