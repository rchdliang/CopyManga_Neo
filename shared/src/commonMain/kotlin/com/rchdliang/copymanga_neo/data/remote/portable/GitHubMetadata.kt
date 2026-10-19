package com.rchdliang.copymanga_neo.data.remote.portable

import com.rchdliang.copymanga_neo.data.text.*
import com.rchdliang.copymanga_neo.domain.model.AppRelease
import com.rchdliang.copymanga_neo.domain.repository.*
import kotlinx.serialization.json.*

/** 公开元数据经现有无凭据资源通道获取，不与 Web/App 会话绑定。 */
class GitHubMetadata(private val client: CopyMangaClient) {
    private suspend fun json(url: String): JsonObject {
        val response = client.resourceResponse(url, ChineseDictionaryStore.MAX_METADATA_BYTES)
        if (url == ProjectLinks.RELEASES_API && response.status == 404) throw PublicReleaseUnavailableException()
        require(response.status in 200..299) { "资源请求失败：HTTP ${response.status}" }
        return Json.parseToJsonElement(response.bytes.decodeToString()).jsonObject
    }

    suspend fun latestDictionary(): DictionaryRelease {
        val release = json("${ChineseDictionaryStore.API_SOURCE}/releases/latest")
        require(!release.getValue("draft").jsonPrimitive.boolean && !release.getValue("prerelease").jsonPrimitive.boolean) { "OpenCC 没有稳定发布" }
        val tag = release.getValue("tag_name").jsonPrimitive.content
        require(tag.matches(Regex("(ver\\.|v)?[0-9]+(\\.[0-9]+){1,3}"))) { "OpenCC 版本无效" }
        val commit =
            json("${ChineseDictionaryStore.API_SOURCE}/commits/$tag").getValue("sha").jsonPrimitive.content
        require(commit.matches(Regex("[0-9a-f]{40}"))) { "OpenCC 提交无效" }
        val tree = json("${ChineseDictionaryStore.API_SOURCE}/git/trees/$commit?recursive=1")
        require(tree["truncated"]?.jsonPrimitive?.booleanOrNull != true) { "词典文件清单不完整" }
        val files = tree.getValue("tree").jsonArray.mapNotNull { item ->
            val entry = item.jsonObject
            val path = entry.getValue("path").jsonPrimitive.content
            val name = path.removePrefix("data/dictionary/")
            if (path == "data/dictionary/$name" && name in ChineseDictionaryStore.allowedFiles && entry.getValue(
                    "type"
                ).jsonPrimitive.content == "blob"
            )
                name to DictionaryFile(
                    entry.getValue("sha").jsonPrimitive.content,
                    entry.getValue("size").jsonPrimitive.int
                )
            else null
        }.toMap()
        return DictionaryRelease(tag.removePrefix("ver.").removePrefix("v"), commit, files)
    }

    suspend fun latestApp(): AppRelease {
        val release = json(ProjectLinks.RELEASES_API)
        require(!release.getValue("draft").jsonPrimitive.boolean && !release.getValue("prerelease").jsonPrimitive.boolean) { "暂无稳定发布" }
        val tag = release.getValue("tag_name").jsonPrimitive.content
        require(tag.matches(Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,127}"))) { "发布版本格式无效" }
        return AppRelease(tag, tag.removePrefix("v"), "${ProjectLinks.GITHUB}/releases/tag/$tag")
    }
}
