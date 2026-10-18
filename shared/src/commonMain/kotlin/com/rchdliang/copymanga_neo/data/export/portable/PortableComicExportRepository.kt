package com.rchdliang.copymanga_neo.data.export.portable

import com.rchdliang.copymanga_neo.data.download.portable.LocalDownloadAccess
import com.rchdliang.copymanga_neo.data.download.portable.use
import com.rchdliang.copymanga_neo.data.local.portable.DocumentStorage
import com.rchdliang.copymanga_neo.data.local.portable.DocumentStore
import com.rchdliang.copymanga_neo.domain.model.*
import com.rchdliang.copymanga_neo.domain.platform.PlatformIOException
import com.rchdliang.copymanga_neo.domain.repository.ComicExportRepository
import com.rchdliang.copymanga_neo.domain.repository.DownloadRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.time.TimeSource
import kotlin.uuid.Uuid

@Serializable
private data class DraftEntry(val id: String, val createdAt: Long)

/** One durable serial export job. Restart preserves successful outputs and marks unfinished work. */
class PortableComicExportRepository(
    private val downloads: DownloadRepository,
    private val io: ComicExportIo,
    private val storage: DocumentStorage,
    private val access: LocalDownloadAccess,
    private val scope: CoroutineScope,
    private val beforeStart: suspend () -> Unit = {}
) : ComicExportRepository {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val document = DocumentStore(storage, "comic-export-task.json", ComicExportState.serializer(), ComicExportState())
    private val draftIndex = DocumentStore(storage, "comic-export-draft-index.json", ListSerializer(DraftEntry.serializer()), emptyList())
    private val mutable = MutableStateFlow(ComicExportState())
    override val state = mutable.asStateFlow()
    private val mutableMessages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    override val messages = mutableMessages.asSharedFlow()
    private val commands = Mutex()
    private var work: Job? = null
    private val restoration = scope.async(start = CoroutineStart.LAZY) {
        try {
            document.load().getOrThrow()
            mutable.value = document.state.value
            if (mutable.value.running) {
                cleanup()
                mutable.update { it.copy(status = ComicExportStatus.INTERRUPTED, current = "导出已中断，可重试未完成的文件") }
                persist()
            }
        } catch (cancel: CancellationException) { throw cancel }
        catch (error: Exception) {
            val message = "导出任务无法恢复，原记录已保留，导出操作已停用；修复存储后请重新启动"
            // This is an in-memory warning only. Never replace the unreadable document or delete outputs.
            mutable.update { it.copy(status = ComicExportStatus.INTERRUPTED, current = message, cleanupWarning = message) }
            throw PlatformIOException(message, error)
        }
    }
    override suspend fun awaitRestoration(): Result<Unit> = attempt { restoration.await() }
    private suspend fun persist() = document.update { mutable.value }.getOrThrow()

    override suspend fun prepare(request: ComicExportRequest): Result<ComicExportPlan> = attempt {
        restoration.await()
        val skipped = mutableListOf<String>()
        val found = mutableSetOf<String>()
        val comics = request.comics.distinct().mapNotNull { path ->
            currentCoroutineContext().ensureActive()
            val comic = downloads.getDownloadedComic(path)
            if (comic == null) { skipped += "$path：没有本地漫画记录"; return@mapNotNull null }
            val chapters = downloads.getDownloadedChapters(path)
                .filter { request.chapters == null || it.uuid in request.chapters }
                .sortedWith(compareBy({ it.groupPathWord.orEmpty() }, { it.index }, { it.uuid }))
                .mapNotNull { chapter ->
                    found += chapter.uuid
                    try {
                        require(chapter.imageCount > 0 && chapter.imagePaths.size == chapter.imageCount) { "图片未完整下载" }
                        val pages = chapter.imagePaths.map { source -> currentCoroutineContext().ensureActive(); io.inspect(source) }
                        ComicExportChapter(chapter.uuid, chapter.name, chapter.index, chapter.groupPathWord?.ifBlank { null } ?: "未分组", pages)
                    } catch (cancel: CancellationException) { throw cancel }
                    catch (error: Exception) { skipped += "${comic.name}／${chapter.name}：${error.message}"; null }
                }
            if (chapters.isEmpty()) null else ComicExportComic(path, comic.name, comic.authorNames, chapters)
        }
        request.chapters?.minus(found)?.takeIf { it.isNotEmpty() }?.let { skipped += "${it.size} 个所选章节没有下载记录" }
        if (comics.isEmpty()) throw PlatformIOException("没有完整下载、可导出的章节")
        ComicExportPlan(comics, skipped)
    }
    override suspend fun saveDraft(plan: ComicExportPlan, outputs: List<ComicExportOutput>): Result<String> = attempt {
        restoration.await()
        val id = Uuid.random().toString()
        val now = kotlin.time.Clock.System.now().toEpochMilliseconds()
        val cutoff = now - 7L * 24 * 60 * 60 * 1000
        val expired = mutableListOf<String>()
        storage.write("comic-export-draft-$id.json", json.encodeToString(ComicExportDraft.serializer(), ComicExportDraft(plan, outputs)))
        draftIndex.update { entries ->
            expired += entries.filter { it.createdAt < cutoff }.map { it.id }
            entries.filter { it.createdAt >= cutoff } + DraftEntry(id, now)
        }.getOrThrow()
        expired.forEach { storage.remove("comic-export-draft-$it.json") }
        id
    }
    override suspend fun readDraft(id: String): Result<ComicExportDraft?> = attempt {
        restoration.await()
        if (!Regex("[a-f0-9-]{36}").matches(id)) null
        else storage.read("comic-export-draft-$id.json")?.let { encoded ->
            val value = Json.parseToJsonElement(encoded).jsonObject
            if ("comics" in value) ComicExportDraft(json.decodeFromJsonElement(ComicExportPlan.serializer(), value))
            else json.decodeFromJsonElement(ComicExportDraft.serializer(), value)
        }
    }
    override fun describeLocation(
        plan: ComicExportPlan, format: ComicExportFormat, previousOutputs: List<ComicExportOutput>
    ): ComicExportLocation? {
        val remaining = exportUnits(plan, format).filter { unit -> previousOutputs.none { it.id == unit.id } }
        return remaining.firstOrNull()?.let { ComicExportLocation(format == ComicExportFormat.IMAGES || remaining.size != 1, it.name, format.mimeType) }
    }
    override suspend fun start(
        plan: ComicExportPlan, format: ComicExportFormat, destination: String, directory: Boolean,
        previousOutputs: List<ComicExportOutput>
    ): Result<Unit> = attempt {
        commands.withLock {
            restoration.await()
            check(!mutable.value.running) { "已有导出任务正在执行" }
            val units = exportUnits(plan, format).filter { unit -> previousOutputs.none { it.id == unit.id } }
            require(units.isNotEmpty()) { "所有文件已经导出完成" }
            require(directory || (units.size == 1 && format != ComicExportFormat.IMAGES)) { "批量导出需要选择目录" }
            require(destination.isNotBlank()) { "请选择保存位置" }
            val lease = access.export(units.flatMap { unit -> unit.chapters.map { "${unit.comic.pathWord}/${it.uuid}" } })
            try {
                mutable.value = ComicExportState(
                    taskId = Uuid.random().toString(), status = ComicExportStatus.RUNNING,
                    plan = plan, format = format, destination = destination, directory = directory,
                    totalBytes = units.sumOf { it.bytes }, outputs = previousOutputs, current = "准备导出"
                )
                persist()
                beforeStart()
                // Enter the try/finally before returning, so even immediate cancellation releases the lease.
                work = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                    lease.use {
                        try { execute(units) }
                        catch (cancel: CancellationException) {
                            withContext(NonCancellable) {
                                cleanup()
                                mutable.update { it.copy(status = ComicExportStatus.CANCELLED, current = "导出已取消") }
                                recordTerminalState()
                            }
                            throw cancel
                        } catch (error: Exception) {
                            cleanup()
                            mutable.update { it.copy(status = ComicExportStatus.INTERRUPTED, current = error.message ?: "导出失败") }
                            recordTerminalState()
                        } finally {
                            mutableMessages.tryEmit(resultMessage())
                        }
                    }
                }
            } catch (error: Throwable) {
                withContext(NonCancellable) {
                    lease.release()
                    cleanup()
                    mutable.update { it.copy(status = ComicExportStatus.INTERRUPTED, current = error.message ?: "无法启动导出") }
                    recordTerminalState()
                }
                throw error
            }
        }
    }
    private suspend fun execute(units: List<ExportUnit>) {
        var bytes = 0L
        var lastUpdate = TimeSource.Monotonic.markNow()
        for (unit in units) {
            currentCoroutineContext().ensureActive()
            try {
                val settings = mutable.value
                val output = io.write(unit, settings.destination, settings.directory, settings.format,
                    onCreated = { uri -> mutable.update { it.copy(activeUri = uri) }; persist() },
                    onBytes = { count ->
                        bytes += count
                        if (lastUpdate.elapsedNow().inWholeMilliseconds >= 300) {
                            mutable.update { it.copy(processedBytes = bytes) }
                            lastUpdate = TimeSource.Monotonic.markNow()
                        }
                    }, onChapter = { name -> mutable.update { it.copy(current = name) } })
                mutable.update { it.copy(activeUri = null, processedBytes = bytes, outputs = it.outputs + output) }
            } catch (cancel: CancellationException) { throw cancel }
            catch (error: Exception) {
                cleanup()
                mutable.update { it.copy(processedBytes = bytes, failures = it.failures + ComicExportFailure(unit.id, unit.name, error.message ?: "写入失败")) }
            }
            persist()
        }
        mutable.update { it.copy(status = ComicExportStatus.COMPLETED, current = "导出完成") }
        persist()
    }
    private suspend fun cleanup() {
        val uri = mutable.value.activeUri ?: return
        val deleted = try { io.delete(uri) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { false }
        mutable.update { it.copy(activeUri = null, cleanupWarning = if (deleted) it.cleanupWarning else "无法清理未完成文件，请手动删除：$uri") }
    }
    private suspend fun recordTerminalState() {
        try { persist() } catch (error: Exception) {
            mutable.update { it.copy(cleanupWarning = listOfNotNull(it.cleanupWarning, "导出状态保存失败：${error.message}").joinToString("；")) }
        }
    }
    private fun resultMessage(): String = mutable.value.resultMessage()
    override fun cancel() {
        scope.launch {
            awaitRestoration().fold(
                onSuccess = { commands.withLock { work?.cancel(CancellationException("用户取消导出")) } },
                onFailure = { mutableMessages.tryEmit(it.message ?: "导出任务无法恢复，请修复存储后重新启动") }
            )
        }
    }
    override suspend fun dismiss(): Result<Unit> = attempt {
        commands.withLock {
            restoration.await()
            if (!mutable.value.running) { mutable.value = ComicExportState(); persist() }
        }
    }
    private suspend fun <T> attempt(block: suspend () -> T): Result<T> = try { Result.success(block()) }
    catch (cancel: CancellationException) { throw cancel }
    catch (error: Exception) { Result.failure(error) }
}
