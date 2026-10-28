package com.rchdliang.copymanga_neo.data.remote

import com.google.gson.JsonArray
import com.rchdliang.copymanga_neo.domain.model.AppRelease
import com.rchdliang.copymanga_neo.domain.model.AppReleaseChannel
import com.rchdliang.copymanga_neo.domain.repository.ProjectLinks
import com.rchdliang.copymanga_neo.domain.repository.PublicReleaseUnavailableException

/** 优先推荐 CMP 正式版；不存在时继续提供旧版 Android 的更新。 */
internal suspend fun selectAppRelease(fetchPage: suspend (Int) -> JsonArray): AppRelease {
    // CMP 接管 v 标签前的 Android 发布，不能误报为跨应用迁移。
    val legacyAndroidTags = setOf("v0.5.21", "v0.6.3", "v0.6.7.2", "v0.6.29", "v0.9.14", "v0.9.24", "v0.9.30", "v0.10.3", "v0.10.19", "v0.10.20", "v0.10.25")
    var android: AppRelease? = null
    for (page in 1..10) {
        val releases = fetchPage(page)
        for (item in releases) {
            val release = item.asJsonObject
            if (release.get("draft").asBoolean || release.get("prerelease").asBoolean) continue
            val tag = release.get("tag_name").asString
            val channel = when {
                tag.matches(Regex("android-v[0-9]+(\\.[0-9]+){1,3}")) -> AppReleaseChannel.ANDROID
                tag !in legacyAndroidTags && tag.matches(Regex("v[0-9]+(\\.[0-9]+){1,3}")) -> AppReleaseChannel.CMP
                else -> continue
            }
            val parsed = AppRelease(tag, tag.removePrefix("android-").removePrefix("v"),
                "${ProjectLinks.GITHUB}/releases/tag/$tag",
                release.get("body")?.takeUnless { it.isJsonNull }?.asString.orEmpty(), channel)
            if (channel == AppReleaseChannel.CMP) return parsed
            if (android == null) android = parsed
        }
        if (releases.size() < 20) break
    }
    return android ?: throw PublicReleaseUnavailableException()
}
