package com.rchdliang.copymanga_neo.data.local.portable

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.*

/** App-private persistence. Implementations must replace a document atomically or throw. */
interface DocumentStorage {
    suspend fun read(key: String): String?
    suspend fun write(key: String, value: String)
    suspend fun remove(key: String)
    /** 日志专用追加；文件后端直接 APPEND。键值后端仅替换当前有界分段，不重写全部历史。 */
    suspend fun append(key: String, value: String) = write(key, read(key).orEmpty() + value)
    suspend fun size(key: String): Long = read(key)?.encodeToByteArray()?.size?.toLong() ?: 0L
    /** 只用于保留旧日志开关；不读取、解析或迁移旧日志正文。 */
    suspend fun readPrefix(key: String, maxChars: Int): String? = read(key)?.take(maxChars)
}

expect fun platformDocumentStorage(directory: String): DocumentStorage

internal val storageJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

/** Load failure never silently overwrites the original; mutations require a successful read. */
class DocumentStore<T>(
    private val storage: DocumentStorage,
    private val key: String,
    private val serializer: KSerializer<T>,
    private val defaultValue: T,
    private val validate: (T) -> Unit = {},
    private val maximumBytes: Int? = null,
    private val schemaVersion: Int = 1,
    private val migrations: Map<Int, (JsonElement) -> JsonElement> = emptyMap()
) {
    init {
        require(schemaVersion >= 1)
    }

    // 磁盘协议严格读取；同版本出现未知字段也不能忽略后全量抹掉。
    private val diskJson = Json { encodeDefaults = true }
    private val format = "copymanga.document/$key"
    private val mutex = Mutex()
    private var loaded = false
    private val mutable = MutableStateFlow(defaultValue)
    val state = mutable.asStateFlow()
    private val mutableError = MutableStateFlow<String?>(null)
    val error = mutableError.asStateFlow()

    suspend fun load(): Result<Unit> = mutex.withLock { loadLocked() }

    /** 首次读取合并到同一把锁；显式 load 仍用于重新读取和修复后重试。 */
    suspend fun ensureLoaded(): Result<Unit> = mutex.withLock {
        if (loaded) Result.success(Unit) else loadLocked()
    }

    /** 首次订阅先恢复磁盘值，避免后置初始化时把默认值误当成用户设置；解析不占用 UI 线程。 */
    fun loadedState(): Flow<T> = flow {
        ensureLoaded().fold({}, { throw it })
        emitAll(state)
    }.flowOn(Dispatchers.Default)

    private suspend fun loadLocked(): Result<Unit> = attempt {
        val original = storage.read(key)
        val decoded = original?.let {
            checkSize(it)
            DocumentEnvelope.decode(it, format, schemaVersion, migrations)
        }
        val value =
            decoded?.let { diskJson.decodeFromJsonElement(serializer, it.payload) } ?: defaultValue
        validate(value)
        // 单文档迁移先保留原件，校验全部通过后原子提交；中断后可从原版本重试。
        if (decoded != null && decoded.changed) {
            val encoded = encode(value)
            storage.write("$key.before-v$schemaVersion", requireNotNull(original))
            storage.write(key, encoded)
        }
        mutable.value = value
        loaded = true
        mutableError.value = null
    }.onFailure { loaded = false }

    suspend fun update(transform: (T) -> T): Result<Unit> = mutex.withLock {
        if (!loaded) loadLocked().exceptionOrNull()?.let { return@withLock Result.failure(it) }
        attempt {
            val next = transform(mutable.value)
            validate(next)
            if (next == mutable.value) {
                mutableError.value = null
                return@attempt
            }
            val encoded = encode(next)
            storage.write(key, encoded)
            mutable.value = next
            mutableError.value = null
        }
    }

    private fun encode(value: T): String = DocumentEnvelope.encode(
        format, schemaVersion, diskJson.encodeToJsonElement(serializer, value)
    ).also(::checkSize)

    private fun checkSize(value: String) {
        maximumBytes?.let { require(value.encodeToByteArray().size <= it) { "本地数据过大，请清理后重试" } }
    }

    private suspend fun attempt(block: suspend () -> Unit): Result<Unit> = try {
        block(); Result.success(Unit)
    } catch (cancel: CancellationException) {
        throw cancel
    } catch (error: Exception) {
        mutableError.value = "本地数据读写失败，原数据已保留；请检查版本和可用空间后重试"
        Result.failure(error)
    }
}

/** 每类文档独立演进；转换函数只处理内存 JSON，不能产生外部副作用。 */
internal object DocumentEnvelope {
    data class Decoded(val payload: JsonElement, val changed: Boolean)

    fun decode(
        raw: String, format: String, target: Int,
        migrations: Map<Int, (JsonElement) -> JsonElement>
    ): Decoded {
        val root = storageJson.parseToJsonElement(raw)
        val envelope = root as? JsonObject
        // 仅接纳尚未公开的裸 payload，作为版本 0；公开后的全部写入都有信封。
        var version: Int
        var payload: JsonElement
        if (envelope != null && ("format" in envelope || "schemaVersion" in envelope || "payload" in envelope)) {
            require(
                envelope.keys == setOf(
                    "format",
                    "schemaVersion",
                    "payload"
                )
            ) { "本地文档信封无效" }
            require(envelope["format"] == JsonPrimitive(format)) { "本地文档类型不匹配" }
            val field = envelope["schemaVersion"] as? JsonPrimitive
            require(field != null && !field.isString && field.intOrNull != null) { "本地文档版本无效" }
            version = field.int
            require(version >= 1) { "本地文档版本无效" }
            payload = envelope.getValue("payload")
        } else {
            version = 0
            payload = root
        }
        require(version <= target) { "本地数据来自更高版本，请升级应用后重试" }
        val changed = version != target
        if (version == 0) version = 1
        while (version < target) {
            val migrate = migrations[version] ?: error("缺少本地数据迁移步骤：$version")
            payload = migrate(payload)
            version++
        }
        return Decoded(payload, changed)
    }

    fun encode(format: String, version: Int, payload: JsonElement): String = buildJsonObject {
        put("format", format)
        put("schemaVersion", version)
        put("payload", payload)
    }.toString()
}
