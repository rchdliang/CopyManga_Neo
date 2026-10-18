package com.rchdliang.copymanga_neo.data.repository.portable

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import com.rchdliang.copymanga_neo.domain.model.RequestMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** 全部模式在同一加密信封中原子替换；密钥留在 Android Keystore，密文不参与备份。 */
class AndroidSecureCredentialStorage(context: Context) : SecureCredentialStorage {
    private val backend = AndroidCredentialBackend(context.applicationContext)
    private val envelope = EnvelopeSecureCredentialStorage(backend)
    override val persistent = true
    override suspend fun readAll() = envelope.readAll()
    override suspend fun writeAll(values: Map<RequestMode, StoredCredential>) =
        envelope.writeAll(values)
}

private class AndroidCredentialBackend(context: Context) : SecureCredentialEnvelopeBackend {
    private val file = AtomicFile(File(context.noBackupFilesDir, "shared-credentials.bin"))
    private val alias = context.packageName + ".shared.credentials.v1"
    private fun key(create: Boolean): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        check(create) { "凭据密钥不可用，原加密记录已保留" }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(
                KeyGenParameterSpec.Builder(
                    alias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256).build()
            )
        }.generateKey()
    }

    override suspend fun readEnvelope(): String? = withContext(Dispatchers.IO) {
        if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) return@withContext null
        try {
            val bytes = file.openRead().use {
                require(it.channel.size() <= EnvelopeSecureCredentialStorage.MAX_BYTES + 64) { "凭据文件过大" }
                it.readBytes()
            }
            require(bytes.size >= 30 && bytes[0] == 1.toByte()) { "凭据格式无效" }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                key(false),
                GCMParameterSpec(128, bytes.copyOfRange(1, 13))
            )
            cipher.updateAAD(alias.toByteArray(Charsets.UTF_8))
            val plain = cipher.doFinal(bytes, 13, bytes.size - 13)
            try {
                plain.toString(Charsets.UTF_8)
            } finally {
                plain.fill(0)
            }
        } catch (_: Exception) {
            throw IOException("无法读取安全凭据，原记录已保留")
        }
    }

    override suspend fun writeEnvelope(value: String): Unit = withContext(Dispatchers.IO) {
        val plain = value.toByteArray(Charsets.UTF_8)
        require(plain.size <= EnvelopeSecureCredentialStorage.MAX_BYTES)
        val encrypted = try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key(!file.baseFile.exists()))
            cipher.updateAAD(alias.toByteArray(Charsets.UTF_8))
            byteArrayOf(1) + cipher.iv + cipher.doFinal(plain)
        } finally {
            plain.fill(0)
        }
        val output = file.startWrite()
        try {
            output.write(encrypted)
            output.fd.sync()
            file.finishWrite(output)
        } catch (error: Exception) {
            file.failWrite(output); throw IOException("安全凭据保存失败", error)
        }
        // AtomicFile 某些提交错误只记录日志，必须确认实际发布的是本次密文。
        val published = file.openRead().use { input ->
            require(input.channel.size() == encrypted.size.toLong()) { "安全凭据提交未确认" }
            input.readBytes()
        }
        check(published.contentEquals(encrypted)) { "安全凭据提交未确认" }
    }

    override suspend fun readLegacy(mode: RequestMode): String? = null
}
