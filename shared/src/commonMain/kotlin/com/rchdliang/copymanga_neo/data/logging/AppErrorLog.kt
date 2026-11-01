package com.rchdliang.copymanga_neo.data.logging

import com.rchdliang.copymanga_neo.domain.repository.ErrorLogRepository
import com.rchdliang.copymanga_neo.domain.repository.ErrorLogState
import com.rchdliang.copymanga_neo.multiplatform.app.portableReleaseBuildInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Clock

/** 进程级诊断出口；写盘失败不能覆盖原异常，也不能递归调用 UI 日志。 */
object AppErrorLog : ErrorLogRepository {
    private data class Sink(val store: ErrorLogStore, val platform: String)
    private val sink = MutableStateFlow<Sink?>(null)
    private val mutable = MutableStateFlow(ErrorLogState())
    override val state = mutable.asStateFlow()

    fun install(store: ErrorLogStore, platform: String) {
        sink.value = Sink(store, platform)
        updateSize()
    }

    fun record(level: String, tag: String, message: String, error: Throwable? = null, crash: Boolean = false) {
        if (error is CancellationException || (level != "W" && level != "E" && !crash)) return
        val target = sink.value ?: return
        try {
            val record = buildJsonObject {
                put("at", Clock.System.now().toString())
                put("kind", if (crash) "crash" else "error")
                put("level", level)
                put("platform", target.platform)
                put("version", portableReleaseBuildInfo.versionName)
                put("tag", redact(tag).take(160))
                put("message", redact(message).take(2000))
                error?.let { put("stack", redact(it.stackTraceToString()).take(12000)) }
            }.toString()
            target.store.append(record, crash)
            // 崩溃路径只做必要写入，避免额外读取整个日志。
            if (!crash) updateSize()
        } catch (_: Throwable) {
            mutable.value = mutable.value.copy(error = "错误日志保存失败，请检查存储空间")
        }
    }

    /** 移除常见凭据及 URL 查询参数；不采集请求体或请求头。 */
    internal fun redact(text: String): String {
        var value = text.replace(Regex("(?i)(https?://[^\\s?#]+)\\?[^\\s#]*"), "$1?[redacted]")
        value = value.replace(Regex("(?i)(Bearer\\s+)[A-Za-z0-9._~+/=-]+"), "$1[redacted]")
        value = value.replace(
            Regex("""(?i)(["']?(?:authorization|cookie|set-cookie|token|access_token|refresh_token|password|passwd|secret)["']?\s*[:=]\s*)(?:"[^"]*"|'[^']*'|[^\s,;}]+)"""),
            "$1[redacted]"
        )
        return value
    }

    private fun updateSize() {
        try {
            val target = sink.value ?: error("尚未初始化错误日志")
            mutable.value = ErrorLogState(target.store.size())
        } catch (_: Throwable) {
            mutable.value = mutable.value.copy(error = "错误日志读取失败，请检查存储空间")
        }
    }

    override suspend fun refresh() = withContext(Dispatchers.Default) { updateSize() }
    override suspend fun clear(): Result<Unit> = withContext(Dispatchers.Default) {
        operation { requireNotNull(sink.value).store.clear(); updateSize() }
    }
    override suspend fun export(): Result<ByteArray> = withContext(Dispatchers.Default) {
        operation { requireNotNull(sink.value).store.snapshot().encodeToByteArray() }
    }
    private inline fun <T> operation(block: () -> T): Result<T> = try { Result.success(block()) }
    catch (cancel: CancellationException) { throw cancel }
    catch (failure: Exception) {
        mutable.value = mutable.value.copy(error = "错误日志操作失败，请重试")
        Result.failure(failure)
    }
}
