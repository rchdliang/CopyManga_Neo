package com.rchdliang.copymanga_neo.data.repository.portable

import com.rchdliang.copymanga_neo.data.remote.portable.PortableSession
import com.rchdliang.copymanga_neo.domain.model.RequestMode

/** Implement using the OS keychain/keystore. Never serialize these records to ordinary preferences. */
interface SecureCredentialStorage {
    val persistent: Boolean
    suspend fun readAll(): Map<RequestMode, StoredCredential>
    /** Atomically replace the whole snapshot, including an empty snapshot tombstone. */
    suspend fun writeAll(values: Map<RequestMode, StoredCredential>)
    suspend fun read(mode:RequestMode):StoredCredential? = readAll()[mode]
}
@kotlinx.serialization.Serializable
data class StoredCredential(
    val session:PortableSession,
    val savedUsername:String? = null,
    val savedPassword:String? = null,
    val rememberPassword:Boolean = false,
    val autoLogin:Boolean = false,
    val appIdentity:com.rchdliang.copymanga_neo.domain.model.AppCredentialIdentity? = null,
)
/** Session-only fallback. Requests to remember a password are explicitly rejected by the repository. */
class MemorySecureCredentialStorage:SecureCredentialStorage {
    override val persistent=false
    private val values=kotlinx.coroutines.flow.MutableStateFlow<Map<RequestMode,StoredCredential>>(emptyMap())
    override suspend fun readAll()=values.value
    override suspend fun writeAll(values:Map<RequestMode,StoredCredential>) {
        require(values.values.all{!it.rememberPassword&&!it.autoLogin&&it.savedPassword==null}){"此平台没有安全持久化凭据存储"}
        this.values.value=values.toMap()
    }
}
