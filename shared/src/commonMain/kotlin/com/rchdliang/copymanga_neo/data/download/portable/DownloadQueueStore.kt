package com.rchdliang.copymanga_neo.data.download.portable

import com.rchdliang.copymanga_neo.data.local.portable.DocumentStorage
import com.rchdliang.copymanga_neo.data.local.portable.DocumentStore
import com.rchdliang.copymanga_neo.domain.model.BookDownloadTask
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlin.time.Clock

@Serializable
private data class SavedQueueRow(val taskJson: String, val createdAt: Long)
@Serializable
private data class QueueSnapshot(val version: Int = 1, val rows: Map<String, SavedQueueRow> = emptyMap())

/** The fixed v2 task payload remains compatible with the original queue codec. */
class DownloadQueueStore(storage: DocumentStorage) {
    private val document = DocumentStore(storage, "download-queue", QueueSnapshot.serializer(), QueueSnapshot())
    suspend fun load(): List<BookDownloadTask> {
        document.load().getOrThrow()
        check(document.state.value.version == 1) { "不支持的下载队列存储版本" }
        return document.state.value.rows.values.sortedBy { it.createdAt }.mapNotNull { row ->
            try { DownloadQueueCodec.decode(row.taskJson) }
            catch (cancel: CancellationException) { throw cancel }
            catch (error: Exception) {
                DownloadLog.e("DownloadQueue", "下载队列条目无法读取，保留原记录", error)
                null
            }
        }
    }
    suspend fun save(task: BookDownloadTask) {
        document.update { snapshot ->
            check(snapshot.version == 1) { "不支持的下载队列存储版本" }
            val created = snapshot.rows[task.taskKey]?.createdAt ?: Clock.System.now().toEpochMilliseconds()
            snapshot.copy(rows = snapshot.rows + (task.taskKey to SavedQueueRow(DownloadQueueCodec.encode(task), created)))
        }.getOrThrow()
    }
    suspend fun remove(taskKey: String) {
        document.update { check(it.version == 1); it.copy(rows = it.rows - taskKey) }.getOrThrow()
    }
    suspend fun clear() {
        document.update { check(it.version == 1); it.copy(rows = emptyMap()) }.getOrThrow()
    }
}

internal object DownloadLog {
    fun d(tag: String, message: String, error: Throwable? = null) = write("D", tag, message, error)
    fun w(tag: String, message: String, error: Throwable? = null) = write("W", tag, message, error)
    fun e(tag: String, message: String, error: Throwable? = null) = write("E", tag, message, error)
    private fun write(level: String, tag: String, message: String, error: Throwable?) {
        println("$level/$tag: $message"); error?.let { println(it.stackTraceToString()) }
    }
}
