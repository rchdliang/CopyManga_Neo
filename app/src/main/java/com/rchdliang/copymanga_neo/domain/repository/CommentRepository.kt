package com.rchdliang.copymanga_neo.domain.repository

import com.rchdliang.copymanga_neo.domain.model.CommentPage
import com.rchdliang.copymanga_neo.domain.model.CommentReplyPreview
import com.rchdliang.copymanga_neo.domain.model.CommentTarget

/** 评论禁用为业务限制，与网络风控无关。 */
class CommentClosedException(message: String) : IllegalStateException(message)

/** 服务端已删除；本地缓存失败不应提示用户重新提交删除。 */
class CommentDeletedCacheException(cause: Throwable) :
    IllegalStateException("评论已删除，但离线缓存更新失败，请重新下载评论", cause)

interface CommentRepository {
    /** 仅开放已验证的请求模式与评论类型。 */
    fun supportsDeletion(target: CommentTarget): Boolean = false

    /** 删除自己的评论或回复；仓库须重新核对当前账号与作者 ID。 */
    suspend fun deleteComment(
        target: CommentTarget,
        comment: com.rchdliang.copymanga_neo.domain.model.MangaComment
    ): Result<Unit> = Result.failure(IllegalStateException("当前模式不支持删除评论"))

    suspend fun getDownloadedChapterComments(uuid: String): Result<com.rchdliang.copymanga_neo.domain.model.DownloadedChapterComments?>

    /** 保存所有可获取的章节评论；失败保留已获取页，不影响已完成的图片。 */
    suspend fun downloadChapterComments(uuid: String): Result<com.rchdliang.copymanga_neo.domain.model.DownloadedChapterComments>

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
