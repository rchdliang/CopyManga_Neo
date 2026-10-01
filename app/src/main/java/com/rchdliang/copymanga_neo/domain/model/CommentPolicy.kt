package com.rchdliang.copymanga_neo.domain.model

/** close_roast 为漫画及章节共用的禁评开关；close_comment 另行限制漫画评论。 */
object CommentPolicy {
    fun postingAllowed(kind: CommentKind, closeComment: Boolean, closeRoast: Boolean): Boolean =
        !closeRoast && (kind == CommentKind.CHAPTER || !closeComment)

    private val endpoints = setOf(
        "/api/v3/comments", "/api/v3/roasts",
        "/api/v3/member/comment", "/api/v3/member/roast"
    )

    /** 只豁免评论接口明确的禁评业务响应，其余 210 仍交给风控处理。 */
    fun isClosedResponse(path: String, httpCode: Int, code: Int?, message: String?): Boolean =
        path.trimEnd('/') in endpoints && isClosedResponse(httpCode, code, message)

    fun isClosedResponse(httpCode: Int, code: Int?, message: String?): Boolean =
        (httpCode == 210 || code == 210) && listOf(
            "该漫画不允许评论", "该章节不允许评论",
            "该漫画不允许吐槽", "该章节不允许吐槽"
        ).any { message?.contains(it) == true }
}
