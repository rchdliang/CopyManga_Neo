package com.rchdliang.copymanga_neo.data.remote

/** 仅放行抓包已确认的业务校验 210，其余 210 继续由风控闸门处理。 */
internal object ApiBusinessErrors {
    fun isValidation(path: String, message: String?): Boolean {
        val text = message ?: return false
        return when (path) {
            "/api/v3/register" -> listOf(
                "用戶名長度", "用户名长度", "密碼長度", "密码长度",
                "id已被佔用", "id已被占用", "賬號只能包括", "账号只能包括"
            ).any(text::contains)

            "/api/v3/member/comment" -> listOf(
                "評論長度為3-200", "评论长度为3-200",
                "注冊24小時候才可以發評論", "注册24小时后才可以发评论"
            ).any(text::contains)

            else -> false
        }
    }
}
