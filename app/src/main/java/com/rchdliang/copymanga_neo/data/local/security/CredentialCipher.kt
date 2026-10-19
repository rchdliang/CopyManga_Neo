package com.rchdliang.copymanga_neo.data.local.security

import java.io.IOException
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** 版本化 AES-GCM 容器；密钥由调用方提供，解密失败绝不生成替代密钥。 */
internal class CredentialCipher(
    private val identity: String,
    private val key: (create: Boolean) -> SecretKey
) {
    fun encrypt(plain: ByteArray, mayCreateKey: Boolean): ByteArray {
        require(plain.size <= MAX_BYTES) { "凭据过大" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(mayCreateKey))
        cipher.updateAAD(identity.toByteArray(Charsets.UTF_8) + HEADER)
        check(cipher.iv.size == 12)
        return HEADER + cipher.iv + cipher.doFinal(plain)
    }

    fun decrypt(encoded: ByteArray): ByteArray {
        if (encoded.size !in (HEADER.size + 12 + 16)..(MAX_BYTES + HEADER.size + 28) ||
            !encoded.copyOfRange(0, HEADER.size).contentEquals(HEADER)
        ) {
            throw IOException("不支持或损坏的凭据文件，原文件已保留")
        }
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                key(false),
                GCMParameterSpec(128, encoded.copyOfRange(HEADER.size, HEADER.size + 12))
            )
            cipher.updateAAD(identity.toByteArray(Charsets.UTF_8) + HEADER)
            return cipher.doFinal(encoded, HEADER.size + 12, encoded.size - HEADER.size - 12)
        } catch (_: Exception) {
            // 不携带可能含密钥别名或秘密片段的底层异常。
            throw IOException("无法解密登录信息，请解锁设备后重试；原文件已保留")
        }
    }

    companion object {
        const val MAX_BYTES = 1024 * 1024
        private val HEADER = byteArrayOf(0x43, 0x4d, 0x4e, 1)
    }
}
