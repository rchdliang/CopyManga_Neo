package com.rchdliang.copymanga_neo.data.remote.api

import com.rchdliang.copymanga_neo.data.remote.CopyMangaNetworkConfig
import com.rchdliang.copymanga_neo.data.remote.dto.BrowseHistoryResponse
import com.rchdliang.copymanga_neo.data.remote.dto.ChapterListResponse
import com.rchdliang.copymanga_neo.data.remote.dto.ComicDetailResponse
import com.rchdliang.copymanga_neo.data.remote.dto.ComicFilterTagsResponse
import com.rchdliang.copymanga_neo.data.remote.dto.ComicListResponse
import com.rchdliang.copymanga_neo.data.remote.dto.ComicQueryResponse
import com.rchdliang.copymanga_neo.data.remote.dto.FavoriteResponse
import com.rchdliang.copymanga_neo.data.remote.dto.HomeIndexResponse
import com.rchdliang.copymanga_neo.data.remote.dto.LoginResponse
import com.rchdliang.copymanga_neo.data.remote.dto.MemberInfoResponse
import com.rchdliang.copymanga_neo.data.remote.dto.NewestResponse
import com.rchdliang.copymanga_neo.data.remote.dto.RankingResponse
import com.rchdliang.copymanga_neo.data.remote.dto.SearchResponse
import com.rchdliang.copymanga_neo.data.remote.dto.SimpleResponse
import com.rchdliang.copymanga_neo.data.remote.dto.TopicDetailResponse
import com.rchdliang.copymanga_neo.data.remote.dto.TopicsResponse
import com.rchdliang.copymanga_neo.data.remote.dto.TrialContentResponse
import com.rchdliang.copymanga_neo.domain.model.QueryType
import retrofit2.Response
import retrofit2.http.Field
import retrofit2.http.FieldMap
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.Header
import retrofit2.http.HeaderMap
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * CopyManga API服务接口
 * 基于copymanga网站API结构定义
 *
 * 业务头（Accept / version / region / platform / webp 等）通过 @HeaderMap 管理，
 * 调用方无需传入即可获得默认值，也可按需覆盖。
 *
 * Authorization 由 AuthInterceptor 自动追加（已登录时）；
 * 需要跳过认证的接口在 @HeaderMap 中携带 "X-No-Auth" = "1" 即可。
 */
interface MangaApiService {
    /** 漫画排行榜；只声明 HAR 已观察到的查询字段。 */
    @GET("api/v3/ranks")
    suspend fun getComicRankings(
        @Query("type") type: Int = 1,
        @Query("date_type") period: String,
        @Query("audience_type") audience: String,
        @Query("offset") offset: Int = 0,
        @Query("limit") limit: Int = 21,
        @Query("_update") update: Boolean = true,
        @HeaderMap headers: Map<String, String> = CopyMangaNetworkConfig.apiHeaders()
    ): Response<RankingResponse>

    @GET("api/v3/update/newest")
    suspend fun getNewestComics(
        @Query("limit") limit: Int = 21,
        @Query("offset") offset: Int = 0,
        @Query("_update") update: Boolean? = null,
        @Query("date") date: String? = null,
        @HeaderMap headers: Map<String, String> = CopyMangaNetworkConfig.apiHeaders()
    ): Response<NewestResponse>

    @GET("api/v3/topics")
    suspend fun getComicTopics(
        @Query("type") type: Int = 1,
        @Query("limit") limit: Int = 20,
        @Query("offset") offset: Int = 0,
        @Query("_update") update: Boolean = true,
        @HeaderMap headers: Map<String, String> = CopyMangaNetworkConfig.apiHeaders()
    ): Response<TopicsResponse>

    @GET("api/v3/topic/{pathWord}")
    suspend fun getComicTopic(
        @Path("pathWord") pathWord: String,
        @Query("limit") limit: String? = "",
        @Query("offset") offset: String? = "",
        @HeaderMap headers: Map<String, String> = CopyMangaNetworkConfig.apiHeaders()
    ): Response<TopicDetailResponse>

    @GET("api/v3/topic/{pathWord}/contents")
    suspend fun getComicTopicContents(
        @Path("pathWord") pathWord: String,
        @Query("limit") limit: Int = 20,
        @Query("offset") offset: Int = 0,
        @Query("type") type: Int? = null,
        @HeaderMap headers: Map<String, String> = CopyMangaNetworkConfig.apiHeaders()
    ): Response<SearchResponse>

    /** 全量题材与数量，沿用默认请求头。 */
    @GET("api/v3/theme/comic/count")
    suspend fun getComicThemeCounts(
        @Query("free_type") freeType: Int = 1,
        @Query("limit") limit: Int = 500,
        @Query("offset") offset: Int = 0,
        @Query("_update") update: Boolean = true,
        @HeaderMap headers: Map<String, String> = CopyMangaNetworkConfig.apiHeaders()
    ): Response<com.rchdliang.copymanga_neo.data.remote.dto.ComicThemeCountsResponse>

    @GET("api/v3/h5/filter/comic/tags")
    suspend fun getComicFilterTags(
        @Query("type") type: Int = 1,
        @HeaderMap headers: Map<String, String> = CopyMangaNetworkConfig.apiHeaders()
    ): Response<ComicFilterTagsResponse>

    @GET("api/v3/comics")
    suspend fun getOriginalWorkComics(
        @Query("parody") pathWord: String,
        @Query("ordering") ordering: String,
        @Query("offset") offset: Int,
        @Query("limit") limit: Int = 21,
        @Query("free_type") freeType: Int = 1,
        @HeaderMap headers: Map<String, String> = CopyMangaNetworkConfig.apiHeaders()
    ): Response<SearchResponse>

    @GET("api/v3/comics")
    suspend fun browseComics(
        @Query("free_type") freeType: Int = 1,
        @Query("top") top: String?,
        @Query("theme") theme: String?,
        @Query("ordering") ordering: String,
        @Query("limit") limit: Int = 21,
        @Query("offset") offset: Int = 0,
        @Query("_update") update: Boolean = true,
        @HeaderMap headers: Map<String, String> = CopyMangaNetworkConfig.apiHeaders()
    ): Response<SearchResponse>

    /** 按最近更新时间分页获取完结漫画；参数与完结入口的请求保持一致。 */
    @GET("api/v3/comics")
    suspend fun getFinishedComics(
        @Query("free_type") freeType: Int = 1,
        @Query("top") top: String = "finish",
        @Query("offset") offset: Int = 0,
        @Query("limit") limit: Int = 21,
        @Query("ordering") ordering: String = "-datetime_updated",
        @HeaderMap headers: Map<String, String> = CopyMangaNetworkConfig.apiHeaders()
    ): Response<SearchResponse>

    /** App 阅读路径，不经过 WWW HTML 解密。 */
    @GET("api/v3/comic/{pathWord}/chapter2/{chapterUuid}")
    suspend fun getOfficialChapter(
        @Path("pathWord") pathWord: String,
        @Path("chapterUuid") chapterUuid: String,
        @HeaderMap headers: Map<String, String> = CopyMangaNetworkConfig.apiHeaders(),
        @Header("Authorization") authorization: String? = null
    ): Response<com.rchdliang.copymanga_neo.data.remote.dto.OfficialChapterResponse>

    /**
     * 获取首页推荐漫画列表
     * @param timestamp 时间戳，防止缓存
     */
    @GET("api/v3/h5/homeIndex")
    suspend fun getHomeComics(
        @Query("_t") timestamp: Long = System.currentTimeMillis(),
        @HeaderMap headers: Map<String, String> = CopyMangaNetworkConfig.apiHeaders(),
    ): Response<HomeIndexResponse>

    @GET("api/v3/recs?pos=3200102")
    suspend fun getRecComics(
        @HeaderMap headers: Map<String, String> = CopyMangaNetworkConfig.apiHeaders(),
        @Query("limit") limit: Int = 10,
        @Query("offset") offset: Int = 0,
        @Query("free_type") freeType: Int?,
    ): Response<ComicListResponse>

    /**
     * 获取漫画详情
     * @param pathWord 漫画路径标识
     */
    @GET("api/v3/comic2/{pathWord}")
    suspend fun getComicDetail(
        @Path("pathWord") pathWord: String,
        @HeaderMap headers: Map<String, String> = CopyMangaNetworkConfig.apiHeaders(),
        @Query("platform") platform: Int = CopyMangaNetworkConfig.WEB_PLATFORM,
        @Query("_update") update: Boolean = true,
    ): Response<ComicDetailResponse>

    /**
     * 获取漫画章节列表
     * @param pathWord 漫画路径标识
     */
    @GET("api/v3/comic/{pathWord}/group/{group}/chapters")
    suspend fun getChapters(
        @Path("pathWord") pathWord: String,
        @Path("group") group: String,
        @Query("limit") limit: Int = 100,
        @Query("offset") offset: Int = 0,
        @HeaderMap headers: Map<String, String> = CopyMangaNetworkConfig.apiHeaders()
    ): Response<ChapterListResponse>


    /** WWW 加密完整目录，包含全部汉化组；原有 API 分页接口保留作回退。 */
    @GET("comicdetail/{pathWord}/chapters")
    suspend fun getChapterCatalog(
        @Path("pathWord") pathWord: String,
        @HeaderMap headers: Map<String, String>,
        @retrofit2.http.Tag protocol: com.rchdliang.copymanga_neo.data.remote.util.WwwCatalogProtocol
    ): Response<okhttp3.ResponseBody>

    /** 漫画 HTML 提供本次目录请求的密钥和 dnts；无需 API 登录。 */
    @GET("comic/{pathWord}")
    suspend fun getComicHtml(
        @Path("pathWord") pathWord: String,
        @Header("X-No-Auth") value: Int = 1
    ): Response<okhttp3.ResponseBody>

    /** 根据题材筛选漫画，响应结构与搜索结果一致。 */
    @GET("api/v3/comics")
    suspend fun getThemeComics(
        @Query("theme") theme: String,
        @Query("offset") offset: Int = 0,
        @Query("limit") limit: Int = 21,
        @Query("free_type") freeType: Int = 1,
        @Query("ordering") ordering: OrderType = OrderType.UPDATED_DESC,
        @HeaderMap headers: Map<String, String> = CopyMangaNetworkConfig.apiHeaders()
    ): Response<SearchResponse>

    /**
     * 保留当前搜索契约和服务端结果顺序，不添加发现页的排序参数。
     * 用户观察：App 结果接近 PC 的 /api/kb/web/searchcl/comics（platform=2、limit=12），
     * 与 H5 的 /api/v3/search/comic（platform=1、limit=20）存在差异；
     * 差异原因、是否按相关性排序尚未验证，不能据此推断两个接口是固定结果集。
     */
    @GET("api/v3/search/comic")
    suspend fun search(
        @Query("platform") platform: Int = CopyMangaNetworkConfig.WEB_PLATFORM,
        @Query("q") query: String,
        @Query("limit") limit: Int = 20,
        @Query("offset") offset: Int = 0,
        @Query("q_type") queryType: QueryType?,
        @Query("_update") update: Boolean = true,
        @HeaderMap headers: Map<String, String> = CopyMangaNetworkConfig.apiHeaders()
    ): Response<SearchResponse>

    /**
     * 获取收藏漫画列表（需要登录）
     */
    @GET("api/v3/member/collect/comics")
    suspend fun getFavoriteComics(
        @Query("free_type") freeType: Int = 1,//不清楚是什么，可能是区分漫画和小说？
        @Query("limit") limit: Int = 20,
        @Query("offset") offset: Int = 0,
        @Query("_update") update: Boolean = true,
        @Query("ordering") order: OrderType = OrderType.MODIFIER_DESC,
        @HeaderMap headers: Map<String, String> = CopyMangaNetworkConfig.apiHeaders()
    ): Response<FavoriteResponse>

    /** 获取当前账号的云端浏览记录。 */
    @GET("api/v3/member/browse/comics")
    suspend fun getBrowseHistory(
        @Query("offset") offset: Int = 0,
        @Query("limit") limit: Int = 20,
        @Query("free_type") freeType: Int? = 1,
        @Query("_update") update: Boolean = true,
        @HeaderMap headers: Map<String, String> = CopyMangaNetworkConfig.apiHeaders()
    ): Response<BrowseHistoryResponse>

    /** 抓包中的清空操作使用带表单请求体的 DELETE。 */
    @FormUrlEncoded
    @HTTP(method = "DELETE", path = "api/v3/member/browse/comics", hasBody = true)
    suspend fun clearBrowseHistory(
        @Field("free_type") freeType: Int = 1,
        @Field("_update") update: Boolean = true,
        @HeaderMap headers: Map<String, String> = CopyMangaNetworkConfig.apiHeaders()
    ): Response<SimpleResponse>

    /**
     * 获取用户信息（需要登录）
     */
    @GET("api/v3/member/info?_update=true")
    suspend fun getMemberInfo(
        @HeaderMap headers: Map<String, String> = CopyMangaNetworkConfig.apiHeaders()
    ): Response<MemberInfoResponse>

    /**
     * 使用待导入的 token 获取用户信息。
     * X-No-Auth 会阻止拦截器覆盖显式 Authorization，并在请求发出前被移除。
     */
    @GET("api/v3/member/info?_update=true")
    suspend fun getMemberInfoWithCredential(
        @Header("Authorization") authorization: String,
        @HeaderMap headers: Map<String, String> = CopyMangaNetworkConfig.noAuthApiHeaders(),
        @retrofit2.http.Tag requestMode: com.rchdliang.copymanga_neo.domain.model.RequestMode? = null,
        @retrofit2.http.Tag appIdentity: com.rchdliang.copymanga_neo.domain.model.AppCredentialIdentity? = null
    ): Response<MemberInfoResponse>

    /**
     * 试看前5页（用于向云端汇报阅读进度，需认证）
     */
    @GET("api/v3/comic/{comicPathWord}/chapter2/{chapterUuid}")
    suspend fun getTrialContent(
        @Path("comicPathWord") comicPathWord: String,
        @Path("chapterUuid") chapterUuid: String,
        @Query("platform") platform: Int = CopyMangaNetworkConfig.WEB_PLATFORM,
        @Query("_update") update: Boolean = true,
        @HeaderMap headers: Map<String, String> = CopyMangaNetworkConfig.apiHeaders()
    ): Response<TrialContentResponse>

    /**
     * 获取章节内容 HTML（需要 www.copy3000.com 域名）
     * @param comicPathWord 漫画路径标识
     * @param chapterUuid 章节 UUID
     */
    @GET("comic/{comicPathWord}/chapter/{chapterUuid}")
    suspend fun getChapterHtml(
        @Path("comicPathWord") comicPathWord: String,
        @Path("chapterUuid") chapterUuid: String,
        @Header("X-No-Auth") value: Int = 1,
    ): Response<okhttp3.ResponseBody>

    /**
     * 登录（不需要认证）
     * @param username 账号
     * @param password Base64编码的(原始密码-盐)
     * @param salt 随机4位盐
     * @param source 固定值 freeSite
     * @param version 固定值 2026.08.24
     * @param platform 固定值 1
     */
    @FormUrlEncoded
    @POST("api/v3/login")
    suspend fun login(
        @Field("username") username: String,
        @Field("password") password: String,
        @Field("salt") salt: String,
        @Field("source") source: String = "freeSite",
        @Field("version") version: String = CopyMangaNetworkConfig.WEB_VERSION,
        @Field("platform") platform: Int = CopyMangaNetworkConfig.WEB_PLATFORM,
        @HeaderMap headers: Map<String, String> = CopyMangaNetworkConfig.noAuthApiHeaders(),
        @retrofit2.http.Tag requestMode: com.rchdliang.copymanga_neo.domain.model.RequestMode? = null,
    ): Response<LoginResponse>

    /**
     * 查询漫画收藏状态与阅读进度
     */
    @GET("api/v3/comic2/{pathWord}/query")
    suspend fun queryComicStatus(
        @Path("pathWord") pathWord: String,
        @Query("platform") platform: Int = CopyMangaNetworkConfig.WEB_PLATFORM,
        @Query("_update") update: Boolean = true,
        @HeaderMap headers: Map<String, String> = CopyMangaNetworkConfig.apiHeaders()
    ): Response<ComicQueryResponse>

    /**
     * 添加漫画到书架（需要登录）
     * @param comicId 漫画 String 类型 UUID
     */
    @FormUrlEncoded
    @POST("api/v3/member/collect/comic")
    suspend fun addFavorite(
        @Field("comic_id") comicId: String,
        @Field("is_collect") isCollect: Int = 1,
        @Field("_update") update: Boolean = true,
        @HeaderMap headers: Map<String, String> = CopyMangaNetworkConfig.apiHeaders()
    ): Response<SimpleResponse>

    /**
     * 从书架移除漫画（需要登录）
     * @param collectId 收藏的 Int 类型 ID
     */
    @FormUrlEncoded
    @HTTP(method = "DELETE", path = "api/v3/member/collect/comics", hasBody = true)
    suspend fun removeFavorite(
        @Field("ids[0]") collectId: Int,
        @HeaderMap headers: Map<String, String> = CopyMangaNetworkConfig.apiHeaders()
    ): Response<SimpleResponse>

    /**
     * 从书架批量移除漫画（需要登录） ids[0]=...&ids[1]=...
     * @param collectIds 要删除的收藏 ID 列表，以 [0] -> "ids[0]" 等带方括号索引的 form 字段名提交
     */
    @FormUrlEncoded
    @HTTP(method = "DELETE", path = "api/v3/member/collect/comics", hasBody = true)
    suspend fun removeFavorites(
        @FieldMap collectIds: Map<String, Int>,
        @HeaderMap headers: Map<String, String> = CopyMangaNetworkConfig.apiHeaders()
    ): Response<SimpleResponse>
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
