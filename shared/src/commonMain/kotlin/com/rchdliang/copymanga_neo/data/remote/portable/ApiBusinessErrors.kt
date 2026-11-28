package com.rchdliang.copymanga_neo.data.remote.portable

/** 仅放行明确的业务校验 210，其余 210 继续由风控闸门处理。 */
internal object ApiBusinessErrors {
    private val unavailableComicMessage = Regex("""^[该該此本]?(?:作品|漫画|漫畫)已[经經]?被?(?:下架|合[并併])""")

    /** 单部作品的内容状态优先于共用的 210 业务码，但不能覆盖 HTTP 访问限制。 */
    fun unavailableComic(path: String, status: Int, code: Int?, message: String?): ComicUnavailableException? {
        if (message == null || (status !in 200..299 && status != 404 && status != 410)) return null
        val errorCode = code?.takeIf { it != 200 } ?: status
        if (errorCode != 210 && errorCode != 404 && errorCode != 410) return null
        val comicPath = path.startsWith("/api/v3/comic2/") || path.startsWith("/api/v3/comic/") ||
            path.startsWith("/comic/") || path.startsWith("/comicdetail/")
        return if (comicPath && unavailableComicMessage.containsMatchIn(message.trimStart())) {
            ComicUnavailableException(errorCode, message)
        } else null
    }

    fun isValidation(path: String, message: String?): Boolean {
        val text = message ?: return false
        if (Regex("/api/v3/comic/[^/]+/chapter2download/[^/]+").matches(path)) {
            return com.rchdliang.copymanga_neo.domain.model.DownloadQuotaPolicy.isExhausted(text)
        }
        return when (path) {
            "/api/v3/login" ->
                com.rchdliang.copymanga_neo.domain.model.LoginValidationPolicy.isPasswordError(text)
            "/api/v3/member/update/info" -> listOf(
                "用户頭像30天只能修改一次", "用户头像30天只能修改一次",
                "用戶頭像30天只能修改一次"
            ).any(text::contains)
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
