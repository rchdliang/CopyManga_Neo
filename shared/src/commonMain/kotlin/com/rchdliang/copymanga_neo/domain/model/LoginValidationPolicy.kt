package com.rchdliang.copymanga_neo.domain.model

/** A correctable password error must not become a persistent, app-wide risk-control block. */
object LoginValidationPolicy {
    fun isPasswordError(message: String?): Boolean = when (message?.trim()) {
        "密码错误", "密碼錯誤" -> true
        // Deliberately exact: “密码错误次数过多” and unknown 210s must still stop requests.
        else -> false
    }
}
