package com.rchdliang.copymanga_neo.data.remote.util

import com.google.gson.Gson
import com.rchdliang.copymanga_neo.data.remote.dto.ChapterCatalogDto
import com.rchdliang.copymanga_neo.domain.model.ChapterCatalog
import com.rchdliang.copymanga_neo.domain.model.ChapterGroup
import com.rchdliang.copymanga_neo.domain.model.ChapterInfo
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/** WWW 目录：16 字符 IV + Hex 密文，固定 128 位密钥，CBC / PKCS7。 */
internal object ChapterCatalogDecryptor {
    private val gson = Gson()

    fun decrypt(results: String, pathWord: String): ChapterCatalog {
        require(results.length > 16) { "章节目录密文为空或缺少 IV" }
        val iv = results.take(16).toByteArray(Charsets.UTF_8)
        val hex = results.drop(16)
        require(iv.size == 16 && hex.length % 32 == 0) { "章节目录密文长度无效" }
        require(hex.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) {
            "章节目录密文不是 Hex"
        }
        val ciphertext = ByteArray(hex.length / 2) { index ->
            hex.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
        // Java 的 AES/PKCS5Padding 对 16 字节块使用与 PKCS7 相同的填充规则。
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec("op0zzpvv.nmn.00p".toByteArray(Charsets.UTF_8), "AES"),
            IvParameterSpec(iv)
        )
        val json = cipher.doFinal(ciphertext).toString(Charsets.UTF_8)
        // Gson 在解析 JSON 时处理 Unicode 转义，不能再手动二次反转义。
        val dto = requireNotNull(gson.fromJson(json, ChapterCatalogDto::class.java))
        require(dto.build?.pathWord == pathWord) { "章节目录与当前漫画不匹配" }
        val sourceGroups = requireNotNull(dto.groups) { "章节目录缺少分组" }
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
