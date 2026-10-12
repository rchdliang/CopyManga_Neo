package com.rchdliang.copymanga_neo.domain.reading

import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.Locale

/** 正文和章节身份使用的纯内容摘要，不读取或写入文件。 */
object ReadingContentHash {
    fun sha256(bytes: ByteArray): String = try {
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(Locale.ROOT, it.toInt() and 255) }
    } catch (error: NoSuchAlgorithmException) {
        throw AssertionError(error)
    }
}
