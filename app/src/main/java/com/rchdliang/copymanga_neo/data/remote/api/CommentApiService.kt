package com.rchdliang.copymanga_neo.data.remote.api

import com.rchdliang.copymanga_neo.data.remote.CopyMangaNetworkConfig
import com.rchdliang.copymanga_neo.data.remote.dto.CommentPostResponseDto
import com.rchdliang.copymanga_neo.data.remote.dto.CommentResponseDto
import retrofit2.Response
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.HeaderMap
import retrofit2.http.POST
import retrofit2.http.Query

/** 漫画评论与章节吐槽分别使用官方接口；读取不依赖登录。 */
interface CommentApiService {
    companion object {
        /** 评论沿用 Web API 公共头；标记仅用于路由和阻止 App 协议改写。 */
        fun headers(): Map<String, String> = CopyMangaNetworkConfig.apiHeaders() +
                ("X-Copy-Comment" to "1")

        /** 发表使用调用方固定的 Token，不由认证拦截器替换。 */
        fun postHeaders(): Map<String, String> = headers() + ("X-No-Auth" to "1")
    }

    @GET("api/v3/comments")
    suspend fun comicComments(
        @Query("comic_id") comicId: String,
        @Query("reply_id") replyId: String? = null,
        @Query("offset") offset: Int = 0,
        @Query("limit") limit: Int = 20,
        @HeaderMap headers: Map<String, String> = headers()
    ): Response<CommentResponseDto>

    @GET("api/v3/roasts")
    suspend fun chapterComments(
        @Query("chapter_id") chapterId: String,
        @Query("offset") offset: Int = 0,
        @Query("limit") limit: Int = 30,
        @Query("_update") update: Boolean = true,
        @HeaderMap headers: Map<String, String> = headers()
    ): Response<CommentResponseDto>

    @FormUrlEncoded
    @POST("api/v3/member/comment")
    suspend fun postComicComment(
        @Field("comic_id") comicId: String,
        @Field("comment") content: String,
        @Field("reply_id") replyId: String?,
        @Header("Authorization") authorization: String,
        @Query("platform") platform: Int = 3,
        @HeaderMap headers: Map<String, String> = postHeaders()
    ): Response<CommentPostResponseDto>

    @FormUrlEncoded
    @POST("api/v3/member/roast")
    suspend fun postChapterComment(
        @Field("chapter_id") chapterId: String,
        @Field("roast") content: String,
        @Header("Authorization") authorization: String,
        @Field("_update") update: Boolean = true,
        @HeaderMap headers: Map<String, String> = postHeaders()
    ): Response<CommentPostResponseDto>
}
