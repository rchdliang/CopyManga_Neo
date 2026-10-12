package com.rchdliang.copymanga_neo.data.export

import android.content.Context
import android.util.AtomicFile
import android.util.Log
import com.google.gson.Gson
import com.rchdliang.copymanga_neo.data.download.LocalDownloadAccess
import com.rchdliang.copymanga_neo.domain.model.ComicExportDraft
import com.rchdliang.copymanga_neo.domain.model.ComicExportFailure
import com.rchdliang.copymanga_neo.domain.model.ComicExportFormat
import com.rchdliang.copymanga_neo.domain.model.ComicExportOutput
import com.rchdliang.copymanga_neo.domain.model.ComicExportPlan
import com.rchdliang.copymanga_neo.domain.model.ComicExportState
import com.rchdliang.copymanga_neo.domain.model.ComicExportStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/** 一个全局串行导出任务。仅保存元数据和结果，进程中断后由用户重试未完成的输出。 */
@Singleton
class ComicExportManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val writer: ComicExportWriter,
    private val access: LocalDownloadAccess
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gson = Gson()
    private val taskFile = AtomicFile(File(context.noBackupFilesDir, "comic-export-task.json"))
    private val mutableState = MutableStateFlow(ComicExportState())
    val state = mutableState.asStateFlow()
    private val mutableMessages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages = mutableMessages.asSharedFlow()
    private val commands = Mutex()
    private var work: Job? = null
    private val restoration = scope.async {
        val restored = try {
            if (taskFile.baseFile.exists()) taskFile.openRead().use {
                gson.fromJson(it.reader(), ComicExportState::class.java)
            } else null
        } catch (error: Exception) {
            Log.w("ComicExport", "导出记录读取失败", error)
            null
        }
        if (restored != null) {
            mutableState.value = restored
            if (restored.running) {
                cleanup()
                mutableState.update {
                    it.copy(
                        status = ComicExportStatus.INTERRUPTED,
                        current = "导出已中断，可重试未完成的文件"
                    )
                }
                persist()
            }
        }
    }

    suspend fun saveDraft(
        plan: ComicExportPlan,
        outputs: List<ComicExportOutput> = emptyList()
    ): String = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        val directory = File(context.noBackupFilesDir, "comic-export-drafts").apply { mkdirs() }
        val cutoff = System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L
        directory.listFiles()?.filter { it.lastModified() < cutoff }?.forEach { it.delete() }
        writeAtomic(AtomicFile(File(directory, "$id.json")), ComicExportDraft(plan, outputs))
        id
    }

    suspend fun readDraft(id: String): ComicExportDraft? = withContext(Dispatchers.IO) {
        if (!id.matches(Regex("[a-f0-9-]{36}"))) return@withContext null
        try {
            AtomicFile(File(context.noBackupFilesDir, "comic-export-drafts/$id.json")).openRead()
                .use {
                    val json = gson.fromJson(it.reader(), com.google.gson.JsonObject::class.java)
                    if (json.has("comics")) ComicExportDraft(
                        gson.fromJson(
                            json,
                            ComicExportPlan::class.java
                        )
                    )
                    else gson.fromJson(json, ComicExportDraft::class.java)
                }
        } catch (error: Exception) {
            null
        }
    }

    suspend fun start(
        plan: ComicExportPlan, format: ComicExportFormat, destination: String, directory: Boolean,
        previousOutputs: List<ComicExportOutput> = emptyList()
    ): Result<Unit> = withContext(Dispatchers.IO) {
        commands.withLock {
            restoration.await()
            if (mutableState.value.running) return@withLock Result.failure(IOException("已有导出任务正在执行"))
            val units = exportUnits(
                plan,
                format
            ).filter { unit -> previousOutputs.none { it.id == unit.id } }
            if (units.isEmpty()) return@withLock Result.failure(IOException("所有文件已经导出完成"))
            if (!directory && (units.size != 1 || format == ComicExportFormat.IMAGES))
                return@withLock Result.failure(IOException("批量导出需要选择目录"))
            val lease = try {
                access.export(units.flatMap { unit -> unit.chapters.map { "${unit.comic.pathWord}/${it.uuid}" } })
            } catch (error: Exception) {
                return@withLock Result.failure(error)
            }
            try {
                mutableState.value = ComicExportState(
                    taskId = UUID.randomUUID().toString(),
                    status = ComicExportStatus.RUNNING,
                    plan = plan,
                    format = format,
                    destination = destination,
                    directory = directory,
                    totalBytes = units.sumOf { it.bytes },
                    outputs = previousOutputs,
                    current = "准备导出",
                    activeUri = destination.takeUnless { directory })
                persist()
                ComicExportService.start(context)
                val entered = AtomicBoolean(false)
                val job = scope.launch {
                    entered.set(true)
                    lease.use {
                        try {
                            execute(units)
                        } catch (error: CancellationException) {
                            withContext(NonCancellable) {
                                cleanup()
                                mutableState.update {
                                    it.copy(
                                        status = ComicExportStatus.CANCELLED,
                                        current = error.message ?: "导出已取消"
                                    )
                                }
                                runCatching { persist() }
                            }
                            throw error
                        } catch (error: Exception) {
                            Log.e("ComicExport", "导出任务失败", error)
                            cleanup()
                            mutableState.update {
                                it.copy(
                                    status = ComicExportStatus.INTERRUPTED,
                                    current = error.message ?: "导出失败"
                                )
                            }
                            runCatching { persist() }
                        }
                    }
                }
                work = job
                job.invokeOnCompletion { cause ->
                    // 极快的取消可能发生在协程第一次调度前，仍需释放租约和清理本次新建文件。
                    if (!entered.get()) scope.launch {
                        lease.use {
                            cleanup()
                            mutableState.update {
                                it.copy(
                                    status = ComicExportStatus.CANCELLED,
                                    current = cause?.message ?: "导出已取消"
                                )
                            }
                            runCatching { persist() }
                            mutableMessages.tryEmit(mutableState.value.resultMessage())
                        }
                    }
                    else mutableMessages.tryEmit(mutableState.value.resultMessage())
                }
                Result.success(Unit)
            } catch (error: CancellationException) {
                lease.close()
                withContext(NonCancellable) {
                    cleanup()
                    mutableState.update {
                        it.copy(
                            status = ComicExportStatus.CANCELLED,
                            current = "导出已取消"
                        )
                    }
                    runCatching { persist() }
                }
                throw error
            } catch (error: Exception) {
                lease.close()
                cleanup()
                mutableState.update {
                    it.copy(
                        status = ComicExportStatus.INTERRUPTED,
                        current = error.message ?: "无法启动后台导出"
                    )
                }
                runCatching { persist() }
                Result.failure(error)
            }
        }
    }

    private suspend fun execute(units: List<ExportUnit>) {
        var bytes = 0L
        var lastUpdate = 0L
        for (unit in units) {
            currentCoroutineContext().ensureActive()
            try {
                val settings = mutableState.value
                val output =
                    writer.write(
                        unit, settings.destination, settings.directory, settings.format,
                        onCreated = { uri ->
                            mutableState.update { it.copy(activeUri = uri.toString()) }
                            persist()
                        }, onBytes = { count ->
                            bytes += count
                            val now = android.os.SystemClock.elapsedRealtime()
                            if (now - lastUpdate >= 300) {
                                mutableState.update { it.copy(processedBytes = bytes) }
                                lastUpdate = now
                            }
                        }, onChapter = { name -> mutableState.update { it.copy(current = name) } })
                mutableState.update {
                    it.copy(
                        activeUri = null,
                        processedBytes = bytes,
                        outputs = it.outputs + output
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                cleanup()
                mutableState.update {
                    it.copy(
                        failures = it.failures + ComicExportFailure(
                            unit.id, unit.name,
                            error.message ?: "写入失败"
                        ), processedBytes = bytes
                    )
                }
            }
            persist()
        }
        mutableState.update { it.copy(status = ComicExportStatus.COMPLETED, current = "导出完成") }
        persist()
    }

    private fun cleanup() {
        val uri = mutableState.value.activeUri ?: return
        val deleted = writer.delete(uri)
        mutableState.update {
            it.copy(
                activeUri = null, cleanupWarning = if (deleted) it.cleanupWarning
                else "无法清理未完成文件，请在保存位置手动删除：$uri"
            )
        }
    }

    fun cancel(reason: String = "用户取消导出") {
        scope.launch { commands.withLock { work?.cancel(CancellationException(reason)) } }
    }

    suspend fun awaitRestoration() {
        restoration.await()
    }

    suspend fun dismiss() = commands.withLock {
        restoration.await()
        if (!mutableState.value.running) {
            mutableState.value = ComicExportState()
            persist()
        }
    }

    private fun persist() = synchronized(taskFile) { writeAtomic(taskFile, mutableState.value) }
    private fun writeAtomic(file: AtomicFile, value: Any) {
        val output = file.startWrite()
        try {
            output.write(gson.toJson(value).toByteArray(Charsets.UTF_8))
            file.finishWrite(output)
        } catch (error: Exception) {
            file.failWrite(output)
            throw error
        }
    }
}
