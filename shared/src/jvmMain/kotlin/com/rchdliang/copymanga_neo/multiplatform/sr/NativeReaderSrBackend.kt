package com.rchdliang.copymanga_neo.multiplatform.sr

import com.rchdliang.copymanga_neo.domain.reading.*
import java.io.*
import java.security.MessageDigest
import java.util.Properties
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal data class SrRaster(val width: Int, val height: Int, val rgba: ByteArray)
internal interface SrRasterCodec {
    /** Read metadata only. Reject animation and non-normal EXIF orientation, before allocating pixels. */
    fun dimensions(bytes: ByteArray): Pair<Int, Int>
    fun decode(bytes: ByteArray): SrRaster
    fun encode(raster: SrRaster): ByteArray
}
internal interface SrWorker {
    suspend fun probe(models: File, gpu: Boolean): Int
    suspend fun run(models: File, gpu: Int, input: File, output: File)
}

internal class SrBundle(
    private val platform: String,
    private val cache: File,
    private val asset: (String) -> InputStream?,
    private val installedRuntime: File? = null,
    modelDirectory: File = File(cache, "reader-sr-models/cunet-20250915"),
) {
    val models = SrModelStore(modelDirectory)
    private val manifest = asset("reader-sr/$platform/manifest.properties")?.use {
        Properties().apply { load(ByteArrayInputStream(it.readLimited(8192))) }
    }
    val unavailable: String? = if (manifest == null) "此安装包未包含当前平台的超分运行时" else null
    val root: File get() {
        val version = manifest?.getProperty("runtimeSha256")?.takeIf { it.matches(Regex("[0-9a-f]{64}")) } ?: "unconfigured"
        return File(cache, "reader-sr-v1/$platform/$version")
    }
    fun prepare(): Pair<File, File> {
        val m = requireNotNull(manifest) { unavailable ?: "缺少超分配置" }
        require(m.getProperty("protocol") == "1" && m.getProperty("platform") == platform &&
            m.getProperty("runtime") == ReaderSrModel.runtime && m.getProperty("tile") == "32" &&
            m.getProperty("scale") == "2" && m.getProperty("noise") == "-1" &&
            m.getProperty("modelSha256") == ReaderSrModel.weights && m.getProperty("paramSha256") == ReaderSrModel.graph)
        require(asset("reader-sr/NOTICE.txt")?.use { it.readLimited(256*1024).isNotEmpty() } == true) { "缺少模型许可清单" }
        root.mkdirs()
        val modelFiles = models.requireReady()
        val hash = m.getProperty("runtimeSha256").also { require(it?.matches(Regex("[0-9a-f]{64}")) == true) }
        val runtime = installedRuntime ?: File(root, if (platform.startsWith("windows")) "copymanga-sr.exe" else "copymanga-sr")
        if (installedRuntime == null) {
            install("reader-sr/$platform/${runtime.name}", runtime, hash, 64*1024*1024)
            check(runtime.setExecutable(true, true) || runtime.canExecute()) { "超分运行时无法执行" }
        } else require(runtime.isFile && runtime.sha256() == hash) { "超分运行时校验失败" }
        return modelFiles to runtime
    }
    private fun install(resource: String, output: File, hash: String, limit: Int) {
        if (output.isFile && output.length() <= limit && output.sha256() == hash) return
        val bytes = asset(resource)?.use { it.readLimited(limit) } ?: error("缺少超分资源")
        require(bytes.sha256() == hash) { "超分资源校验失败" }
        val temp = File.createTempFile("sr-resource-", ".tmp", output.parentFile)
        try {
            temp.writeBytes(bytes)
            java.nio.file.Files.move(temp.toPath(), output.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        } finally { temp.delete() }
    }
}

internal class NativeReaderSrBackend(
    private val bundle: SrBundle,
    private val readSource: suspend (String) -> ByteArray,
    private val codec: SrRasterCodec,
    private val workerFactory: (File) -> SrWorker,
    override val budgetBytes: Long = 192L*1024*1024,
) : ReaderSrPlatform {
    override val models: ReaderSrModels = object : ReaderSrModels by bundle.models {
        override suspend fun delete() = gate.withLock { bundle.models.delete() }
    }
    override val unavailableReason get() = bundle.unavailable ?: if (budgetBytes <= workingBytes) "当前可用内存不足，保留原图" else null
    override val workingBytes = 96L*1024*1024
    override val detail = MutableStateFlow("等待当前页原图加载；GPU 使用前将自检")
    private var cpuTested = false
    private var gpuTested = false
    private var gpuIndex = -1
    private var cpuFailed = false

    override suspend fun source(page: SrPage): SrSource = withContext(Dispatchers.IO) {
        val bytes = readSource(page.source)
        require(bytes.size <= MAX_INPUT_BYTES)
        val (width, height) = codec.dimensions(bytes)
        checkDimensions(width, height)
        SrSource(page.id, bytes.sha256(), width, height, location = page.source)
    }

    override suspend fun upscale(request: SrRequest): SrArtifact = gate.withLock {
        withContext(Dispatchers.IO) {
            require(request.model == ReaderSrModel.model && request.parameters.tile == 32 &&
                request.parameters.noise == -1 && !request.parameters.tta)
            checkDimensions(request.source.width, request.source.height)
            require(request.source.width.toLong()*request.source.height*20 <= budgetBytes-workingBytes) { "超出超分内存预算" }
            val (models, binary) = bundle.prepare()
            val worker = workerFactory(binary)
            suspend fun cpu() {
                check(!cpuFailed) { "CPU 自检失败，本次阅读已停用该后端" }
                if (!cpuTested) {
                    try { withTimeout(20_000) { check(worker.probe(models, false) == -1) }; cpuTested = true }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (failure: Exception) { cpuFailed = true; throw failure }
                }
            }
            suspend fun gpu(): Int {
                if (!gpuTested) {
                    try { gpuIndex = withTimeout(20_000) { worker.probe(models, true) } }
                    catch (cancelled: TimeoutCancellationException) { currentCoroutineContext().ensureActive(); gpuIndex = -1 }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { gpuIndex = -1 }
                    gpuTested = true
                }
                return gpuIndex
            }
            var device = when (request.parameters.mode) {
                SrExecutionMode.CPU -> { cpu(); -1 }
                SrExecutionMode.GPU -> gpu().also { check(it >= 0) { "GPU 自检未通过，继续显示原图" } }
                SrExecutionMode.AUTO -> gpu().also {
                    if (it < 0) { check(request.parameters.cpuFallback) { "GPU 不可用，CPU 回退已关闭" }; cpu() }
                }
            }
            currentCoroutineContext().ensureActive()
            val jobs = File(bundle.root, "jobs").apply { mkdirs() }
            // Only this adapter owns this directory; no model or original image is ever removed.
            pruneAbandonedJobs(jobs)
            val directory = java.nio.file.Files.createTempDirectory(jobs.toPath(), "job-").toFile()
            try {
                RandomAccessFile(File(directory, "lock"), "rw").use { lockFile ->
                    lockFile.channel.lock().use {
                val input = File(directory, "input.rgba")
                val output = File(directory, "output.rgba")
                prepareInput(request.source, input)
                // No chapter-sized raster cache. Raw files are private and removed in finally.
                detail.value = if (device >= 0) "GPU · 小分块" else "CPU · 小分块"
                try { worker.run(models, device, input, output) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) {
                    if (device < 0) { cpuFailed = true; throw failure }
                    gpuIndex = -1; gpuTested = true
                    if (request.parameters.mode != SrExecutionMode.AUTO || !request.parameters.cpuFallback) throw failure
                    output.delete(); cpu(); device = -1
                    detail.value = "GPU 失败，已回退 CPU"
                    worker.run(models, device, input, output)
                }
                currentCoroutineContext().ensureActive()
                val result = readRaster(output, request.source.width*2, request.source.height*2)
                val png = codec.encode(result)
                require(png.size <= ReaderSrCoordinator.MAX_ENCODED_BYTES)
                // Decode implementation validates the generated PNG's full contents in platform tests;
                // encoding here starts from an exactly-sized, fully-read native RGBA result.
                SrArtifact(png, result.width, result.height)
                    }
                }
            } finally { directory.deleteRecursively() }
        }
    }
    private fun checkDimensions(width: Int, height: Int) {
        require(width in 1..4096 && height in 1..4096 && width.toLong()*height <= 3L*1024*1024) {
            "此页过大或为长图，继续显示原图"
        }
    }
    private suspend fun prepareInput(source: SrSource, input: File) {
        val encoded = readSource(source.location)
        require(encoded.size <= MAX_INPUT_BYTES && encoded.sha256() == source.contentSha256) { "原图已变化，取消本次增强" }
        val raster = codec.decode(encoded)
        require(raster.width == source.width && raster.height == source.height)
        writeRaster(input, raster)
    }
    companion object {
        // Also serializes different windows/routes. A worker is reaped before this lock is released.
        private val gate = Mutex()
        const val MAX_INPUT_BYTES = 16*1024*1024
    }
}

/** Another application process may use this cache too. Never remove its locked active job. */
private fun pruneAbandonedJobs(root: File) {
    val before = System.currentTimeMillis()-60_000
    root.listFiles()?.filter { it.isDirectory && it.name.startsWith("job-") && it.lastModified() < before }?.forEach { directory ->
        val stale = runCatching {
            RandomAccessFile(File(directory, "lock"), "rw").use { file ->
                val lock = file.channel.tryLock() ?: return@use false
                lock.release(); true
            }
        }.getOrDefault(false)
        if (stale) directory.deleteRecursively()
    }
}

internal fun InputStream.readLimited(maximum: Int): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val count = read(buffer, 0, minOf(buffer.size, maximum-output.size()+1))
        if (count < 0) break
        require(output.size()+count <= maximum) { "资源超过大小限制" }
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}
internal fun ByteArray.sha256(): String = MessageDigest.getInstance("SHA-256").digest(this).joinToString("") { "%02x".format(it) }
internal fun File.sha256(): String = inputStream().use { stream ->
    val digest = MessageDigest.getInstance("SHA-256"); val buffer = ByteArray(8192)
    while (true) { val count = stream.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) }
    digest.digest().joinToString("") { "%02x".format(it) }
}
internal fun writeRaster(file: File, raster: SrRaster) = DataOutputStream(BufferedOutputStream(file.outputStream())).use {
    require(raster.rgba.size.toLong() == raster.width.toLong()*raster.height*4)
    it.writeInt(Integer.reverseBytes(0x31525343)); it.writeInt(Integer.reverseBytes(raster.width)); it.writeInt(Integer.reverseBytes(raster.height)); it.write(raster.rgba)
}
internal fun readRaster(file: File, width: Int, height: Int): SrRaster {
    val expected = width.toLong()*height*4
    require(expected in 1..(12L*1024*1024*4) && file.length() == expected+12) { "无效的超分输出大小" }
    return DataInputStream(BufferedInputStream(file.inputStream())).use {
        require(Integer.reverseBytes(it.readInt()) == 0x31525343 && Integer.reverseBytes(it.readInt()) == width && Integer.reverseBytes(it.readInt()) == height)
        SrRaster(width, height, ByteArray(expected.toInt()).also(it::readFully))
    }
}
