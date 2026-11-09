package com.rchdliang.copymanga_neo.multiplatform.platform

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference

/** 使用用户默认钥匙串；密钥只经本进程内存传入 Security.framework。 */
internal class MacKeychain(private val service: String, private val api: MacKeychainApi = JnaMacKeychainApi()) {
    fun read(account: String): String? {
        val found = api.find(service, account)
        if (found.status == ITEM_NOT_FOUND) return null
        checkStatus(found.status)
        val data = requireNotNull(found.bytes) { "钥匙串返回了空凭据" }
        return try { data.toString(Charsets.UTF_8) } finally { data.fill(0) }
    }

    fun write(account: String, value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        try {
            require(bytes.size <= com.rchdliang.copymanga_neo.data.repository.portable.EnvelopeSecureCredentialStorage.MAX_BYTES) { "凭据数据过大" }
            checkStatus(api.upsert(service, account, bytes))
        }
        finally { bytes.fill(0) }
    }

    private fun checkStatus(status: Int) {
        check(status == 0) { "无法访问 macOS 登录钥匙串（OSStatus $status），请先解锁或授权；原凭据已保留" }
    }

    companion object { const val ITEM_NOT_FOUND = -25300 }
}

internal data class MacKeychainRead(val status: Int, val bytes: ByteArray? = null)
internal interface MacKeychainApi {
    fun find(service: String, account: String): MacKeychainRead
    fun upsert(service: String, account: String, value: ByteArray): Int
}

private class JnaMacKeychainApi : MacKeychainApi {
    private val security by lazy { Native.load("Security", MacSecurity::class.java) }
    private val core by lazy { Native.load("CoreFoundation", MacCoreFoundation::class.java) }
    override fun find(service: String, account: String): MacKeychainRead {
        val length = IntByReference()
        val password = PointerByReference()
        val serviceBytes = service.toByteArray(Charsets.UTF_8)
        val accountBytes = account.toByteArray(Charsets.UTF_8)
        val status = security.SecKeychainFindGenericPassword(null, serviceBytes.size, serviceBytes, accountBytes.size, accountBytes, length, password, null)
        if (status != 0) return MacKeychainRead(status)
        return try {
            check(length.value in 0..com.rchdliang.copymanga_neo.data.repository.portable.EnvelopeSecureCredentialStorage.MAX_BYTES) { "钥匙串凭据大小异常" }
            MacKeychainRead(status, if (length.value == 0) byteArrayOf() else requireNotNull(password.value).getByteArray(0, length.value))
        } finally { password.value?.let { security.SecKeychainItemFreeContent(null, it) } }
    }

    override fun upsert(service: String, account: String, value: ByteArray): Int {
        val serviceBytes = service.toByteArray(Charsets.UTF_8)
        val accountBytes = account.toByteArray(Charsets.UTF_8)
        val item = PointerByReference()
        val status = security.SecKeychainFindGenericPassword(null, serviceBytes.size, serviceBytes, accountBytes.size, accountBytes, null, null, item)
        return when (status) {
            0 -> try { security.SecKeychainItemModifyAttributesAndData(requireNotNull(item.value), null, value.size, value) }
                finally { item.value?.let(core::CFRelease) }
            MacKeychain.ITEM_NOT_FOUND -> security.SecKeychainAddGenericPassword(null, serviceBytes.size, serviceBytes, accountBytes.size, accountBytes, value.size, value, null)
            else -> status // 锁定、拒绝访问等错误不得当作不存在后覆盖。
        }
    }
}

internal interface MacSecurity : Library {
    fun SecKeychainFindGenericPassword(keychain: Pointer?, serviceLength: Int, service: ByteArray, accountLength: Int, account: ByteArray,
        passwordLength: IntByReference?, password: PointerByReference?, item: PointerByReference?): Int
    fun SecKeychainAddGenericPassword(keychain: Pointer?, serviceLength: Int, service: ByteArray, accountLength: Int, account: ByteArray,
        passwordLength: Int, password: ByteArray, item: PointerByReference?): Int
    fun SecKeychainItemModifyAttributesAndData(item: Pointer, attributes: Pointer?, length: Int, data: ByteArray): Int
    fun SecKeychainItemFreeContent(attributes: Pointer?, data: Pointer): Int
}
