package com.rchdliang.copymanga_neo.data.remote.portable.util

import kotlinx.serialization.json.*
import kotlinx.serialization.decodeFromString
import com.rchdliang.copymanga_neo.data.remote.portable.dto.ChapterCatalogDto
import com.rchdliang.copymanga_neo.domain.model.ChapterCatalog
import com.rchdliang.copymanga_neo.domain.model.ChapterGroup
import com.rchdliang.copymanga_neo.domain.model.ChapterInfo
import com.rchdliang.copymanga_neo.domain.repository.RiskControlException

/** WWW 目录：16 字符 IV + Hex 密文，网页提供 128 位密钥，CBC / PKCS7。 */
internal object ChapterCatalogDecryptor {
    private val jsonParser = Json { ignoreUnknownKeys = true; explicitNulls = false }

    /** 兼容加密 results 和风控时返回的明文目录；HTTP 成功不等于目录可用。 */
    fun parseResponse(json: String, pathWord: String, key: String): ChapterCatalog {
        val root = jsonParser.parseToJsonElement(json).jsonObject
        if ("build" in root && "groups" in root) return parseCatalog(root.toString(), pathWord)
        if (root.get("code")?.jsonPrimitive?.int == 210) {
            throw RiskControlException(
                root.get("message")?.takeUnless { it == JsonNull }?.jsonPrimitive?.content
                    ?: "官方暂时限制了当前网络的访问"
            )
        }
        check(root.get("code")?.jsonPrimitive?.int == 200) {
            root.get("message")?.takeUnless { it == JsonNull }?.jsonPrimitive?.content ?: "WWW 章节目录响应异常"
        }
        val results = requireNotNull(
            root.get("results")?.takeUnless { it == JsonNull }) { "WWW 章节目录缺少内容" }
        return if (results is JsonObject) parseCatalog(results.toString(), pathWord)
        else {
            val value = results.jsonPrimitive.content
            if (value.trimStart().startsWith("{")) parseCatalog(value, pathWord) else decrypt(
                value,
                pathWord,
                key
            )
        }
    }

    fun decrypt(results: String, pathWord: String, key: String): ChapterCatalog {
        require(key.encodeToByteArray().size == 16) { "章节目录密钥长度无效" }
        require(results.length > 16) { "章节目录密文为空或缺少 IV" }
        val iv = results.take(16).encodeToByteArray()
        val hex = results.drop(16)
        require(iv.size == 16 && hex.length % 32 == 0) { "章节目录密文长度无效" }
        require(hex.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) {
            "章节目录密文不是 Hex"
        }
        val ciphertext = ByteArray(hex.length / 2) { index ->
            hex.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
        val json = ProtocolCrypto.aesCbcPkcs7Decrypt(key.encodeToByteArray(), iv, ciphertext).decodeToString(throwOnInvalidSequence = true)
        return parseCatalog(json, pathWord)
    }

    private fun parseCatalog(json: String, pathWord: String): ChapterCatalog {
        // Gson 在解析 JSON 时处理 Unicode 转义，不能再手动二次反转义。
        val dto = jsonParser.decodeFromString<ChapterCatalogDto>(json)
        require(dto.build?.pathWord == pathWord) { "章节目录与当前漫画不匹配" }
        val sourceGroups = requireNotNull(dto.groups) { "章节目录缺少分组" }
        if (sourceGroups.isNotEmpty() && sourceGroups.values.all { it.count == 0 && it.chapters?.isEmpty() == true }) {
            throw RiskControlException("WWW 章节目录返回所有分组均为 0 章，疑似受到访问限制")
        }
        val groups = mutableListOf<ChapterGroup>()
        val chaptersByGroup = linkedMapOf<String, List<ChapterInfo>>()
        sourceGroups.forEach { (key, group) ->
            val groupPath = group.pathWord?.takeIf { it.isNotBlank() } ?: key
            require(groupPath.isNotBlank() && groupPath !in chaptersByGroup) { "章节分组标识无效" }
            val entries = requireNotNull(group.chapters) { "章节分组缺少目录" }
            require(group.count == entries.size) { "章节目录不完整，改用分页接口" }
            val ids = entries.map { requireNotNull(it.id?.takeIf(String::isNotBlank)) }
            require(ids.distinct().size == ids.size) { "章节目录包含重复 ID" }
            val last = group.lastChapter
            if (last != null) {
                require(
                    last.comicPathWord == pathWord && last.groupPathWord == groupPath &&
                            last.uuid == ids.lastOrNull()
                ) { "末章信息与章节目录不匹配" }
            }
            groups += ChapterGroup(groupPath, entries.size, group.name ?: groupPath)
            // 仅在完整的远端分组目录内建立上下章关系，不从本地下载子集推断。
            chaptersByGroup[groupPath] = entries.mapIndexed { index, entry ->
                val metadata = last?.takeIf { it.uuid == ids[index] }
                ChapterInfo(
                    index = index, uuid = ids[index], count = entries.size, ordered = index,
                    size = metadata?.size,
                    name = requireNotNull(entry.name?.takeIf(String::isNotBlank)),
                    comicId = last?.comicId.orEmpty(), comicPathWord = pathWord,
                    groupId = last?.groupId, groupPathWord = groupPath,
                    type = requireNotNull(entry.type), news = metadata?.news,
                    datetimeCreated = metadata?.datetimeCreated,
                    prevChapter = ids.getOrNull(index - 1), nextChapter = ids.getOrNull(index + 1)
                )
            }
        }
        return ChapterCatalog(groups, chaptersByGroup)
    }
}