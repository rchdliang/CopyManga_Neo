import java.util.jar.JarFile
import java.security.MessageDigest
import org.jetbrains.compose.desktop.tasks.AbstractJarsFlattenTask
import org.jetbrains.compose.desktop.application.tasks.AbstractJPackageTask

import org.jetbrains.compose.desktop.application.dsl.TargetFormat
plugins {
    kotlin("jvm")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}
// Explicit portable-JAR target; native packaging still requires a matching host.
val portableDesktopTarget = providers.gradleProperty("copymanga.desktopTarget").orNull



kotlin { jvmToolchain(21) }
dependencies {
    implementation(project(":shared"))
    implementation(when (portableDesktopTarget) {
                null -> compose.desktop.currentOs
                "windows-x64" -> compose.desktop.windows_x64
                "windows-arm64" -> compose.desktop.windows_arm64
                else -> error("copymanga.desktopTarget must be windows-x64 or windows-arm64")
            })
    implementation("org.jetbrains.compose.material3:material3:1.12.0-alpha03")
    implementation("org.jetbrains.androidx.lifecycle:lifecycle-viewmodel:2.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.11.0")
}
compose.desktop {
    application {
        mainClass = "com.rchdliang.copymanga_neo.desktop.MainKt"
        nativeDistributions {
            // Dependency analysis finds these runtime modules; charset providers are loaded
            // dynamically and preserve the full-JDK decoder catalog in packaged apps.
            modules("java.instrument", "java.management", "jdk.unsupported", "jdk.charsets")
            targetFormats(TargetFormat.Msi, TargetFormat.Exe, TargetFormat.Deb, TargetFormat.Rpm)
            packageName = "CopyMangaNeo"
            packageVersion = rootProject.extra["copyMangaVersionName"] as String
            description = "CopyManga Neo multiplatform client"
            vendor = "rchdliang"
            windows { iconFile.set(project.file("icons/copymanga-neo.ico")) }
            linux { iconFile.set(project.file("src/main/resources/copymanga-neo.png")) }
        }
    }
}

/** Fail closed rather than silently dropping providers/classes in Compose's first-wins flattening. */
abstract class VerifyPortableJarInputs : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val jars: ConfigurableFileCollection

    @get:OutputFile
    abstract val inputReport: RegularFileProperty

    @TaskAction
    fun verify() {
        val seen = mutableMapOf<String, Pair<String, String>>()
        val digest = MessageDigest.getInstance("SHA-256")
        val resolvedJars = jars.files.filter { it.isFile && it.extension == "jar" }.sortedBy { it.absolutePath }
        resolvedJars.forEach { file ->
            JarFile(file).use { jar ->
                val multiRelease = jar.manifest?.mainAttributes?.getValue("Multi-Release")?.equals("true", true) == true
                jar.entries().asSequence().filterNot { it.isDirectory }.forEach { entry ->
                    val name = entry.name
                    val upper = name.uppercase()
                    check(!(upper.startsWith("META-INF/") &&
                        (upper.endsWith(".SF") || upper.endsWith(".RSA") || upper.endsWith(".DSA") || upper.endsWith(".EC") || upper.substringAfter("META-INF/").startsWith("SIG-")))) {
                        "Signed dependency cannot be flattened safely: ${file.name}:$name"
                    }
                    check(!name.startsWith("META-INF/versions/") || multiRelease) {
                        "Inactive versioned resource would become active in portable JAR: ${file.name}:$name"
                    }
                    // Module descriptors belong to individual libraries, not the combined classpath JAR.
                    if (!name.endsWith("module-info.class") &&
                        (name.endsWith(".class") || name.startsWith("META-INF/services/"))) {
                        val hash = jar.getInputStream(entry).use { input ->
                            digest.reset()
                            val buffer = ByteArray(8192)
                            while (true) { val count = input.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) }
                            digest.digest().joinToString("") { "%02x".format(it) }
                        }
                        val previous = seen.putIfAbsent(name, file.name to hash)
                        check(previous == null || previous.second == hash) {
                            "Conflicting portable JAR entry $name: ${previous?.first} and ${file.name}; use intact dependency JARs instead"
                        }
                    }
                }
            }
        }
        inputReport.get().asFile.apply {
            parentFile.mkdirs()
            writeText(resolvedJars.joinToString(separator = "\n", postfix = "\n") { it.absolutePath })
        }
    }
}

// Register outside task configuration: Gradle forbids nested task-container mutation.
// Compose 1.12.1 defines these two independent flatten variants.
val preflights = listOf("flattenJars", "flattenReleaseJars").associateWith { flattenName ->
    val runtimeInputs = objects.fileCollection()
    val verifier = tasks.register<VerifyPortableJarInputs>("verify${flattenName.replaceFirstChar { it.uppercase() }}Inputs") {
        jars.from(runtimeInputs)
        inputReport.set(layout.buildDirectory.file("reports/portable-jar/${portableDesktopTarget ?: "host"}-$flattenName-inputs.txt"))
    }
    runtimeInputs to verifier
}
tasks.withType<AbstractJarsFlattenTask>().configureEach {
    val (runtimeInputs, verifier) = preflights.getValue(name)
    runtimeInputs.from(inputFiles)
    dependsOn(verifier)
}
// Gradle 9: Compose creates the JVM base task, not the legacy bundling.Jar subclass.
tasks.withType<org.gradle.jvm.tasks.Jar>().matching { it.name.contains("UberJar") }.configureEach {
    portableDesktopTarget?.let { target ->
        // Compose's later task action sets archiveAppendix back to the build host.
        // An explicit lazy filename keeps its package/version/build-type properties,
        // but takes the platform segment from the selected native dependency target.
        archiveFileName.set(
            archiveBaseName.zip(archiveVersion.orElse("")) { base, version ->
                listOf(base, target, version).filter { it.isNotEmpty() }.joinToString("-")
            }.zip(archiveClassifier.orElse("")) { stem, classifier ->
                if (classifier.isEmpty()) stem else "$stem-$classifier"
            }.zip(archiveExtension) { stem, extension -> "$stem.$extension" }
        )
    }
    // Versioned classes/resources exist in the resolved dependencies (including JBR API).
    manifest.attributes["Multi-Release"] = "true"
    exclude("module-info.class", "META-INF/versions/**/module-info.class")
}

if (portableDesktopTarget != null) {
    val selectedTarget = portableDesktopTarget
    val hostOs = System.getProperty("os.name").lowercase()
    val hostArch = when (System.getProperty("os.arch").lowercase()) {
        "amd64", "x86_64" -> "x64"
        "aarch64", "arm64" -> "arm64"
        else -> "unsupported"
    }
    val matchesHost = hostOs.startsWith("windows") && selectedTarget == "windows-$hostArch"
    tasks.configureEach {
        if (!matchesHost && (this is JavaExec || this is Test || this is AbstractJPackageTask ||
                name == "createDistributable" || name == "createReleaseDistributable" ||
                name == "runDistributable" || name == "runReleaseDistributable")) {
            doFirst {
                error("$selectedTarget runtime cannot run or create host-native images/installers here; use packageUberJarForCurrentOS for a portable JAR")
            }
        }
    }
}
