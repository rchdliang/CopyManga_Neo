package com.rchdliang.copymanga_neo.data.remote.portable.util

import kotlinx.serialization.json.*

object ChapterDecryptor {
    fun decryptChapterImages(html: String): Result<List<String>> = runCatching {
        fun variable(name: String) = Regex("var\\s+$name\\s*=\\s*['\"]([^'\"]*)['\"];" ).find(html)?.groupValues?.get(1)
        val cct = variable("cct")
        val value = variable("contentKey")
        require(cct != null && value != null) { "解析章节内容失败：缺少 cct 或 contentKey" }
        require(value.length > 16) { "contentKey 为空或密文无效" }
        val plain = ProtocolCrypto.aesCbcPkcs7Decrypt(cct.encodeToByteArray(), value.take(16).encodeToByteArray(), ProtocolCrypto.unhex(value.drop(16)))
        val images = Json.parseToJsonElement(plain.decodeToString(throwOnInvalidSequence = true)).jsonArray.mapNotNull { it.jsonObject["url"]?.jsonPrimitive?.contentOrNull }
        require(images.isNotEmpty()) { "解析章节内容失败：图片列表为空" }
        images
    }
}
