package com.rchdliang.copymanga_neo.domain.model

/** Captured App top-level book comments use a bare reply_id; Web uses reply_id=. */
object NovelCommentFormPolicy {
    fun encodeAppTopLevelReply(method: String, path: String, encodedForm: String): String =
        if (method == "POST" && path == "/api/v3/member/bookcomment") {
            encodedForm.split('&').joinToString("&") { if (it == "reply_id=") "reply_id" else it }
        } else encodedForm
}
