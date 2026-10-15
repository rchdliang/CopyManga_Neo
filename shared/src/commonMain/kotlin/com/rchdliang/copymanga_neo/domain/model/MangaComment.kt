package com.rchdliang.copymanga_neo.domain.model

@kotlinx.serialization.Serializable
data class CommentReportType(val type: Int, val name: String)
@kotlinx.serialization.Serializable
data class CommentReportConfig(val types: List<CommentReportType>, val tip: String)

@kotlinx.serialization.Serializable
enum class CommentKind { COMIC, CHAPTER, NOVEL }

@kotlinx.serialization.Serializable
data class CommentTarget(val kind: CommentKind, val id: String)

@kotlinx.serialization.Serializable
data class MangaComment(
    val id: String,
    val username: String,
    val avatar: String?,
    val content: String,
    val createdAt: String,
    val replyCount: Int = 0,
    val parentUsername: String? = null
)

@kotlinx.serialization.Serializable
data class CommentPage(val comments: List<MangaComment>, val total: Int, val nextOffset: Int?)

/** 本地评论不使用在线分页游标；完整性与总数分开，明确展示部分下载。 */
@kotlinx.serialization.Serializable
data class DownloadedChapterComments(
    val comments: List<MangaComment>,
    val total: Int,
    val complete: Boolean,
    val closed: Boolean = false,
    val savedAt: Long
)

/** 少量回复用可见数量校正；较多回复的 total 沿用 API 总数。 */
@kotlinx.serialization.Serializable
data class CommentReplyPreview(val comments: List<MangaComment>, val total: Int? = null)

/** 按 Unicode 码位计算，表情不会被重复计数。 */
object CommentText {
    const val MIN_LENGTH = 3
    const val MAX_LENGTH = 200
    fun length(value: String): Int = value.trim().let { it.unicodeCodePointCount() }
    fun isValid(value: String): Boolean = length(value) in MIN_LENGTH..MAX_LENGTH
}
