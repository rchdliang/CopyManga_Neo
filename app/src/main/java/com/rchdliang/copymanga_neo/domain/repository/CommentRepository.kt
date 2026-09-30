package com.rchdliang.copymanga_neo.domain.repository

import com.rchdliang.copymanga_neo.domain.model.CommentPage
import com.rchdliang.copymanga_neo.domain.model.CommentTarget

interface CommentRepository {
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
