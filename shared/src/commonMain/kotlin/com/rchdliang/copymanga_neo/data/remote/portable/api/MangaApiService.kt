package com.rchdliang.copymanga_neo.data.remote.portable.api

import com.rchdliang.copymanga_neo.data.remote.portable.*
import com.rchdliang.copymanga_neo.data.remote.portable.dto.*
import com.rchdliang.copymanga_neo.domain.model.*
import io.ktor.http.encodeURLPathPart
import kotlinx.serialization.serializer
import kotlinx.serialization.json.JsonObject
import kotlin.time.Clock

/** Portable implementation of the pinned upstream endpoint contract. */
class MangaApiService(private val client: CopyMangaClient, private val www: Boolean = false) {
    suspend fun getComicRankings(
        type: Int = 1,
        period: String,
        audience: String,
        offset: Int = 0,
        limit: Int = 21,
        update: Boolean = true,
        headers: Map<String, String> = CopyMangaNetworkConfig.apiHeaders()
    ): PortableResponse<RankingResponse> = client.call(ApiRequest(path = "/api/v3/ranks", method = "GET", query = listOfNotNull("type" to type.toString(), "date_type" to period, "audience_type" to audience, "offset" to offset.toString(), "limit" to limit.toString(), "_update" to update.toString()), form = null, headers = headers, www = www, mode = null, appIdentity = null), serializer<RankingResponse>())

    suspend fun getNewestComics(
        limit: Int = 21,
        offset: Int = 0,
        update: Boolean? = null,
        date: String? = null,
        headers: Map<String, String> = CopyMangaNetworkConfig.apiHeaders()
    ): PortableResponse<NewestResponse> = client.call(ApiRequest(path = "/api/v3/update/newest", method = "GET", query = listOfNotNull("limit" to limit.toString(), "offset" to offset.toString(), update?.let { "_update" to it.toString() }, date?.let { "date" to it }), form = null, headers = headers, www = www, mode = null, appIdentity = null), serializer<NewestResponse>())

    suspend fun getComicTopics(
        type: Int = 1,
        limit: Int = 20,
        offset: Int = 0,
        update: Boolean = true,
        headers: Map<String, String> = CopyMangaNetworkConfig.apiHeaders()
    ): PortableResponse<TopicsResponse> = client.call(ApiRequest(path = "/api/v3/topics", method = "GET", query = listOfNotNull("type" to type.toString(), "limit" to limit.toString(), "offset" to offset.toString(), "_update" to update.toString()), form = null, headers = headers, www = www, mode = null, appIdentity = null), serializer<TopicsResponse>())

    suspend fun getComicTopic(
        pathWord: String,
        limit: String? = "",
        offset: String? = "",
        headers: Map<String, String> = CopyMangaNetworkConfig.apiHeaders()
    ): PortableResponse<TopicDetailResponse> = client.call(ApiRequest(path = "/api/v3/topic/${pathWord.encodeURLPathPart()}", method = "GET", query = listOfNotNull(limit?.let { "limit" to it }, offset?.let { "offset" to it }), form = null, headers = headers, www = www, mode = null, appIdentity = null), serializer<TopicDetailResponse>())

    suspend fun getComicTopicContents(
        pathWord: String,
        limit: Int = 20,
        offset: Int = 0,
        type: Int? = null,
        headers: Map<String, String> = CopyMangaNetworkConfig.apiHeaders()
    ): PortableResponse<SearchResponse> = client.call(ApiRequest(path = "/api/v3/topic/${pathWord.encodeURLPathPart()}/contents", method = "GET", query = listOfNotNull("limit" to limit.toString(), "offset" to offset.toString(), type?.let { "type" to it.toString() }), form = null, headers = headers, www = www, mode = null, appIdentity = null), serializer<SearchResponse>())

    suspend fun getComicThemeCounts(
        freeType: Int = 1,
        limit: Int = 500,
        offset: Int = 0,
        update: Boolean = true,
        headers: Map<String, String> = CopyMangaNetworkConfig.apiHeaders()
    ): PortableResponse<ComicThemeCountsResponse> = client.call(ApiRequest(path = "/api/v3/theme/comic/count", method = "GET", query = listOfNotNull("free_type" to freeType.toString(), "limit" to limit.toString(), "offset" to offset.toString(), "_update" to update.toString()), form = null, headers = headers, www = www, mode = null, appIdentity = null), serializer<ComicThemeCountsResponse>())

    suspend fun getComicFilterTags(
        type: Int = 1,
        headers: Map<String, String> = CopyMangaNetworkConfig.apiHeaders()
    ): PortableResponse<ComicFilterTagsResponse> = client.call(ApiRequest(path = "/api/v3/h5/filter/comic/tags", method = "GET", query = listOfNotNull("type" to type.toString()), form = null, headers = headers, www = www, mode = null, appIdentity = null), serializer<ComicFilterTagsResponse>())

    suspend fun getOriginalWorkComics(
        pathWord: String,
        ordering: String,
        offset: Int
    ): PortableResponse<SearchResponse> = client.call(
        ApiRequest(path = "/api/v3/comics", query = listOf(
            "parody" to pathWord, "ordering" to ordering, "offset" to offset.toString(),
            "limit" to "21", "free_type" to "1"), www = www),
        serializer<SearchResponse>()
    )

    suspend fun browseComics(
        freeType: Int = 1,
        top: String?,
        theme: String?,
        ordering: String,
        limit: Int = 21,
        offset: Int = 0,
        update: Boolean = true,
        headers: Map<String, String> = CopyMangaNetworkConfig.apiHeaders()
    ): PortableResponse<SearchResponse> = client.call(ApiRequest(path = "/api/v3/comics", method = "GET", query = listOfNotNull("free_type" to freeType.toString(), top?.let { "top" to it }, theme?.let { "theme" to it }, "ordering" to ordering, "limit" to limit.toString(), "offset" to offset.toString(), "_update" to update.toString()), form = null, headers = headers, www = www, mode = null, appIdentity = null), serializer<SearchResponse>())

    suspend fun getFinishedComics(
        freeType: Int = 1,
        top: String = "finish",
        offset: Int = 0,
        limit: Int = 21,
        ordering: String = "-datetime_updated",
        headers: Map<String, String> = CopyMangaNetworkConfig.apiHeaders()
    ): PortableResponse<SearchResponse> = client.call(ApiRequest(path = "/api/v3/comics", method = "GET", query = listOfNotNull("free_type" to freeType.toString(), "top" to top, "offset" to offset.toString(), "limit" to limit.toString(), "ordering" to ordering), form = null, headers = headers, www = www, mode = null, appIdentity = null), serializer<SearchResponse>())

    /** App 游客请求可返回完整章节；内容加载与预载不得携带账号，阅读上报使用 getTrialContent。 */
    suspend fun getOfficialChapterContent(
        pathWord: String,
        chapterUuid: String
    ): PortableResponse<OfficialChapterResponse> = client.call(ApiRequest(path = "/api/v3/comic/${pathWord.encodeURLPathPart()}/chapter2/${chapterUuid.encodeURLPathPart()}", method = "GET", query = emptyList(), form = null, headers = CopyMangaNetworkConfig.noAuthApiHeaders() + ("Authorization" to "Token"), www = www, mode = null, appIdentity = null), serializer<OfficialChapterResponse>())

    suspend fun getHomeComics(
        timestamp: Long = Clock.System.now().toEpochMilliseconds(),
        headers: Map<String, String> = CopyMangaNetworkConfig.apiHeaders()
    ): PortableResponse<HomeIndexResponse> = client.call(ApiRequest(path = "/api/v3/h5/homeIndex", method = "GET", query = listOfNotNull("_t" to timestamp.toString()), form = null, headers = headers, www = www, mode = null, appIdentity = null), serializer<HomeIndexResponse>())

    suspend fun getRecComics(
        headers: Map<String, String> = CopyMangaNetworkConfig.apiHeaders(),
        limit: Int = 10,
        offset: Int = 0,
        freeType: Int?
    ): PortableResponse<ComicListResponse> = client.call(ApiRequest(path = "/api/v3/recs", method = "GET", query = listOfNotNull("pos" to "3200102", "limit" to limit.toString(), "offset" to offset.toString(), freeType?.let { "free_type" to it.toString() }), form = null, headers = headers, www = www, mode = null, appIdentity = null), serializer<ComicListResponse>())

    suspend fun getComicDetail(
        pathWord: String,
        headers: Map<String, String> = CopyMangaNetworkConfig.apiHeaders(),
        platform: Int = CopyMangaNetworkConfig.WEB_PLATFORM,
        update: Boolean = true
    ): PortableResponse<ComicDetailResponse> = client.call(ApiRequest(path = "/api/v3/comic2/${pathWord.encodeURLPathPart()}", method = "GET", query = listOfNotNull("platform" to platform.toString(), "_update" to update.toString()), form = null, headers = headers, www = www, mode = null, appIdentity = null), serializer<ComicDetailResponse>())

    suspend fun getChapters(
        pathWord: String,
        group: String,
        limit: Int = 100,
        offset: Int = 0,
        headers: Map<String, String> = CopyMangaNetworkConfig.apiHeaders()
    ): PortableResponse<ChapterListResponse> = client.call(ApiRequest(path = "/api/v3/comic/${pathWord.encodeURLPathPart()}/group/${group.encodeURLPathPart()}/chapters", method = "GET", query = listOfNotNull("limit" to limit.toString(), "offset" to offset.toString()), form = null, headers = headers, www = www, mode = null, appIdentity = null), serializer<ChapterListResponse>())

    suspend fun getChapterCatalog(
        pathWord: String,
        headers: Map<String, String>,
        protocol: com.rchdliang.copymanga_neo.data.remote.portable.util.WwwCatalogProtocol
    ): PortableResponse<RawResponseBody> = client.raw(ApiRequest(path = "/comicdetail/${pathWord.encodeURLPathPart()}/chapters", method = "GET", query = emptyList(), form = null, headers = headers, www = www, mode = null, appIdentity = null))

    suspend fun getComicHtml(
        pathWord: String,
        value: Int = 1
    ): PortableResponse<RawResponseBody> = client.raw(ApiRequest(path = "/comic/${pathWord.encodeURLPathPart()}", method = "GET", query = emptyList(), form = null, headers = mapOf("X-No-Auth" to value.toString()), www = www, mode = null, appIdentity = null))

    suspend fun getThemeComics(
        theme: String,
        offset: Int = 0,
        limit: Int = 21,
        freeType: Int = 1,
        ordering: OrderType = OrderType.UPDATED_DESC,
        headers: Map<String, String> = CopyMangaNetworkConfig.apiHeaders()
    ): PortableResponse<SearchResponse> = client.call(ApiRequest(path = "/api/v3/comics", method = "GET", query = listOfNotNull("theme" to theme, "offset" to offset.toString(), "limit" to limit.toString(), "free_type" to freeType.toString(), "ordering" to ordering.toString()), form = null, headers = headers, www = www, mode = null, appIdentity = null), serializer<SearchResponse>())

    suspend fun search(
        platform: Int = CopyMangaNetworkConfig.WEB_PLATFORM,
        query: String,
        limit: Int = 20,
        offset: Int = 0,
        queryType: QueryType?,
        update: Boolean = true,
        headers: Map<String, String> = CopyMangaNetworkConfig.apiHeaders()
    ): PortableResponse<SearchResponse> = client.call(ApiRequest(path = "/api/v3/search/comic", method = "GET", query = listOfNotNull("platform" to platform.toString(), "q" to query, "limit" to limit.toString(), "offset" to offset.toString(), queryType?.let { "q_type" to it.toString() }, "_update" to update.toString()), form = null, headers = headers, www = www, mode = null, appIdentity = null), serializer<SearchResponse>())

    suspend fun getFavoriteComics(
        freeType: Int = 1,
        limit: Int = 20,
        offset: Int = 0,
        update: Boolean = true,
        order: OrderType = OrderType.MODIFIER_DESC,
        headers: Map<String, String> = CopyMangaNetworkConfig.apiHeaders()
    ): PortableResponse<FavoriteResponse> = client.call(ApiRequest(path = "/api/v3/member/collect/comics", method = "GET", query = listOfNotNull("free_type" to freeType.toString(), "limit" to limit.toString(), "offset" to offset.toString(), "_update" to update.toString(), "ordering" to order.toString()), form = null, headers = headers, www = www, mode = null, appIdentity = null), serializer<FavoriteResponse>())

    suspend fun getBrowseHistory(
        offset: Int = 0,
        limit: Int = 20,
        freeType: Int? = 1,
        update: Boolean = true,
        headers: Map<String, String> = CopyMangaNetworkConfig.apiHeaders()
    ): PortableResponse<BrowseHistoryResponse> = client.call(ApiRequest(path = "/api/v3/member/browse/comics", method = "GET", query = listOfNotNull("offset" to offset.toString(), "limit" to limit.toString(), freeType?.let { "free_type" to it.toString() }, "_update" to update.toString()), form = null, headers = headers, www = www, mode = null, appIdentity = null), serializer<BrowseHistoryResponse>())

    suspend fun clearBrowseHistory(
        freeType: Int = 1,
        update: Boolean = true,
        headers: Map<String, String> = CopyMangaNetworkConfig.apiHeaders()
    ): PortableResponse<SimpleResponse> = client.call(ApiRequest(path = "/api/v3/member/browse/comics", method = "DELETE", query = emptyList(), form = listOfNotNull("free_type" to freeType.toString(), "_update" to update.toString()), headers = headers, www = www, mode = null, appIdentity = null), serializer<SimpleResponse>())

    suspend fun getMemberInfo(
        headers: Map<String, String> = CopyMangaNetworkConfig.apiHeaders()
    ): PortableResponse<MemberInfoResponse> = client.call(ApiRequest(path = "/api/v3/member/info", method = "GET", query = listOfNotNull("_update" to "true"), form = null, headers = headers, www = www, mode = null, appIdentity = null), serializer<MemberInfoResponse>())

    suspend fun getMemberInfoWithCredential(
        authorization: String,
        headers: Map<String, String> = CopyMangaNetworkConfig.noAuthApiHeaders(),
        requestMode: com.rchdliang.copymanga_neo.domain.model.RequestMode? = null,
        appIdentity: com.rchdliang.copymanga_neo.domain.model.AppCredentialIdentity? = null
    ): PortableResponse<MemberInfoResponse> = client.call(ApiRequest(path = "/api/v3/member/info", method = "GET", query = listOfNotNull("_update" to "true"), form = null, headers = headers + mapOf("Authorization" to authorization), www = www, mode = requestMode, appIdentity = appIdentity), serializer<MemberInfoResponse>())

    suspend fun getTrialContent(
        comicPathWord: String,
        chapterUuid: String,
        platform: Int = CopyMangaNetworkConfig.WEB_PLATFORM,
        update: Boolean = true,
        headers: Map<String, String> = CopyMangaNetworkConfig.apiHeaders()
    ): PortableResponse<TrialContentResponse> = client.call(ApiRequest(path = "/api/v3/comic/${comicPathWord.encodeURLPathPart()}/chapter2/${chapterUuid.encodeURLPathPart()}", method = "GET", query = listOfNotNull("platform" to platform.toString(), "_update" to update.toString()), form = null, headers = headers, www = www, mode = null, appIdentity = null), serializer<TrialContentResponse>())

    suspend fun getChapterHtml(
        comicPathWord: String,
        chapterUuid: String,
        value: Int = 1
    ): PortableResponse<RawResponseBody> = client.raw(ApiRequest(path = "/comic/${comicPathWord.encodeURLPathPart()}/chapter/${chapterUuid.encodeURLPathPart()}", method = "GET", query = emptyList(), form = null, headers = mapOf("X-No-Auth" to value.toString()), www = www, mode = null, appIdentity = null))

    suspend fun login(
        username: String,
        password: String,
        salt: String,
        source: String = "freeSite",
        version: String = CopyMangaNetworkConfig.WEB_VERSION,
        platform: Int = CopyMangaNetworkConfig.WEB_PLATFORM,
        headers: Map<String, String> = CopyMangaNetworkConfig.noAuthApiHeaders(),
        requestMode: com.rchdliang.copymanga_neo.domain.model.RequestMode? = null
    ): PortableResponse<LoginResponse> = client.call(ApiRequest(path = "/api/v3/login", method = "POST", query = emptyList(), form = listOfNotNull("username" to username, "password" to password, "salt" to salt, "source" to source, "version" to version, "platform" to platform.toString()), headers = headers, www = www, mode = requestMode, appIdentity = null), serializer<LoginResponse>())

    suspend fun queryComicStatus(
        pathWord: String,
        platform: Int = CopyMangaNetworkConfig.WEB_PLATFORM,
        update: Boolean = true,
        headers: Map<String, String> = CopyMangaNetworkConfig.apiHeaders()
    ): PortableResponse<ComicQueryResponse> = client.call(ApiRequest(path = "/api/v3/comic2/${pathWord.encodeURLPathPart()}/query", method = "GET", query = listOfNotNull("platform" to platform.toString(), "_update" to update.toString()), form = null, headers = headers, www = www, mode = null, appIdentity = null), serializer<ComicQueryResponse>())

    suspend fun addFavorite(
        comicId: String,
        isCollect: Int = 1,
        update: Boolean = true,
        headers: Map<String, String> = CopyMangaNetworkConfig.apiHeaders()
    ): PortableResponse<SimpleResponse> = client.call(ApiRequest(path = "/api/v3/member/collect/comic", method = "POST", query = emptyList(), form = listOfNotNull("comic_id" to comicId, "is_collect" to isCollect.toString(), "_update" to update.toString()), headers = headers, www = www, mode = null, appIdentity = null), serializer<SimpleResponse>())

    suspend fun removeFavorite(
        collectId: Int,
        headers: Map<String, String> = CopyMangaNetworkConfig.apiHeaders()
    ): PortableResponse<SimpleResponse> = client.call(ApiRequest(path = "/api/v3/member/collect/comics", method = "DELETE", query = emptyList(), form = listOfNotNull("ids[0]" to collectId.toString()), headers = headers, www = www, mode = null, appIdentity = null), serializer<SimpleResponse>())

    suspend fun removeFavorites(
        collectIds: Map<String, Int>,
        headers: Map<String, String> = CopyMangaNetworkConfig.apiHeaders()
    ): PortableResponse<SimpleResponse> = client.call(ApiRequest(path = "/api/v3/member/collect/comics", method = "DELETE", query = emptyList(), form = collectIds.map { it.key to it.value.toString() }, headers = headers, www = www, mode = null, appIdentity = null), serializer<SimpleResponse>())
}
enum class OrderType(val value: String) {
    MODIFIER_ASC("datetime_modifier"),
    MODIFIER_DESC("-datetime_modifier"),
    BROWSE_ASC("datetime_browse"),
    BROWSE_DESC("-datetime_browse"),
    UPDATED_ASC("datetime_updated"),
    UPDATED_DESC("-datetime_updated");

    override fun toString(): String = value
}
