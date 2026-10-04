package com.rchdliang.copymanga_neo.domain.model

/** 账号已创建；会话变化时交由用户手动登录，不能重新提交注册。 */
data class RegistrationInfo(val username: String, val canAutoLogin: Boolean)

/** 注册校验来自官方 App 的实际业务响应，不限制密码字符种类。 */
object RegistrationPolicy {
    fun usernameError(username: String): String? = when {
        username.isEmpty() -> "请输入账号"
        !Regex("[A-Za-z0-9@.+_\\-]+").matches(username) -> "账号仅支持字母、数字和 @ . + - _"
        username.length !in 6..36 -> "账号长度须为 6–36 位"
        else -> null
    }

    fun passwordError(password: String): String? = when {
        password.isEmpty() -> "请输入密码"
        password.codePointCount(0, password.length) < 6 -> "密码至少需要 6 位"
        password.codePointCount(0, password.length) > 36 -> "密码最多 36 位"
        else -> null
    }

    fun confirmationError(password: String, confirmation: String): String? = when {
        confirmation.isEmpty() -> "请再次输入密码"
        password != confirmation -> "两次输入的密码不一致"
        else -> null
    }

    fun validationError(username: String, password: String): String? =
        usernameError(username) ?: passwordError(password)

    /** 服务端账号冲突归到账号字段，其他网络或业务错误保留在表单级。 */
    fun isUsernameUnavailable(message: String): Boolean =
        listOf(
            "id已被佔用", "id已被占用", "账号不可用", "賬號不可用",
            "用戶名長度", "用户名长度", "賬號只能包括", "账号只能包括"
        ).any(message::contains)
}
