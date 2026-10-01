package com.rchdliang.copymanga_neo.domain.repository

import com.rchdliang.copymanga_neo.domain.model.CommentPage
import com.rchdliang.copymanga_neo.domain.model.CommentReplyPreview
import com.rchdliang.copymanga_neo.domain.model.CommentTarget

/** 评论禁用为业务限制，与网络风控无关。 */
class CommentClosedException(message: String) : IllegalStateException(message)

interface CommentRepository {
    suspend fun getReportConfig(target: CommentTarget): Result<com.rchdliang.copymanga_neo.domain.model.CommentReportConfig>

    suspend fun reportComment(
        target: CommentTarget,
        commentId: String,
        type: Int,
        reason: String
    ): Result<Unit>

    /** 展示两条预览；少量回复按实际数量校正，其余沿用 API 总数。 */
    suspend fun getReplyPreview(
        target: CommentTarget,
        commentId: String
    ): Result<CommentReplyPreview>

    suspend fun getComments(
        target: CommentTarget,
        offset: Int = 0,
        replyId: String? = null
    ): Result<CommentPage>

    suspend fun postComment(
        target: CommentTarget,
        content: String,
        replyId: String? = null
    ): Result<Unit>
}
