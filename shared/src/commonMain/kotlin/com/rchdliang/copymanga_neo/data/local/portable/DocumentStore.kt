package com.rchdliang.copymanga_neo.data.local.portable

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

/** App-private persistence. Implementations must replace a document atomically or throw. */
interface DocumentStorage {
    suspend fun read(key: String): String?
    suspend fun write(key: String, value: String)
    suspend fun remove(key: String)
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
    private val maximumBytes: Int? = null
) {
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
    private suspend fun loadLocked(): Result<Unit> = attempt {
        val value = storage.read(key)?.let {
            checkSize(it)
            storageJson.decodeFromString(serializer, it)
        } ?: defaultValue
        validate(value)
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
            val encoded = storageJson.encodeToString(serializer, next)
            checkSize(encoded)
            storage.write(key, encoded)
            mutable.value = next
            mutableError.value = null
        }
    }
    private fun checkSize(value: String) {
        maximumBytes?.let { require(value.encodeToByteArray().size <= it) { "本地数据过大，请清理后重试" } }
    }
    private suspend fun attempt(block: suspend () -> Unit): Result<Unit> = try {
        block(); Result.success(Unit)
    } catch (cancel: CancellationException) { throw cancel }
    catch (error: Exception) {
        mutableError.value = "本地数据读写失败，原数据已保留：${error.message.orEmpty()}"
        Result.failure(error)
    }
}
