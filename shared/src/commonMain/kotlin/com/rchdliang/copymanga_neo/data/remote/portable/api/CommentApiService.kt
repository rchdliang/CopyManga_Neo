package com.rchdliang.copymanga_neo.data.remote.portable.api

import com.rchdliang.copymanga_neo.data.remote.portable.*
import com.rchdliang.copymanga_neo.data.remote.portable.dto.*
import com.rchdliang.copymanga_neo.domain.model.*
import io.ktor.http.encodeURLPathPart
import kotlinx.serialization.serializer
import kotlinx.serialization.json.JsonObject
import kotlin.time.Clock

/** Portable implementation of the pinned upstream endpoint contract. */
class CommentApiService(private val client: CopyMangaClient, private val www: Boolean = false) {
    companion object {
        fun headers() = CopyMangaNetworkConfig.apiHeaders() + ("X-Copy-Comment" to "1")
        fun readHeaders(appMode: Boolean) = if(appMode) CopyMangaNetworkConfig.apiHeaders() else headers()
        fun postHeaders(appMode: Boolean = false) = readHeaders(appMode) + ("X-No-Auth" to "1")
        fun reportHeaders(appMode: Boolean) = postHeaders(appMode)
    }
    suspend fun novelComments(
        bookId: String,
        replyId: String = "",
        offset: Int = 0,
        limit: Int = 20,
        headers: Map<String, String> = headers()
    ): PortableResponse<CommentResponseDto> = client.call(ApiRequest(path = "/api/v3/bookcomments", method = "GET", query = listOfNotNull("book_id" to bookId.toString(), "reply_id" to replyId.toString(), "offset" to offset.toString(), "limit" to limit.toString()), form = null, headers = headers, www = www, mode = null, appIdentity = null), serializer<CommentResponseDto>())

    suspend fun novelReportConfig(
        update: Boolean = true,
        headers: Map<String, String> = headers()
    ): PortableResponse<CommentReportConfigResponseDto> = client.call(ApiRequest(path = "/api/v3/bookcomment/config", method = "GET", query = listOfNotNull("_update" to update.toString()), form = null, headers = headers, www = www, mode = null, appIdentity = null), serializer<CommentReportConfigResponseDto>())

    suspend fun reportNovelComment(
        bookId: String,
        commentId: String,
        type: Int,
        reason: String,
        authorization: String,
        update: Boolean = true,
        headers: Map<String, String> = postHeaders(),
        requestMode: com.rchdliang.copymanga_neo.domain.model.RequestMode? = null
    ): PortableResponse<CommentPostResponseDto> = client.call(ApiRequest(path = "/api/v3/member/bookcomment/report", method = "POST", query = emptyList(), form = listOfNotNull("book_id" to bookId.toString(), "comment_id" to commentId.toString(), "report_type" to type.toString(), "report_reason" to reason.toString(), "_update" to update.toString()), headers = headers + mapOf("Authorization" to authorization), www = www, mode = requestMode, appIdentity = null, invalidateCurrentSessionOn401 = true), serializer<CommentPostResponseDto>())

    suspend fun chapterReportConfig(
        update: Boolean = true,
        headers: Map<String, String> = headers()
    ): PortableResponse<CommentReportConfigResponseDto> = client.call(ApiRequest(path = "/api/v3/roast/config", method = "GET", query = listOfNotNull("_update" to update.toString()), form = null, headers = headers, www = www, mode = null, appIdentity = null), serializer<CommentReportConfigResponseDto>())

    suspend fun reportChapterComment(
        roastId: String,
        chapterId: String,
        type: Int,
        reason: String,
        authorization: String,
        update: Boolean = true,
        headers: Map<String, String> = postHeaders()
    ): PortableResponse<CommentPostResponseDto> = client.call(ApiRequest(path = "/api/v3/member/roast/report", method = "POST", query = emptyList(), form = listOfNotNull("roast_id" to roastId.toString(), "chapter_id" to chapterId.toString(), "report_type" to type.toString(), "report_reason" to reason.toString(), "_update" to update.toString()), headers = headers + mapOf("Authorization" to authorization), www = www, mode = null, appIdentity = null, invalidateCurrentSessionOn401 = true), serializer<CommentPostResponseDto>())

    suspend fun reportConfig(
        update: Boolean = true,
        headers: Map<String, String> = headers()
    ): PortableResponse<CommentReportConfigResponseDto> = client.call(ApiRequest(path = "/api/v3/comment/config", method = "GET", query = listOfNotNull("_update" to update.toString()), form = null, headers = headers, www = www, mode = null, appIdentity = null), serializer<CommentReportConfigResponseDto>())

    suspend fun reportComment(
        comicId: String,
        commentId: String,
        type: Int,
        reason: String,
        authorization: String,
        update: Boolean = true,
        headers: Map<String, String> = postHeaders()
    ): PortableResponse<CommentPostResponseDto> = client.call(ApiRequest(path = "/api/v3/member/comment/report", method = "POST", query = emptyList(), form = listOfNotNull("comic_id" to comicId.toString(), "comment_id" to commentId.toString(), "report_type" to type.toString(), "report_reason" to reason.toString(), "_update" to update.toString()), headers = headers + mapOf("Authorization" to authorization), www = www, mode = null, appIdentity = null, invalidateCurrentSessionOn401 = true), serializer<CommentPostResponseDto>())

    suspend fun comicComments(
        comicId: String,
        replyId: String? = null,
        offset: Int = 0,
        limit: Int = 20,
        headers: Map<String, String> = headers()
    ): PortableResponse<CommentResponseDto> = client.call(ApiRequest(path = "/api/v3/comments", method = "GET", query = listOfNotNull("comic_id" to comicId.toString(), replyId?.let { "reply_id" to it.toString() }, "offset" to offset.toString(), "limit" to limit.toString()), form = null, headers = headers, www = www, mode = null, appIdentity = null), serializer<CommentResponseDto>())

    suspend fun chapterComments(
        chapterId: String,
        offset: Int = 0,
        limit: Int = 30,
        update: Boolean = true,
        headers: Map<String, String> = headers()
    ): PortableResponse<CommentResponseDto> = client.call(ApiRequest(path = "/api/v3/roasts", method = "GET", query = listOfNotNull("chapter_id" to chapterId.toString(), "offset" to offset.toString(), "limit" to limit.toString(), "_update" to update.toString()), form = null, headers = headers, www = www, mode = null, appIdentity = null), serializer<CommentResponseDto>())

    suspend fun postComicComment(
        comicId: String,
        content: String,
        replyId: String?,
        authorization: String,
        platform: Int = 3,
        headers: Map<String, String> = postHeaders()
    ): PortableResponse<CommentPostResponseDto> = client.call(ApiRequest(path = "/api/v3/member/comment", method = "POST", query = listOfNotNull("platform" to platform.toString()), form = listOfNotNull("comic_id" to comicId.toString(), "comment" to content.toString(), replyId?.let { "reply_id" to it.toString() }), headers = headers + mapOf("Authorization" to authorization), www = www, mode = null, appIdentity = null, invalidateCurrentSessionOn401 = true), serializer<CommentPostResponseDto>())

    suspend fun postChapterComment(
        chapterId: String,
        content: String,
        authorization: String,
        update: Boolean = true,
        headers: Map<String, String> = postHeaders()
    ): PortableResponse<CommentPostResponseDto> = client.call(ApiRequest(path = "/api/v3/member/roast", method = "POST", query = emptyList(), form = listOfNotNull("chapter_id" to chapterId.toString(), "roast" to content.toString(), "_update" to update.toString()), headers = headers + mapOf("Authorization" to authorization), www = www, mode = null, appIdentity = null, invalidateCurrentSessionOn401 = true), serializer<CommentPostResponseDto>())
}
