package com.rchdliang.copymanga_neo.data.remote.util

import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.regex.Pattern
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * 章节内容解密工具类
 */
object ChapterDecryptor {
    private const val TAG = "ChapterDecryptor"
    private val gson = Gson()

    /**
     * 从 HTML 源码中提取 cct 和 contentKey 并解密得到图片列表
     * 上下章节 UUID 由上层（Repository）从章节列表 API 获取
     */
    fun decryptChapterImages(html: String): Result<List<String>> {
        try {
            val cct = extractVariable(html, "cct")
            val contentKey = extractVariable(html, "contentKey")

            if (cct == null && contentKey == null) {
                val msg = "无密钥及图片列表"
                logError(msg)
                return Result.failure(Exception(msg))
            }

            if (cct != null && contentKey == "") {
                val msg = "contentKey为空"
                logError(msg)
                return Result.failure(Exception(msg))
            }

            if (cct == null || contentKey == null) {
                val msg = "解析章节内容失败：缺少 cct 或 contentKey"
                logError(msg)
                return Result.failure(Exception(msg))
            }

            val imageUrls = decrypt(cct, contentKey)
            if (imageUrls.isEmpty()) {
                val msg = "解析章节内容失败：图片列表为空"
                logError(msg)
                return Result.failure(Exception(msg))
            }

            return Result.success(imageUrls)
        } catch (e: Exception) {
            logError("Error decrypting chapter images", e)
            return Result.failure(e)
        }
    }

    private fun logError(message: String, e: Exception? = null) {
        try {
            if (e != null) {
                Log.e(TAG, message, e)
            } else {
                Log.e(TAG, message)
            }
        } catch (_: Exception) {
            println(message)
            e?.printStackTrace()
        }
    }

    /**
     * 提取 JS 变量值
     */
    private fun extractVariable(html: String, name: String): String? {
        val pattern = Pattern.compile("var\\s+$name\\s*=\\s*['\"]([^'\"]*)['\"];")
        val matcher = pattern.matcher(html)
        return if (matcher.find()) matcher.group(1) else null
    }


    /**
     * AES/CBC/PKCS7 解密逻辑
     * @param cct 密钥
     * @param contentKey 包含偏移量(前16位)和加密数据(剩余部分)
     */
    private fun decrypt(cct: String, contentKey: String): List<String> {
        if (contentKey.length < 16) return emptyList()

        val ivString = contentKey.substring(0, 16)
        val encryptedHex = contentKey.substring(16)

        val keyBytes = cct.toByteArray(Charsets.UTF_8)
        val ivBytes = ivString.toByteArray(Charsets.UTF_8)
        val encryptedBytes = hexToBytes(encryptedHex)

        val secretKey = SecretKeySpec(keyBytes, "AES")
        val ivSpec = IvParameterSpec(ivBytes)

        // 注意：在普通 JVM 环境下可能需要 BouncyCastle 提供 PKCS7Padding，
        // 或者使用 PKCS5Padding（对于 AES 两者通用）
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec)

        val decryptedBytes = cipher.doFinal(encryptedBytes)
        val jsonString = String(decryptedBytes, Charsets.UTF_8)

        val type = object : TypeToken<List<Map<String, String>>>() {}.type
        val list = gson.fromJson<List<Map<String, String>>>(jsonString, type)
        return list.mapNotNull { it["url"] }
    }

    /**
     * Hex 字符串转字节数组
     */
    private fun hexToBytes(hex: String): ByteArray {
        val len = hex.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] =
                ((Character.digit(hex[i], 16) shl 4) + Character.digit(hex[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }
}