package com.rchdliang.copymanga_neo.data.local.portable

import com.rchdliang.copymanga_neo.data.remote.portable.NetworkDiagnostic
import com.rchdliang.copymanga_neo.domain.platform.NetworkLogOutputStream
import com.rchdliang.copymanga_neo.domain.repository.NetworkLogRepository
import com.rchdliang.copymanga_neo.domain.repository.NetworkLogState
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import kotlin.time.Clock

@Serializable
private data class LogDocument(val enabled: Boolean = false, val lines: List<String> = emptyList())

/** Receives only the transport's sanitized event schema, never raw URLs, headers, or bodies. */
class PortableNetworkLog(storage: DocumentStorage, scope: CoroutineScope) : NetworkLogRepository {
    private val document = DocumentStore(storage, "network-diagnostics.json", LogDocument.serializer(), LogDocument())
    private val events = Channel<String>(128, BufferOverflow.DROP_OLDEST)
    override val state = combine(document.state, document.error) { data, error ->
        NetworkLogState(data.enabled, data.lines.sumOf { it.encodeToByteArray().size.toLong() + 1 }, error)
    }.stateIn(scope, SharingStarted.Eagerly, NetworkLogState())
    init { scope.launch { for (line in events) document.update { data ->
        if (!data.enabled) data else {
            val lines = (data.lines + line).toMutableList()
            var bytes = lines.sumOf { it.encodeToByteArray().size + 1 }
            while (bytes > 4 * 1024 * 1024 && lines.isNotEmpty()) bytes -= lines.removeAt(0).encodeToByteArray().size + 1
            data.copy(lines = lines)
        }
    } } }
    suspend fun initialize() = document.load()
    fun record(event: NetworkDiagnostic) {
        if (!document.state.value.enabled) return
        events.trySend(buildJsonObject {
            put("at", Clock.System.now().toString()); put("phase", event.phase); put("method", event.method)
            put("host", event.host); put("endpoint", event.endpoint); put("mode", event.mode.name)
            put("generation", event.generation); event.status?.let { put("status", it) }; event.businessCode?.let { put("businessCode", it) }
            event.elapsedMillis?.let { put("elapsedMillis", it) }; event.bytes?.let { put("bytes", it) }
            event.failureKind?.let { put("failureKind", it) }; put("outcomeUnknown", event.outcomeUnknown)
        }.toString())
    }
    override suspend fun setEnabled(enabled: Boolean) { document.update { it.copy(enabled = enabled) }.getOrThrow() }
    override suspend fun clear() = document.update { it.copy(lines = emptyList()) }
    override suspend fun exportChunks(write: suspend (ByteArray) -> Unit): Result<Unit> = try {
        document.load().getOrThrow()
        for (line in document.state.value.lines) write((line + "\n").encodeToByteArray())
        Result.success(Unit)
    } catch (cancel: CancellationException) { throw cancel } catch (failure: Exception) { Result.failure(failure) }
    override suspend fun export(output: NetworkLogOutputStream): Result<Unit> = withContext(Dispatchers.Default) {
        try {
            document.load().getOrThrow()
            for (line in document.state.value.lines) { output.write(line.encodeToByteArray()); output.write(10) }
            output.flush()
            Result.success(Unit)
        } catch (cancel: CancellationException) { throw cancel } catch (e: Exception) { Result.failure(e) }
    }
}
