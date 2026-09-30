package com.rchdliang.copymanga_neo.domain.model

enum class CommentKind { COMIC, CHAPTER }

data class CommentTarget(val kind: CommentKind, val id: String)

data class MangaComment(
    val id: String,
    val username: String,
    val avatar: String?,
    val content: String,
    val createdAt: String,
    val replyCount: Int = 0,
    val parentUsername: String? = null
)

data class CommentPage(val comments: List<MangaComment>, val total: Int, val nextOffset: Int?)

/** 按 Unicode 码位计算，表情不会被重复计数。 */
object CommentText {
    const val MIN_LENGTH = 3
    const val MAX_LENGTH = 200
    fun length(value: String): Int = value.trim().let { it.codePointCount(0, it.length) }
    fun isValid(value: String): Boolean = length(value) in MIN_LENGTH..MAX_LENGTH
}
