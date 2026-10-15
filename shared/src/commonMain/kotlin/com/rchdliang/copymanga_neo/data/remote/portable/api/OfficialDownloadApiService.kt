package com.rchdliang.copymanga_neo.data.remote.portable.api

import com.rchdliang.copymanga_neo.data.remote.portable.*
import com.rchdliang.copymanga_neo.data.remote.portable.dto.*
import com.rchdliang.copymanga_neo.domain.model.*
import io.ktor.http.encodeURLPathPart
import kotlinx.serialization.serializer
import kotlinx.serialization.json.JsonObject
import kotlin.time.Clock

/** Portable implementation of the pinned upstream endpoint contract. */
class OfficialDownloadApiService(private val client: CopyMangaClient, private val www: Boolean = false) {
    suspend fun chapter(
        pathWord: String,
        chapterUuid: String,
        authorization: String,
        headers: Map<String, String> = CopyMangaNetworkConfig.noAuthApiHeaders()
    ): PortableResponse<OfficialChapterResponse> = client.call(ApiRequest(path = "/api/v3/comic/${pathWord.encodeURLPathPart()}/chapter2download/${chapterUuid.encodeURLPathPart()}", method = "GET", query = emptyList(), form = null, headers = headers + mapOf("Authorization" to authorization), www = www, mode = null, appIdentity = null, invalidateCurrentSessionOn401 = true), serializer<OfficialChapterResponse>())
}
