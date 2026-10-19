package com.rchdliang.copymanga_neo.data.local.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferencesFileSerializer
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.io.IOException
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import kotlinx.coroutines.CancellationException

/** 密文只落在 noBackupFilesDir；认证信息不参与云备份或设备搬迁。 */
internal fun credentialCipher(context: Context, name: String): CredentialCipher {
    val alias = context.packageName + ".secrets." + name + ".v1"
    return CredentialCipher(alias) { create ->
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey) ?: run {
            check(create) { "设备凭据密钥不可用" }
            KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
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
    }
}

internal fun createSecureAuthDataStore(context: Context): DataStore<Preferences> {
    val file = File(context.noBackupFilesDir, "auth-v1.preferences_pb")
    val cipher = credentialCipher(context, "auth")
    return DataStoreFactory.create(
        serializer = object : Serializer<Preferences> {
            override val defaultValue = PreferencesFileSerializer.defaultValue
            override suspend fun readFrom(input: InputStream): Preferences {
                val plain = cipher.decrypt(readSecretBytes(input))
                return try {
                    PreferencesFileSerializer.readFrom(plain.inputStream())
                } catch (cancel: CancellationException) {
                    throw cancel
                } catch (_: Exception) {
                    throw IOException("登录信息格式无效，原文件已保留")
                } finally {
                    plain.fill(0)
                }
            }

            override suspend fun writeTo(t: Preferences, output: OutputStream) {
                val buffer = ByteArrayOutputStream()
                PreferencesFileSerializer.writeTo(t, buffer)
                val plain = buffer.toByteArray()
                try {
                    output.write(cipher.encrypt(plain, mayCreateKey = !file.exists()))
                } finally {
                    plain.fill(0); buffer.reset()
                }
            }
        },
        produceFile = { file }
    )
}

/** 同步 Cookie 回调使用 AtomicFile；失败保留旧密文，不回退到明文。 */
internal class EncryptedCookieFile(context: Context) {
    private val file = AtomicFile(File(context.noBackupFilesDir, "web-cookies-v1.bin"))
    private val cipher = credentialCipher(context, "cookies")
    fun read(): String? {
        if (!exists()) return null
        val plain = cipher.decrypt(file.openRead().use(::readSecretBytes))
        return try {
            plain.toString(Charsets.UTF_8)
        } finally {
            plain.fill(0)
        }
    }

    fun write(value: String) {
        val plain = value.toByteArray(Charsets.UTF_8)
        val encoded = try {
            cipher.encrypt(plain, mayCreateKey = !exists())
        } finally {
            plain.fill(0)
        }
        val output = file.startWrite()
        try {
            output.write(encoded)
            output.fd.sync()
            file.finishWrite(output)
        } catch (error: Exception) {
            file.failWrite(output); throw error
        }
        check(
            file.openRead().use(::readSecretBytes).contentEquals(encoded)
        ) { "Cookie 保存失败，未确认提交" }
    }

    private fun exists() = file.baseFile.exists() || File(file.baseFile.path + ".bak").exists()
}

private fun readSecretBytes(input: InputStream): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val count = input.read(buffer)
        if (count < 0) break
        if (output.size() + count > CredentialCipher.MAX_BYTES + 64) throw IOException("凭据文件过大")
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}
