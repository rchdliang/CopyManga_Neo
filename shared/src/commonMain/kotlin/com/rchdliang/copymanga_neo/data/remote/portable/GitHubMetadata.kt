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
        // 这些旧标签在 CMP 接管 v 前已用于 Android 发布，不能当作 CMP 更新。
        val legacyAndroidTags = setOf("v0.5.21", "v0.6.3", "v0.6.7.2", "v0.6.29", "v0.9.14", "v0.9.24", "v0.9.30", "v0.10.3", "v0.10.19", "v0.10.20", "v0.10.25")
        for (page in 1..10) {
            val response = client.resourceResponse("${ProjectLinks.RELEASES_API}?per_page=20&page=$page", ChineseDictionaryStore.MAX_METADATA_BYTES)
            if (response.status == 404) throw PublicReleaseUnavailableException()
            require(response.status in 200..299) { "资源请求失败：HTTP ${response.status}" }
            val releases = Json.parseToJsonElement(response.bytes.decodeToString()).jsonArray
            for (item in releases) {
                val release = item.jsonObject
                val tag = release.getValue("tag_name").jsonPrimitive.content
                if (!release.getValue("draft").jsonPrimitive.boolean && !release.getValue("prerelease").jsonPrimitive.boolean &&
                    tag !in legacyAndroidTags && tag.matches(Regex("v[0-9]+(\\.[0-9]+){1,3}"))) {
                    return AppRelease(tag, tag.removePrefix("v"), "${ProjectLinks.GITHUB}/releases/tag/$tag",
                        release["body"]?.jsonPrimitive?.contentOrNull.orEmpty())
                }
            }
            if (releases.size < 20) break
        }
        throw PublicReleaseUnavailableException()
    }
}
