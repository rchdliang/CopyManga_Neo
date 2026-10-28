package com.rchdliang.copymanga_neo.domain.model

/** 官方 App 漫画与小说顶层评论使用裸 reply_id；Web 保持 reply_id=。 */
object NovelCommentFormPolicy {
    fun encodeAppTopLevelReply(method: String, path: String, encodedForm: String): String =
        if (method == "POST" && path in setOf("/api/v3/member/bookcomment", "/api/v3/member/comment")) {
            encodedForm.split('&').joinToString("&") { if (it == "reply_id=") "reply_id" else it }
        } else encodedForm
}
