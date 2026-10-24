package com.rchdliang.copymanga_neo.multiplatform.sr

import com.rchdliang.copymanga_neo.domain.reading.*
import java.io.File
import java.io.IOException
import kotlin.test.*
import kotlinx.coroutines.*
import org.junit.Assume.assumeTrue
import org.jetbrains.skia.*

class NativeReaderSrTest {
    private val resources = javaClass.classLoader
    private val nativePlatform = if (System.getProperty("os.name").startsWith("Windows")) "windows-x64" else "linux-x64"
    private val root = File("build/sr-integration/中文 路径").apply { mkdirs() }
    private fun bundle(cache: File): SrBundle {
        val bundle = SrBundle(nativePlatform, cache, { resources.getResourceAsStream(it) })
        // Explicit network integration, separate from inference: production never downloads without consent.
        runBlocking { bundle.models.download() }
        check(bundle.models.state.value == SrModelState.Ready)
        return bundle
    }
    private fun bytes(name: String) = resources.getResourceAsStream("sr/$name-source.png")!!.use { it.readBytes() }
    private fun request(source: SrSource, mode: SrExecutionMode = SrExecutionMode.CPU, fallback: Boolean = true) =
        SrRequest(source, ReaderSrModel.model, SrParameters(mode = mode, cpuFallback = fallback))

    @Test fun realPackagedCpuProducesFourValidTwoTimesImages() = runBlocking {
        assumeTrue("Build the host native bundle to run real integration", resources.getResource("reader-sr/$nativePlatform/manifest.properties") != null)
        val cache = File(root, "real").apply { deleteRecursively(); mkdirs() }
        val backend = NativeReaderSrBackend(bundle(cache), { bytes(it) }, DesktopSrCodec(), { DesktopSrWorker(it) })
        for (name in listOf("small-text", "screentone", "thin-lines", "color-page")) {
            val source = backend.source(SrPage("chapter:image:$name", name))
            val start = System.nanoTime()
            val artifact = backend.upscale(request(source))
            assertEquals(384, artifact.width); assertEquals(256, artifact.height)
            Image.makeFromEncoded(artifact.png).use { image -> assertEquals(384, image.width); assertEquals(256, image.height) }
            File(root, "$name-native.png").writeBytes(artifact.png)
            println("SR_REAL_CPU $name seconds=${(System.nanoTime()-start)/1e9} bytes=${artifact.png.size}")
        }
        assertFalse(cache.walkTopDown().any { it.extension == "rgba" })
    }

    @Test fun realPackagedGpuProducesFourValidTwoTimesImagesWithoutCpuFallback() = runBlocking {
        assumeTrue(resources.getResource("reader-sr/$nativePlatform/manifest.properties") != null)
        val cache = File(root, "real-gpu").apply { deleteRecursively(); mkdirs() }
        val assets = bundle(cache)
        val (models, binary) = assets.prepare()
        assumeTrue("No validated GPU available", withTimeout(20_000) { DesktopSrWorker(binary).probe(models, true) } >= 0)
        val backend = NativeReaderSrBackend(assets, { bytes(it) }, DesktopSrCodec(), { DesktopSrWorker(it) })
        for (name in listOf("small-text", "screentone", "thin-lines", "color-page")) {
            val source = backend.source(SrPage("chapter:image:$name", name))
            val start = System.nanoTime()
            val artifact = backend.upscale(request(source, SrExecutionMode.GPU, fallback = false))
            assertEquals(384, artifact.width); assertEquals(256, artifact.height)
            Image.makeFromEncoded(artifact.png).use { image -> assertEquals(384, image.width); assertEquals(256, image.height) }
            assertTrue(backend.detail.value.startsWith("GPU"))
            File(root, "$name-gpu-native.png").writeBytes(artifact.png)
            println("SR_REAL_GPU $name seconds=${(System.nanoTime()-start)/1e9} bytes=${artifact.png.size}")
        }
        assertFalse(cache.walkTopDown().any { it.extension == "rgba" })
    }

    @Test fun childSignalFailureDoesNotCrashJvmAndCancellationReapsProcess() = runBlocking {
        assumeTrue(System.getProperty("os.name").startsWith("Linux"))
        val directory = File(root, "process").apply { mkdirs() }
        val crash = File(directory, "crash.sh").apply { writeText("#!/bin/sh\nulimit -c 0\nkill -SEGV $$\n"); setExecutable(true) }
        assertFailsWith<IOException> { DesktopSrWorker(crash).probe(directory, true) }
        val pid = File(directory, "pid").absoluteFile.apply { delete() }
        val sleep = File(directory, "sleep.sh").apply {
            writeText("#!/bin/sh\necho $$ > '${pid.path.replace("'", "'\\''")}'\nexec sleep 30\n"); setExecutable(true)
        }
        assertFailsWith<TimeoutCancellationException> { withTimeout(300) { DesktopSrWorker(sleep).probe(directory, true) } }
        assertTrue(pid.isFile)
        assertFalse(ProcessHandle.of(pid.readText().trim().toLong()).map { it.isAlive }.orElse(false))
    }

    @Test fun gpuCrashAutoFallsBackOnceButGpuOnlyAndFallbackOffPreserveOriginal() = runBlocking {
        assumeTrue(resources.getResource("reader-sr/$nativePlatform/manifest.properties") != null)
        val cache = File(root, "fallback").apply { deleteRecursively(); mkdirs() }
        val modes = mutableListOf<Int>()
        var gpuProbes = 0
        val stub = object : SrWorker {
            override suspend fun probe(models: File, gpu: Boolean): Int {
                if (gpu) { gpuProbes++; return 0 }; return -1
            }
            override suspend fun run(models: File, gpu: Int, input: File, output: File) {
                modes += gpu
                if (gpu >= 0) throw IOException("simulated worker death")
                writeRaster(output, SrRaster(384, 256, ByteArray(384*256*4) { 127 }))
            }
        }
        val backend = NativeReaderSrBackend(bundle(cache), { bytes("small-text") }, DesktopSrCodec(), { stub })
        val source = backend.source(SrPage("single-real-page-7", "small-text"))
        backend.upscale(request(source, SrExecutionMode.AUTO))
        assertEquals(listOf(0, -1), modes)
        assertEquals(1, gpuProbes)
        assertFails { backend.upscale(request(source, SrExecutionMode.GPU)) }
        assertFails { backend.upscale(request(source, SrExecutionMode.AUTO, false)) }
        assertEquals(listOf(0, -1), modes)
    }

    @Test fun sourceMutationAndOversizedOutputAreRejectedAndJobFilesRemoved() = runBlocking {
        assumeTrue(resources.getResource("reader-sr/$nativePlatform/manifest.properties") != null)
        val cache = File(root, "invalid").apply { deleteRecursively(); mkdirs() }
        var sourceBytes = bytes("small-text")
        var ran = false
        val stub = object : SrWorker {
            override suspend fun probe(models: File, gpu: Boolean) = -1
            override suspend fun run(models: File, gpu: Int, input: File, output: File) { ran = true; output.writeBytes(byteArrayOf(1, 2, 3)) }
        }
        val backend = NativeReaderSrBackend(bundle(cache), { sourceBytes }, DesktopSrCodec(), { stub })
        val source = backend.source(SrPage("chapter:image:7", "source"))
        sourceBytes = bytes("color-page")
        assertFails { backend.upscale(request(source)) }; assertFalse(ran)
        sourceBytes = bytes("small-text")
        assertFails { backend.upscale(request(source)) }; assertTrue(ran)
        assertFalse(cache.walkTopDown().any { it.extension == "rgba" })
    }

    @Test fun staleUnlockedJobsAreRemovedButLockedJobsFromAnotherInstanceSurvive() = runBlocking {
        assumeTrue(resources.getResource("reader-sr/$nativePlatform/manifest.properties") != null)
        val cache = File(root, "locks").apply { deleteRecursively(); mkdirs() }
        val assets = bundle(cache)
        assets.prepare()
        val jobs = File(assets.root, "jobs").apply { mkdirs() }
        val active = File(jobs, "job-active").apply { mkdirs() }
        val stale = File(jobs, "job-stale").apply { mkdirs(); setLastModified(1) }
        val worker = object : SrWorker {
            override suspend fun probe(models: File, gpu: Boolean) = -1
            override suspend fun run(models: File, gpu: Int, input: File, output: File) {
                writeRaster(output, SrRaster(384, 256, ByteArray(384*256*4)))
            }
        }
        val backend = NativeReaderSrBackend(assets, { bytes("small-text") }, DesktopSrCodec(), { worker })
        java.io.RandomAccessFile(File(active, "lock"), "rw").use { file ->
            file.channel.lock().use {
                active.setLastModified(1)
                backend.upscale(request(backend.source(SrPage("page7", "source"))))
                assertTrue(active.isDirectory); assertFalse(stale.exists())
            }
        }
        cache.deleteRecursively()
        Unit
    }

    @Test fun packageWithoutModelRequiresExplicitDownload() {
        assumeTrue(resources.getResource("reader-sr/$nativePlatform/manifest.properties") != null)
        assertNull(resources.getResource("reader-sr/models/scale2.0x_model.bin"))
        val missing = SrBundle(nativePlatform, File(root, "missing-model"), { resources.getResourceAsStream(it) })
        assertFailsWith<IllegalStateException> { missing.prepare() }

    }

    @Test fun modelDeletionWaitsForNativeWorkerBeforeRemovingFiles() = runBlocking {
        assumeTrue(resources.getResource("reader-sr/$nativePlatform/manifest.properties") != null)
        val cache = File(root, "delete-during-use").apply { deleteRecursively(); mkdirs() }
        val assets = bundle(cache)
        val started = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        val worker = object : SrWorker {
            override suspend fun probe(models: File, gpu: Boolean) = -1
            override suspend fun run(models: File, gpu: Int, input: File, output: File) {
                started.complete(Unit); finish.await()
                assertTrue(File(models, "scale2.0x_model.bin").isFile)
                writeRaster(output, SrRaster(384, 256, ByteArray(384*256*4)))
            }
        }
        val backend = NativeReaderSrBackend(assets, { bytes("small-text") }, DesktopSrCodec(), { worker })
        val inference = async { backend.upscale(request(backend.source(SrPage("page", "source")))) }
        started.await()
        val deletion = launch { backend.models.delete() }
        withTimeout(3000) { while (backend.models.state.value != SrModelState.Deleting) delay(10) }
        assets.models.requireReady()
        finish.complete(Unit)
        assertEquals(384, inference.await().width)
        deletion.join()
        assertEquals(SrModelState.Missing, backend.models.state.value)
        assertFailsWith<IllegalStateException> { assets.models.requireReady() }
        Unit
    }
}
