package com.rchdliang.copymanga_neo.data.remote

import com.google.gson.JsonParser
import com.rchdliang.copymanga_neo.data.text.*
import com.rchdliang.copymanga_neo.domain.model.AppRelease
import com.rchdliang.copymanga_neo.domain.repository.*
import javax.inject.Inject
import javax.inject.Singleton

/** 只读取官方稳定发布及同一提交的文件清单，固定仓库和原始文件下载域名。 */
@Singleton
class GitHubMetadata @Inject constructor(private val resources: PublicResourceClient) {
    private suspend fun json(url: String): com.google.gson.JsonObject {
        val bytes = try {
            resources.fetch(url, ChineseDictionaryStore.MAX_METADATA_BYTES)
        } catch (error: PublicResourceHttpException) {
            if (url == ProjectLinks.RELEASES_API && error.status == 404) throw PublicReleaseUnavailableException()
            throw error
        }
        return JsonParser.parseString(bytes.decodeToString()).asJsonObject
    }

    suspend fun latestDictionary(): DictionaryRelease {
        val release = json("${ChineseDictionaryStore.API_SOURCE}/releases/latest")
        require(!release.get("draft").asBoolean && !release.get("prerelease").asBoolean) { "OpenCC 没有稳定发布" }
        val tag = release.get("tag_name").asString
        require(tag.matches(Regex("(ver\\.|v)?[0-9]+(\\.[0-9]+){1,3}"))) { "OpenCC 版本无效" }
        val commit = json("${ChineseDictionaryStore.API_SOURCE}/commits/$tag").get("sha").asString
        require(commit.matches(Regex("[0-9a-f]{40}"))) { "OpenCC 提交无效" }
        val tree = json("${ChineseDictionaryStore.API_SOURCE}/git/trees/$commit?recursive=1")
        require(tree.get("truncated")?.asBoolean != true) { "词典文件清单不完整" }
        val files = tree.getAsJsonArray("tree").mapNotNull { item ->
            val entry = item.asJsonObject
            val path = entry.get("path").asString
            val name = path.removePrefix("data/dictionary/")
            if (path == "data/dictionary/$name" && name in ChineseDictionaryStore.allowedFiles && entry.get(
                    "type"
                ).asString == "blob"
            )
                name to DictionaryFile(entry.get("sha").asString, entry.get("size").asInt)
            else null
        }.toMap()
        return DictionaryRelease(tag.removePrefix("ver.").removePrefix("v"), commit, files)
    }

    suspend fun latestApp(): AppRelease {
        val release = json(ProjectLinks.RELEASES_API)
        require(!release.get("draft").asBoolean && !release.get("prerelease").asBoolean) { "暂无稳定发布" }
        val tag = release.get("tag_name").asString
        require(tag.matches(Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,127}"))) { "发布版本格式无效" }
        return AppRelease(tag, tag.removePrefix("v"), "${ProjectLinks.GITHUB}/releases/tag/$tag")
    }
}
