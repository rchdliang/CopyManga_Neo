@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.UnsafeNumber::class, kotlinx.cinterop.BetaInteropApi::class)

package com.rchdliang.copymanga_neo.multiplatform.platform

import com.rchdliang.copymanga_neo.data.repository.portable.SecureCredentialStorage
import com.rchdliang.copymanga_neo.data.repository.portable.EnvelopeSecureCredentialStorage
import com.rchdliang.copymanga_neo.data.repository.portable.SecureCredentialEnvelopeBackend
import com.rchdliang.copymanga_neo.data.repository.portable.StoredCredential
import com.rchdliang.copymanga_neo.domain.model.RequestMode
import kotlinx.cinterop.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.CoreFoundation.*
import platform.Foundation.*
import platform.Security.*
import platform.posix.memcpy

/** Both modes, app identity, and remembered passwords share one device-only Keychain record. */
class IosSecureCredentialStorage(
    private val service: String = "com.rchdliang.copymanga_neo.credentials"
) : SecureCredentialStorage {
    override val persistent = true
    private val envelope = EnvelopeSecureCredentialStorage(object : SecureCredentialEnvelopeBackend {
        override suspend fun readEnvelope() = readProtected("sessions-v2")
        override suspend fun writeEnvelope(value: String) = writeProtected(value)
        override suspend fun readLegacy(mode: RequestMode) = readProtected(mode.name)
    })
    override suspend fun readAll() = envelope.readAll()
    override suspend fun writeAll(values: Map<RequestMode, StoredCredential>) = envelope.writeAll(values)

    private suspend fun readProtected(account: String): String? = withContext(Dispatchers.Default) {
        keychainQuery(account) { base ->
            memScoped {
                val output = alloc<CFTypeRefVar>()
                val status = withIosCfDictionary(base + listOf(kSecReturnData to kCFBooleanTrue, kSecMatchLimit to kSecMatchLimitOne)) {
                    SecItemCopyMatching(it, output.ptr)
                }
                if (status == errSecItemNotFound) null else {
                    checkStatus(status)
                    val data = CFBridgingRelease(output.value) as? NSData ?: error("钥匙串返回了无效数据")
                    require(data.length <= 1024u * 1024u) { "钥匙串记录过大" }
                    val bytes = data.copyBytes()
                    try { bytes.decodeToString(throwOnInvalidSequence = true) } finally { bytes.fill(0) }
                }
            }
        }
    }

    private suspend fun writeProtected(value: String): Unit = withContext(Dispatchers.Default) {
        keychainQuery("sessions-v2") { base ->
            val bytes = value.encodeToByteArray()
            require(bytes.size <= EnvelopeSecureCredentialStorage.MAX_BYTES) { "钥匙串记录过大" }
            val data = bytes.asData()
            val retained = CFBridgingRetain(data)
            try {
                val attributes = listOf(kSecValueData to retained, kSecAttrAccessible to kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly)
                val status = withIosCfDictionary(base) { query ->
                    withIosCfDictionary(attributes) { update -> SecItemUpdate(query, update) }
                }
                if (status == errSecItemNotFound) checkStatus(withIosCfDictionary(base + attributes) { SecItemAdd(it, null) })
                else checkStatus(status)
            } finally { CFBridgingRelease(retained); bytes.fill(0) }
        }
    }

    private fun <T> keychainQuery(account: String, action: (List<Pair<CFStringRef?, CFTypeRef?>>) -> T): T {
        val retainedService = CFBridgingRetain(service)
        val retainedAccount = CFBridgingRetain(account)
        return try { action(listOf(kSecClass to kSecClassGenericPassword, kSecAttrService to retainedService, kSecAttrAccount to retainedAccount)) }
        finally { CFBridgingRelease(retainedAccount); CFBridgingRelease(retainedService) }
    }
    private fun checkStatus(status: Int) { check(status == errSecSuccess) { "系统钥匙串操作失败（$status）；凭据未写入普通文件" } }
}

internal fun <T> withIosCfDictionary(values: List<Pair<CFStringRef?, CFTypeRef?>>, action: (CFDictionaryRef?) -> T): T = memScoped {
    val keys = allocArrayOf(*values.map { it.first }.toTypedArray())
    val objects = allocArrayOf(*values.map { it.second }.toTypedArray())
    val dictionary = CFDictionaryCreate(kCFAllocatorDefault, keys.reinterpret(), objects.reinterpret(), values.size.convert(), null, null)
    try { action(dictionary) } finally { CFBridgingRelease(dictionary) }
}

internal fun ByteArray.asData(): NSData = if (isEmpty()) NSData() else usePinned {
    NSData.create(bytes = it.addressOf(0), length = size.toULong())
}
internal fun NSData.copyBytes(): ByteArray {
    require(length <= Int.MAX_VALUE.toULong()) { "文件过大" }
    return ByteArray(length.toInt()).also { target -> if (target.isNotEmpty()) target.usePinned { memcpy(it.addressOf(0), bytes, length) } }
}
