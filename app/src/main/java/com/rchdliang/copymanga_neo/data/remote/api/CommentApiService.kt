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

/** 漫画、整本小说评论与章节吐槽分别使用对应接口；读取不依赖登录。 */
interface CommentApiService {
    @GET("api/v3/bookcomments")
    suspend fun novelComments(
        @Query("book_id") bookId: String,
        @Query("reply_id") replyId: String = "",
        @Query("offset") offset: Int = 0,
        @Query("limit") limit: Int = 20,
        @HeaderMap headers: Map<String, String> = headers()
    ): Response<CommentResponseDto>

    /** 整本小说发表与回复共用接口；顶层也保留空 reply_id 字段。 */
    @FormUrlEncoded
    @POST("api/v3/member/bookcomment")
    suspend fun postNovelComment(
        @Field("book_id") bookId: String,
        @Field("comment") content: String,
        @Field("reply_id") replyId: String,
        @Header("Authorization") authorization: String,
        @HeaderMap headers: Map<String, String>,
        @retrofit2.http.Tag requestMode: com.rchdliang.copymanga_neo.domain.model.RequestMode
    ): Response<CommentPostResponseDto>

    @GET("api/v3/bookcomment/config")
    suspend fun novelReportConfig(
        @Query("_update") update: Boolean = true,
        @HeaderMap headers: Map<String, String> = headers()
    ): Response<com.rchdliang.copymanga_neo.data.remote.dto.CommentReportConfigResponseDto>

    @FormUrlEncoded
    @POST("api/v3/member/bookcomment/report")
    suspend fun reportNovelComment(
        @Field("book_id") bookId: String,
        @Field("comment_id") commentId: String,
        @Field("report_type") type: Int,
        @Field("report_reason") reason: String,
        @Header("Authorization") authorization: String,
        @Field("_update") update: Boolean = true,
        @HeaderMap headers: Map<String, String> = postHeaders(),
        @retrofit2.http.Tag requestMode: com.rchdliang.copymanga_neo.domain.model.RequestMode? = null
    ): Response<CommentPostResponseDto>

    @GET("api/v3/roast/config")
    suspend fun chapterReportConfig(
        @Query("_update") update: Boolean = true,
        @HeaderMap headers: Map<String, String> = headers()
    ): Response<com.rchdliang.copymanga_neo.data.remote.dto.CommentReportConfigResponseDto>

    @FormUrlEncoded
    @POST("api/v3/member/roast/report")
    suspend fun reportChapterComment(
        @Field("roast_id") roastId: String,
        @Field("chapter_id") chapterId: String,
        @Field("report_type") type: Int,
        @Field("report_reason") reason: String,
        @Header("Authorization") authorization: String,
        @Field("_update") update: Boolean = true,
        @HeaderMap headers: Map<String, String> = postHeaders(),
        @retrofit2.http.Tag requestMode: com.rchdliang.copymanga_neo.domain.model.RequestMode? = null
    ): Response<CommentPostResponseDto>

    @GET("api/v3/comment/config")
    suspend fun reportConfig(
        @Query("_update") update: Boolean = true,
        @HeaderMap headers: Map<String, String> = headers()
    ): Response<com.rchdliang.copymanga_neo.data.remote.dto.CommentReportConfigResponseDto>

    @FormUrlEncoded
    @POST("api/v3/member/comment/report")
    suspend fun reportComment(
        @Field("comic_id") comicId: String,
        @Field("comment_id") commentId: String,
        @Field("report_type") type: Int,
        @Field("report_reason") reason: String,
        @Header("Authorization") authorization: String,
        @Field("_update") update: Boolean = true,
        @HeaderMap headers: Map<String, String> = postHeaders(),
        @retrofit2.http.Tag requestMode: com.rchdliang.copymanga_neo.domain.model.RequestMode? = null
    ): Response<CommentPostResponseDto>

    companion object {
        /** Web 保留网页头；App 按请求模式构造官方请求头与公共表单。 */
        fun headers(): Map<String, String> = CopyMangaNetworkConfig.apiHeaders() +
                ("X-Copy-Comment" to "1")

        fun readHeaders(appMode: Boolean): Map<String, String> =
            if (appMode) CopyMangaNetworkConfig.apiHeaders() else headers()

        /** 发表使用调用方固定的 Token，不由认证拦截器替换。 */
        fun postHeaders(appMode: Boolean = false): Map<String, String> =
            readHeaders(appMode) + ("X-No-Auth" to "1")

        /** 固定举报账号，防止认证拦截器替换显式 Token。 */
        fun reportHeaders(appMode: Boolean): Map<String, String> =
            readHeaders(appMode) + ("X-No-Auth" to "1")
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
        @HeaderMap headers: Map<String, String> = headers(),
        @Query("view") view: String? = null
    ): Response<CommentResponseDto>

    /** 删除使用表单请求体，并固定请求模式及登录凭据。 */
    @FormUrlEncoded
    @retrofit2.http.HTTP(method = "DELETE", path = "api/v3/member/comment", hasBody = true)
    suspend fun deleteComicComment(
        @Field("comic_id") comicId: String,
        @Field("comment_id") commentId: String,
        @Header("Authorization") authorization: String,
        @HeaderMap headers: Map<String, String>,
        @retrofit2.http.Tag requestMode: com.rchdliang.copymanga_neo.domain.model.RequestMode
    ): Response<CommentPostResponseDto>

    @FormUrlEncoded
    @retrofit2.http.HTTP(method = "DELETE", path = "api/v3/member/roast", hasBody = true)
    suspend fun deleteChapterComment(
        @Field("chapter_id") chapterId: String,
        @Field("roast_id") roastId: String,
        @Header("Authorization") authorization: String,
        @HeaderMap headers: Map<String, String>,
        @retrofit2.http.Tag requestMode: com.rchdliang.copymanga_neo.domain.model.RequestMode
    ): Response<CommentPostResponseDto>

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
