package com.rchdliang.copymanga_neo.data.repository.portable

import com.rchdliang.copymanga_neo.domain.model.RequestMode
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Raw strings may exist only in memory and OS-protected storage, never ordinary files or logs. */
interface SecureCredentialEnvelopeBackend {
    /** null means genuinely absent. Locked, unreadable and malformed records must throw. */
    suspend fun readEnvelope(): String?
    /** Replace one protected item atomically. Empty snapshots are written, never deleted. */
    suspend fun writeEnvelope(value: String)
    suspend fun readLegacy(mode: RequestMode): String?
}

@Serializable
private data class CredentialEnvelope(val version: Int, val sessions: Map<RequestMode, StoredCredential>)

/** Migrate only when the new item is absent. Legacy records are retained until explicit cleanup. */
class EnvelopeSecureCredentialStorage(
    private val backend: SecureCredentialEnvelopeBackend,
    override val persistent: Boolean = true
) : SecureCredentialStorage {
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private fun checkSize(value: String) {
        require(value.encodeToByteArray().size <= MAX_BYTES) { "安全凭据记录过大" }
    }
    private fun encode(values: Map<RequestMode, StoredCredential>): String =
        json.encodeToString(CredentialEnvelope.serializer(), CredentialEnvelope(version = 1, sessions = values)).also(::checkSize)
    private fun decode(value: String): Map<RequestMode, StoredCredential> {
        checkSize(value)
        // Do not expose serialization messages that may contain fragments of secret values.
        val envelope = try { json.decodeFromString(CredentialEnvelope.serializer(), value) }
        catch (_: Exception) { error("安全凭据记录格式无效；原记录已保留") }
        require(envelope.version == 1) { "不支持的安全凭据记录版本" }
        return envelope.sessions
    }
    override suspend fun readAll(): Map<RequestMode, StoredCredential> = mutex.withLock {
        backend.readEnvelope()?.let { return@withLock decode(it) }
        val legacy = buildMap {
            RequestMode.entries.forEach { mode ->
                backend.readLegacy(mode)?.let { raw ->
                    checkSize(raw)
                    val value = try { json.decodeFromString(StoredCredential.serializer(), raw) }
                    catch (_: Exception) { error("旧版安全凭据格式无效；原记录已保留") }
                    put(mode, value)
                }
            }
        }
        // A genuinely fresh installation needs no secure write/unlock merely to open the app.
        // Explicit writeAll(emptyMap()) still writes a tombstone after an intentional clear.
        if (legacy.isEmpty()) return@withLock emptyMap()
        // Publish all modes at once before exposing the migrated session. No legacy deletion is
        // necessary for correctness; a durable empty envelope also prevents later resurrection.
        backend.writeEnvelope(encode(legacy))
        legacy
    }
    override suspend fun writeAll(values: Map<RequestMode, StoredCredential>) = mutex.withLock {
        backend.writeEnvelope(encode(values))
    }
    companion object { const val MAX_BYTES = 1024 * 1024 }
}
