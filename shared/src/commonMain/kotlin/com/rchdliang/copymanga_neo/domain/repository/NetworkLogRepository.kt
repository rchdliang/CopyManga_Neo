package com.rchdliang.copymanga_neo.domain.repository

import kotlinx.coroutines.flow.StateFlow
import com.rchdliang.copymanga_neo.domain.platform.NetworkLogOutputStream

data class NetworkLogState(
    val enabled: Boolean = false,
    val bytes: Long = 0,
    val error: String? = null
)

/** 本机网络诊断记录；导出仅由用户主动发起。 */
interface NetworkLogRepository {
    val state: StateFlow<NetworkLogState>
    suspend fun setEnabled(enabled: Boolean)
    suspend fun clear(): Result<Unit>
    suspend fun export(output: NetworkLogOutputStream): Result<Unit>
    /** Suspendable sink for browser file streams. Native implementations may use the bounded adapter. */
    suspend fun exportChunks(write: suspend (ByteArray) -> Unit): Result<Unit> {
        val chunks = mutableListOf<ByteArray>()
        var size = 0L
        val collected = export(object : NetworkLogOutputStream() {
            override fun write(value: Int) = this.write(byteArrayOf(value.toByte()))
            override fun write(bytes: ByteArray) = this.write(bytes, 0, bytes.size)
            override fun write(bytes: ByteArray, offset: Int, length: Int) {
                require(offset >= 0 && length >= 0 && offset <= bytes.size - length)
                require(size + length <= 16L * 1024 * 1024) { "诊断日志超过流式适配上限，请使用原生文件导出" }
                size += length
                chunks += bytes.copyOfRange(offset, offset + length)
            }
        })
        if (collected.isFailure) return collected
        return try { for (chunk in chunks) write(chunk); Result.success(Unit) }
        catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
        catch (failure: Exception) { Result.failure(failure) }
    }
}
