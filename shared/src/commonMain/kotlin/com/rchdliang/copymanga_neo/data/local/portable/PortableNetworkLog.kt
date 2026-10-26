package com.rchdliang.copymanga_neo.data.local.portable

import com.rchdliang.copymanga_neo.data.remote.portable.NetworkDiagnostic
import com.rchdliang.copymanga_neo.domain.platform.NetworkLogOutputStream
import com.rchdliang.copymanga_neo.domain.repository.NetworkLogRepository
import com.rchdliang.copymanga_neo.domain.repository.NetworkLogState
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import kotlin.time.Clock

@Serializable
private data class NetworkLogSettings(val enabled: Boolean = false, val activeSlot: Int = 0)

/** 诊断事件以 JSONL 追加，开关独立保存；启动只读小型设置和文件大小，不解析历史正文。 */
class PortableNetworkLog(private val storage: DocumentStorage, scope: CoroutineScope) : NetworkLogRepository {
    private val settings = DocumentStore(storage, SETTINGS_KEY, NetworkLogSettings.serializer(), NetworkLogSettings(),
        validate = { require(it.activeSlot in 0 until SLOT_COUNT) })
    private val events = Channel<String>(128, BufferOverflow.DROP_OLDEST)
    private val restored = CompletableDeferred<Unit>()
    private val mutex = Mutex()
    private val mutable = MutableStateFlow(NetworkLogState())
    override val state = mutable.asStateFlow()
    private val sizes = LongArray(SLOT_COUNT)
    private var initialized = false

    init {
        scope.launch {
            restored.await()
            for (line in events) mutex.withLock {
                attempt {
                    if (!initialized || !settings.state.value.enabled) return@attempt
                    val record = "\n$line\n"
                    val bytes = record.encodeToByteArray().size
                    if (bytes > SEGMENT_BYTES) return@attempt
                    var slot = settings.state.value.activeSlot
                    // 仅 stat，不读取文件；上次写入被进程中断时也能恢复准确容量。
                    sizes[slot] = storage.size(key(slot))
                    if (sizes[slot] + bytes > SEGMENT_BYTES) {
                        slot = (slot + 1) % SLOT_COUNT
                        storage.remove(key(slot))
                        sizes[slot] = 0
                        settings.update { it.copy(activeSlot = slot) }.fold({}, { throw it })
                    }
                    storage.append(key(slot), record)
                    sizes[slot] += bytes
                    publish()
                }
            }
        }
    }

    suspend fun initialize(): Result<Unit> = mutex.withLock {
        attempt { initializeLocked() }
    }.also { restored.complete(Unit) }

    private suspend fun initializeLocked() {
        if (initialized) return
        if (storage.read(SETTINGS_KEY) == null) {
            // 旧日志可丢弃，只从文件头保留原开关；不反序列化其 lines 数组。
            val prefix = try { storage.readPrefix(LEGACY_KEY, 1024).orEmpty() }
                catch (cancel: CancellationException) { throw cancel }
                catch (_: Exception) { "" }
            val enabled = Regex("\"enabled\"\\s*:\\s*(true|false)").find(prefix)?.groupValues?.get(1) == "true"
            settings.update { it.copy(enabled = enabled) }.fold({}, { throw it })
        } else settings.ensureLoaded().fold({}, { throw it })
        for (slot in sizes.indices) sizes[slot] = storage.size(key(slot))
        storage.remove(LEGACY_KEY)
        initialized = true
        publish()
    }

    fun record(event: NetworkDiagnostic) {
        if (restored.isCompleted && !state.value.enabled) return
        val json = buildJsonObject {
            put("at", Clock.System.now().toString()); put("phase", event.phase); put("method", event.method)
            put("host", event.host); put("endpoint", event.endpoint); put("mode", event.mode.name)
            put("generation", event.generation); event.status?.let { put("status", it) }; event.businessCode?.let { put("businessCode", it) }
            event.elapsedMillis?.let { put("elapsedMillis", it) }; event.bytes?.let { put("bytes", it) }
            event.failureKind?.let { put("failureKind", it) }; put("outcomeUnknown", event.outcomeUnknown)
        }.toString()
        // 以 ASCII JSON 转义非 ASCII 字符，进程中断不会留下半个 UTF-8 字符导致整段无法读取。
        events.trySend(if (json.all { it.code < 128 }) json else buildString {
            for (char in json) {
                if (char.code < 128) append(char)
                else append("\\u").append(char.code.toString(16).padStart(4, '0'))
            }
        })
    }

    override suspend fun setEnabled(enabled: Boolean) {
        val result = mutex.withLock { attempt {
            initializeLocked()
            settings.update { it.copy(enabled = enabled) }.fold({}, { throw it })
            publish()
        } }
        restored.complete(Unit)
        result.fold({}, { throw it })
    }

    override suspend fun clear(): Result<Unit> = mutex.withLock { attempt {
        initializeLocked()
        for (slot in sizes.indices) {
            storage.remove(key(slot))
            sizes[slot] = 0
        }
        settings.update { it.copy(activeSlot = 0) }.fold({}, { throw it })
        publish()
    } }.also { restored.complete(Unit) }

    override suspend fun exportChunks(write: suspend (ByteArray) -> Unit): Result<Unit> = withContext(Dispatchers.Default) {
        mutex.withLock { attempt {
            initializeLocked()
            val active = settings.state.value.activeSlot
            // 从最旧分段输出，每次最多读取 256 KiB；解析仅发生在用户主动导出时。
            for (step in 1..SLOT_COUNT) {
                val text = storage.read(key((active + step) % SLOT_COUNT)) ?: continue
                for (line in text.lineSequence().filter(String::isNotBlank)) {
                    // 每次追加先换行：进程被杀留下的半行不会污染下一条，只在导出时丢弃残行。
                    val complete = try { Json.parseToJsonElement(line) is JsonObject }
                        catch (_: kotlinx.serialization.SerializationException) { false }
                        catch (_: IllegalArgumentException) { false }
                    if (complete) write((line + "\n").encodeToByteArray())
                }
            }
            publish()
        } }.also { restored.complete(Unit) }
    }

    override suspend fun export(output: NetworkLogOutputStream): Result<Unit> = withContext(Dispatchers.Default) {
        exportChunks { output.write(it) }.fold(
            onSuccess = { attempt { output.flush() } },
            onFailure = { Result.failure(it) }
        )
    }

    private fun publish() { mutable.value = NetworkLogState(settings.state.value.enabled, sizes.sum()) }
    private suspend fun attempt(block: suspend () -> Unit): Result<Unit> = try {
        block(); Result.success(Unit)
    } catch (cancel: CancellationException) { throw cancel }
    catch (error: Exception) {
        mutable.value = NetworkLogState(settings.state.value.enabled, sizes.sum(), "诊断日志读写失败，请检查存储空间后重试")
        Result.failure(error)
    }

    private fun key(slot: Int) = "network-log-$slot.jsonl"
    private companion object {
        const val SETTINGS_KEY = "network-log-settings.json"
        const val LEGACY_KEY = "network-diagnostics.json"
        const val SEGMENT_BYTES = 256 * 1024
        const val SLOT_COUNT = 16
    }
}
