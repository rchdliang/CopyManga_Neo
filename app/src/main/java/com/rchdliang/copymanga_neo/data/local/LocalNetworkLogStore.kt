package com.rchdliang.copymanga_neo.data.local

import android.content.Context
import androidx.core.content.edit
import com.google.gson.JsonObject
import com.rchdliang.copymanga_neo.BuildConfig
import com.rchdliang.copymanga_neo.domain.model.RequestMode
import com.rchdliang.copymanga_neo.domain.repository.NetworkLogRepository
import com.rchdliang.copymanga_neo.domain.repository.NetworkLogState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

/** 私有目录保存 JSONL，分卷轮换；记录失败不影响业务请求。 */
@Singleton
class LocalNetworkLogStore @Inject constructor(@ApplicationContext context: Context) :
    NetworkLogRepository {
    private val preferences =
        context.getSharedPreferences("local_network_log", Context.MODE_PRIVATE)

    // 排除在系统备份之外，卸载应用时一并删除。
    private val directory = File(context.noBackupFilesDir, "network_logs")
    private val lock = Any()
    private var generation = 0L
    private val mutableState =
        MutableStateFlow(NetworkLogState(preferences.getBoolean("enabled", false), size()))
    override val state = mutableState.asStateFlow()

    fun beginCapture(): Long? =
        synchronized(lock) { generation.takeIf { mutableState.value.enabled } }

    /** 只记录导入阶段和状态，不保存凭据、个人资料或异常消息。 */
    fun credentialImport(
        stage: String,
        mode: RequestMode? = null,
        status: Int? = null,
        code: Int? = null,
        exception: Exception? = null,
        loggedIn: Boolean? = null
    ) {
        val capture = beginCapture() ?: return
        append(capture, JsonObject().apply {
            addProperty("timestamp_ms", System.currentTimeMillis())
            addProperty("event", "credential_import")
            addProperty("stage", stage)
            addProperty("client_version", BuildConfig.VERSION_NAME)
            mode?.let { addProperty("request_mode", it.name) }
            status?.let { addProperty("status", it) }
            code?.let { addProperty("code", it) }
            exception?.let { addProperty("exception", it.javaClass.simpleName) }
            loggedIn?.let { addProperty("logged_in", it) }
        })
    }

    fun catalogFailure(pathWord: String, error: Exception) {
        val capture = beginCapture() ?: return
        append(capture, JsonObject().apply {
            addProperty("timestamp_ms", System.currentTimeMillis())
            addProperty("event", "catalog_processing_failure")
            addProperty("comic_path", pathWord)
            addProperty("exception", error.javaClass.simpleName)
            addProperty(
                "stage",
                if (error is javax.crypto.BadPaddingException) "AES padding validation" else "catalog parsing"
            )
            addProperty("action", "fallback_to_chapter_api")
        })
    }

    fun append(generation: Long, entry: JsonObject) = synchronized(lock) {
        if (generation != this.generation || !mutableState.value.enabled) return@synchronized
        try {
            check(directory.exists() || directory.mkdirs())
            var bytes = (entry.toString() + "\n").toByteArray(Charsets.UTF_8)
            if (bytes.size > 5 * 1024 * 1024) {
                entry.remove("body")
                entry.remove("body_text")
                entry.remove("form")
                entry.addProperty("body_note", "记录编码后超过分卷上限，正文已省略")
                entry.addProperty("body_truncated", true)
                bytes = (entry.toString() + "\n").toByteArray(Charsets.UTF_8)
            }
            val active = file(0)
            if (active.length() + bytes.size > 5L * 1024 * 1024) {
                if (file(3).exists()) check(file(3).delete())
                for (index in 2 downTo 0) if (file(index).exists()) check(
                    file(index).renameTo(
                        file(
                            index + 1
                        )
                    )
                )
            }
            file(0).appendBytes(bytes)
            mutableState.value = mutableState.value.copy(bytes = size(), error = null)
        } catch (_: Exception) {
            mutableState.value = mutableState.value.copy(error = "日志写入失败，请检查剩余存储空间")
        }
    }

    override suspend fun setEnabled(enabled: Boolean) = withContext(Dispatchers.IO) {
        synchronized(lock) {
            generation++
            preferences.edit { putBoolean("enabled", enabled) }
            mutableState.value = mutableState.value.copy(enabled = enabled)
        }
    }

    override suspend fun clear(): Result<Unit> = ioResult {
        synchronized(lock) {
            generation++
            for (index in 0..3) if (file(index).exists()) check(file(index).delete())
            mutableState.value = mutableState.value.copy(bytes = 0, error = null)
        }
    }

    override suspend fun export(output: OutputStream): Result<Unit> = ioResult {
        check(directory.exists() || directory.mkdirs())
        val snapshot = File.createTempFile("export-", ".tmp", directory)
        try {
            synchronized(lock) {
                // 本地快照固定分卷内容；写入用户选择的目标时不持有日志锁。
                snapshot.outputStream().use { target ->
                    target.write((JsonObject().apply {
                        addProperty("event", "export_info")
                        addProperty("format", "CopyManga network log v1")
                        addProperty("client_version", BuildConfig.VERSION_NAME)
                        addProperty("body_limit_bytes", 2097152)
                        addProperty("credentials", "redacted")
                    }.toString() + "\n").toByteArray())
                    for (index in 3 downTo 0) if (file(index).exists()) file(index).inputStream()
                        .use { it.copyTo(target) }
                }
            }
            snapshot.inputStream().use { it.copyTo(output) }
            output.flush()
        } finally {
            snapshot.delete()
        }
    }

    private fun file(index: Int) = File(directory, "network-$index.jsonl")
    private fun size() = (0..3).sumOf { file(it).length() }
    private suspend fun ioResult(block: () -> Unit): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            block(); Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
