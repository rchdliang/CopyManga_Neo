package com.rchdliang.copymanga_neo.data.remote.api

import com.rchdliang.copymanga_neo.data.remote.dto.ChapterListResponse
import com.rchdliang.copymanga_neo.data.remote.dto.ComicDetailResponse
import com.rchdliang.copymanga_neo.data.remote.dto.FavoriteResponse
import com.rchdliang.copymanga_neo.data.remote.dto.HomeIndexResponse
import com.rchdliang.copymanga_neo.data.remote.dto.LoginResponse
import com.rchdliang.copymanga_neo.data.remote.dto.MemberInfoResponse
import com.rchdliang.copymanga_neo.data.remote.dto.SearchResponse
import retrofit2.Response
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * CopyManga API服务接口
 * 基于copymanga网站API结构定义
 */
interface MangaApiService {
    
    /**
     * 获取首页推荐漫画列表
    //     * @param page 页码
    //     * @param limit 每页数量
     * @param timestamp 时间戳，防止缓存
     */
    @GET("api/v3/h5/homeIndex")
    suspend fun getHomeComics(
//        @Query("page") page: Int = 1,
//        @Query("limit") limit: Int = 20,
        @Query("_t") timestamp: Long = System.currentTimeMillis()
    ): Response<HomeIndexResponse>
    /**
     * 获取漫画详情
     * @param pathWord 漫画路径标识
     */
    @GET("api/v3/comic2/{pathWord}?platform=1&_update=true")
    suspend fun getComicDetail(
        @Path("pathWord") pathWord: String,
//        @Query("_t") timestamp: Long = System.currentTimeMillis()
    ): Response<ComicDetailResponse>
    
    /**
     * 获取漫画章节列表
     * @param pathWord 漫画路径标识
     */
    @GET("api/v3/comic/{pathWord}/group/default/chapters")
    suspend fun getChapters(
        @Path("pathWord") pathWord: String,
        @Query("limit") limit: Int = 100,
        @Query("offset") offset: Int = 0,
//        @Query("_t") timestamp: Long = System.currentTimeMillis()
    ): Response<ChapterListResponse>
    
    /**
     * 搜索漫画
     * @param query 搜索关键词
     * @param page 页码
     * @param timestamp 时间戳，防止缓存
     */
    @GET("api/v3/search/comic")
    suspend fun searchComics(
        @Query("q") query: String,
        @Query("page") page: Int = 1,
        @Query("_t") timestamp: Long = System.currentTimeMillis()
    ): Response<SearchResponse>

    @GET("api/v3/member/collect/comics?free_type=1&_update=true&ordering=-datetime_modifier")
    suspend fun getFavoriteComics(
        @Query("limit") limit: Int = 21,
        @Query("offset") offset: Int = 0
    ): Response<FavoriteResponse>//必要的请求头 authorization: Token d01f487...

    /**
     * 获取用户信息
     */
    @GET("api/v3/member/info?_update=true")
    suspend fun getMemberInfo(): Response<MemberInfoResponse>

    /**
     * 获取章节内容 HTML
     * @param comicPathWord 漫画路径标识
     * @param chapterUuid 章节 UUID
     */
    @GET("https://www.2026copy.com/comic/{comicPathWord}/chapter/{chapterUuid}")
    suspend fun getChapterHtml(
        @Path("comicPathWord") comicPathWord: String,
        @Path("chapterUuid") chapterUuid: String
    ): Response<okhttp3.ResponseBody>

    /**
     * 登录
     * @param username 账号
     * @param password Base64编码的(原始密码-盐)
     * @param salt 随机4位盐
     * @param source 固定值 freeSite
     * @param version 固定值 2026.04.30
     * @param platform 固定值 1
     */
    @FormUrlEncoded
    @POST("api/v3/login")
    suspend fun login(
        @Field("username") username: String,
        @Field("password") password: String,
        @Field("salt") salt: String,
        @Field("source") source: String = "freeSite",
        @Field("version") version: String = "2026.04.30",
        @Field("platform") platform: String = "1"
    ): Response<LoginResponse>
}