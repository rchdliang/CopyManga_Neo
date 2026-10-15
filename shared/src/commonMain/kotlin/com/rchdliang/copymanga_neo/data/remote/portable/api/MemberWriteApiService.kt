package com.rchdliang.copymanga_neo.data.remote.portable.api

import com.rchdliang.copymanga_neo.data.remote.portable.*
import com.rchdliang.copymanga_neo.data.remote.portable.dto.*
import com.rchdliang.copymanga_neo.domain.model.*
import io.ktor.http.encodeURLPathPart
import kotlinx.serialization.serializer
import kotlinx.serialization.json.JsonObject
import kotlin.time.Clock

/** Portable implementation of the pinned upstream endpoint contract. */
class MemberWriteApiService(private val client: CopyMangaClient, private val www: Boolean = false) {
    suspend fun novelQuery(
        path: String,
        authorization: String,
        headers: Map<String, String> = CopyMangaNetworkConfig.noAuthApiHeaders(),
        requestMode: com.rchdliang.copymanga_neo.domain.model.RequestMode? = null
    ): PortableResponse<ComicQueryResponse> = client.call(ApiRequest(path = "/api/v3/book/${path.encodeURLPathPart()}/query", method = "GET", query = emptyList(), form = null, headers = headers + mapOf("Authorization" to authorization), www = www, mode = requestMode, appIdentity = null, invalidateCurrentSessionOn401 = true), serializer<ComicQueryResponse>())

    suspend fun addNovelFavorite(
        bookId: String,
        authorization: String,
        isCollect: Int = 1,
        headers: Map<String, String> = CopyMangaNetworkConfig.noAuthApiHeaders(),
        requestMode: com.rchdliang.copymanga_neo.domain.model.RequestMode? = null
    ): PortableResponse<SimpleResponse> = client.call(ApiRequest(path = "/api/v3/member/collect/book", method = "POST", query = emptyList(), form = listOfNotNull("book_id" to bookId.toString(), "is_collect" to isCollect.toString()), headers = headers + mapOf("Authorization" to authorization), www = www, mode = requestMode, appIdentity = null, invalidateCurrentSessionOn401 = true), serializer<SimpleResponse>())

    suspend fun novelFavorites(
        authorization: String,
        offset: Int = 0,
        limit: Int = 23,
        freeType: Int = 1,
        ordering: String = "-datetime_modifier",
        update: Boolean? = null,
        headers: Map<String, String> = CopyMangaNetworkConfig.noAuthApiHeaders(),
        requestMode: com.rchdliang.copymanga_neo.domain.model.RequestMode? = null
    ): PortableResponse<JsonObject> = client.call(ApiRequest(path = "/api/v3/member/collect/books", method = "GET", query = listOfNotNull("offset" to offset.toString(), "limit" to limit.toString(), "free_type" to freeType.toString(), "ordering" to ordering.toString(), update?.let { "_update" to it.toString() }), form = null, headers = headers + mapOf("Authorization" to authorization), www = www, mode = requestMode, appIdentity = null, invalidateCurrentSessionOn401 = true), serializer<JsonObject>())

    suspend fun removeNovelFavoritesWeb(
        collectIds: Map<String, Int>,
        authorization: String,
        headers: Map<String, String> = CopyMangaNetworkConfig.noAuthApiHeaders(),
        requestMode: com.rchdliang.copymanga_neo.domain.model.RequestMode? = null
    ): PortableResponse<JsonObject> = client.call(ApiRequest(path = "/api/v3/member/collect/books", method = "DELETE", query = emptyList(), form = collectIds.map { it.key to it.value.toString() }, headers = headers + mapOf("Authorization" to authorization), www = www, mode = requestMode, appIdentity = null, invalidateCurrentSessionOn401 = true), serializer<JsonObject>())

    suspend fun removeNovelFavoritesApp(
        collectIds: List<Int>,
        authorization: String,
        headers: Map<String, String> = CopyMangaNetworkConfig.noAuthApiHeaders(),
        requestMode: com.rchdliang.copymanga_neo.domain.model.RequestMode? = null
    ): PortableResponse<JsonObject> = client.call(ApiRequest(path = "/api/v3/member/collect/books", method = "DELETE", query = emptyList(), form = collectIds.map { "ids" to it.toString() }, headers = headers + mapOf("Authorization" to authorization), www = www, mode = requestMode, appIdentity = null, invalidateCurrentSessionOn401 = true), serializer<JsonObject>())

    suspend fun addFavorite(
        comicId: String,
        authorization: String,
        isCollect: Int = 1,
        headers: Map<String, String> = CopyMangaNetworkConfig.noAuthApiHeaders()
    ): PortableResponse<SimpleResponse> = client.call(ApiRequest(path = "/api/v3/member/collect/comic", method = "POST", query = emptyList(), form = listOfNotNull("comic_id" to comicId.toString(), "is_collect" to isCollect.toString()), headers = headers + mapOf("Authorization" to authorization), www = www, mode = null, appIdentity = null, invalidateCurrentSessionOn401 = true), serializer<SimpleResponse>())

    suspend fun removeFavorites(
        collectIds: List<Int>,
        authorization: String,
        headers: Map<String, String> = CopyMangaNetworkConfig.noAuthApiHeaders()
    ): PortableResponse<SimpleResponse> = client.call(ApiRequest(path = "/api/v3/member/collect/comics", method = "DELETE", query = emptyList(), form = collectIds.map { "ids" to it.toString() }, headers = headers + mapOf("Authorization" to authorization), www = www, mode = null, appIdentity = null, invalidateCurrentSessionOn401 = true), serializer<SimpleResponse>())

    suspend fun clearBrowseHistory(
        authorization: String,
        freeType: Int = 1,
        headers: Map<String, String> = CopyMangaNetworkConfig.noAuthApiHeaders()
    ): PortableResponse<SimpleResponse> = client.call(ApiRequest(path = "/api/v3/member/browse/comics", method = "DELETE", query = emptyList(), form = listOfNotNull("free_type" to freeType.toString()), headers = headers + mapOf("Authorization" to authorization), www = www, mode = null, appIdentity = null, invalidateCurrentSessionOn401 = true), serializer<SimpleResponse>())

    suspend fun logout(
        authorization: String,
        headers: Map<String, String> = CopyMangaNetworkConfig.noAuthApiHeaders(),
        requestMode: com.rchdliang.copymanga_neo.domain.model.RequestMode? = null
    ): PortableResponse<SimpleResponse> = client.call(ApiRequest(path = "/api/v3/logout", method = "POST", query = emptyList(), form = null, headers = headers + mapOf("Authorization" to authorization), www = www, mode = requestMode, appIdentity = null, invalidateCurrentSessionOn401 = true), serializer<SimpleResponse>())
}
