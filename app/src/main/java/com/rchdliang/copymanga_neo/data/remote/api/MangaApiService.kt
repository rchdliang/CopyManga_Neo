package com.rchdliang.copymanga_neo.data.remote.api

import com.rchdliang.copymanga_neo.data.remote.dto.ChapterListResponse
import com.rchdliang.copymanga_neo.data.remote.dto.ComicDetailResponse
import com.rchdliang.copymanga_neo.data.remote.dto.HomeIndexResponse
import com.rchdliang.copymanga_neo.data.remote.dto.SearchResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * CopyManga API服务接口
 * 基于copymanga网站API结构定义
 */
interface MangaApiService {
    
    /**
     * 获取首页推荐漫画列表
     * @param page 页码
     * @param limit 每页数量
     * @param timestamp 时间戳，防止缓存
     */
    @GET("api/v3/h5/homeIndex")
    suspend fun getHomeComics(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20,
        @Query("_t") timestamp: Long = System.currentTimeMillis()
    ): Response<HomeIndexResponse>

    @GET("api/v3/h5/homeIndex")
    suspend fun getHomeComicsRaw(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20,
        @Query("_t") timestamp: Long = System.currentTimeMillis()
    ): Response<okhttp3.ResponseBody>
    /**
     * 获取漫画详情
     * @param pathWord 漫画路径标识
     * @param timestamp 时间戳，防止缓存
     */
    @GET("api/v3/comic/{pathWord}")
    suspend fun getComicDetail(
        @Path("pathWord") pathWord: String,
        @Query("_t") timestamp: Long = System.currentTimeMillis()
    ): Response<ComicDetailResponse>
    
    /**
     * 获取漫画章节列表
     * @param pathWord 漫画路径标识
     * @param timestamp 时间戳，防止缓存
     */
    @GET("api/v3/comic/{pathWord}/group/default/chapters")
    suspend fun getChapters(
        @Path("pathWord") pathWord: String,
        @Query("_t") timestamp: Long = System.currentTimeMillis()
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
}