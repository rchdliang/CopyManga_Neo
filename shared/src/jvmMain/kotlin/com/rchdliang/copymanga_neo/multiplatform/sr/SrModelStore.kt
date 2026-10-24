package com.rchdliang.copymanga_neo.multiplatform.sr

import com.rchdliang.copymanga_neo.domain.reading.*
import java.io.File
import java.io.RandomAccessFile
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal data class SrModelFile(val name: String, val bytes: Long, val hash: String) {
    val url get() = "https://raw.githubusercontent.com/nihui/waifu2x-ncnn-vulkan/a86cfb043e6482b5f08778a114df9b5faf6e73b7/models/models-cunet/$name"
}
internal val SR_MODEL_FILES = listOf(
    SrModelFile("scale2.0x_model.bin", 2776400, ReaderSrModel.weights),
    SrModelFile("scale2.0x_model.param", 7249, ReaderSrModel.graph))

/** Only an explicit UI confirmation calls download. No bundled-model extraction or auto-download. */
internal class SrModelStore(
    private val directory: File,
    private val specs: List<SrModelFile> = SR_MODEL_FILES,
    private val remove: (File) -> Boolean = { it.deleteRecursively() },
    private val fetch: suspend (SrModelFile, File, (Long) -> Unit) -> Unit = ::fetchOfficialModel,
) : ReaderSrModels {
    private class Entry {
        val mutex = Mutex()
        val state = MutableStateFlow<SrModelState>(SrModelState.Missing)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        var job: Job? = null
        var deletion: Deferred<Unit>? = null
        var downloaders = 0
    }
    private val entry = entries.getOrPut(directory.canonicalPath) { Entry() }
    override val state = entry.state
    override val downloadBytes get() = specs.sumOf { it.bytes }
    private fun valid(folder: File): Boolean = try { specs.all { spec ->
        val file = File(folder, spec.name)
        file.isFile && file.length() == spec.bytes && file.sha256() == spec.hash
    } } catch (_: java.io.IOException) { false }
    fun requireReady(): File {
        check(valid(directory)) { "请先下载并校验超分模型，继续显示原图" }
        return directory
    }
    override fun startDownload() { synchronized(entry) {
        if (entry.deletion?.isActive != true && entry.job?.isActive != true) entry.job = entry.scope.launch { download() }
    } }
    override fun cancelDownload() { synchronized(entry) { entry.job?.cancel() } }
    override suspend fun refresh() = withContext(Dispatchers.IO) {
        entry.mutex.withLock { synchronized(entry) {
            if (entry.deletion?.isActive != true) state.value = if (valid(directory)) SrModelState.Ready else SrModelState.Missing
        } }
    }
    private suspend fun <T> locked(block: suspend () -> T): T = withContext(Dispatchers.IO) {
        entry.mutex.withLock {
            directory.parentFile.mkdirs()
            RandomAccessFile(File(directory.parentFile, "model-download.lock"), "rw").use { file ->
                var lock: java.nio.channels.FileLock? = null
                try {
                    while (lock == null) {
                        currentCoroutineContext().ensureActive()
                        lock = try { file.channel.tryLock() } catch (_: java.nio.channels.OverlappingFileLockException) { null }
                        if (lock == null) delay(100)
                    }
                    block()
                } finally { lock?.release() }
            }
        }
    }
    private suspend fun operation(kind: SrModelOperation, block: suspend () -> Unit) {
        try { locked(block) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { state.value = SrModelState.Failed("模型存储操作失败，原图保持可读；请检查可用空间后重试", kind) }
    }
    override suspend fun download() {
        synchronized(entry) {
            if (entry.deletion?.isActive == true) return
            entry.downloaders++
        }
        try { downloadModel() } finally { synchronized(entry) { entry.downloaders-- } }
    }
    private suspend fun downloadModel() = operation(SrModelOperation.DOWNLOAD) {
        if (valid(directory)) { state.value = SrModelState.Ready; return@operation }
        val staging = Files.createTempDirectory(directory.parentFile.toPath(), "model-pending-").toFile()
        try {
            var completed = 0L
            for (spec in specs) {
                state.value = SrModelState.Downloading(completed, downloadBytes)
                val output = File(staging, spec.name)
                fetch(spec, output) { state.value = SrModelState.Downloading(completed + it, downloadBytes) }
                currentCoroutineContext().ensureActive()
                state.value = SrModelState.Preparing
                require(output.length() == spec.bytes && output.sha256() == spec.hash) { "模型下载校验失败，请重试" }
                completed += spec.bytes
            }
            require(valid(staging))
            currentCoroutineContext().ensureActive()
            // A complete versioned directory becomes visible atomically; partial files are never enabled.
            if (directory.exists()) check(directory.deleteRecursively())
            Files.move(staging.toPath(), directory.toPath(), StandardCopyOption.ATOMIC_MOVE)
            state.value = SrModelState.Ready
        } catch (cancelled: CancellationException) {
            state.value = if (valid(directory)) SrModelState.Ready else SrModelState.Cancelled
            throw cancelled
        } catch (_: Exception) {
            state.value = SrModelState.Failed("下载或校验失败，原图保持可读；可重新下载")
        } finally { staging.deleteRecursively() }
    }
    override suspend fun delete() = deleteWhenIdle { it() }

    /** 已确认删除由进程托管；返回只离开界面，不中断等待推理门或删除。 */
    suspend fun deleteWhenIdle(whenIdle: suspend (suspend () -> Unit) -> Unit) {
        val deletion = synchronized(entry) {
            entry.deletion?.takeIf { it.isActive } ?: run {
                // 下载使用同一模型目录；下载/校验期间只提供取消下载，不排队删除。
                if (entry.downloaders > 0 || entry.job?.isActive == true) return
                state.value = SrModelState.Deleting
                entry.scope.async(start = CoroutineStart.LAZY) {
                    try {
                        whenIdle {
                            operation(SrModelOperation.DELETE) {
                                if (directory.exists()) check(remove(directory)) { "模型删除失败" }
                                state.value = SrModelState.Missing
                            }
                        }
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) {
                        state.value = SrModelState.Failed("无法删除模型，请检查文件是否被占用后重试", SrModelOperation.DELETE)
                    }
                }.also { entry.deletion = it; it.start() }
            }
        }
        deletion.await()
    }
    companion object { private val entries = ConcurrentHashMap<String, Entry>() }
}

private suspend fun fetchOfficialModel(spec: SrModelFile, output: File, progress: (Long) -> Unit) {
    val connection = URL(spec.url).openConnection() as HttpsURLConnection
    connection.connectTimeout = 10_000; connection.readTimeout = 10_000
    connection.instanceFollowRedirects = false
    try {
        require(connection.responseCode == 200) { "模型服务器未返回成功响应" }
        require(connection.contentLengthLong == -1L || connection.contentLengthLong == spec.bytes) { "模型文件大小异常" }
        connection.inputStream.use { input -> output.outputStream().use { out ->
            val buffer = ByteArray(64 * 1024); var total = 0L
            while (true) {
                currentCoroutineContext().ensureActive()
                val count = input.read(buffer); if (count < 0) break
                total += count; require(total <= spec.bytes) { "模型文件超过大小上限" }
                out.write(buffer, 0, count); progress(total)
            }
        } }
    } finally { connection.disconnect() }
}
