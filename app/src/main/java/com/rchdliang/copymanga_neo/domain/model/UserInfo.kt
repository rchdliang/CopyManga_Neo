package com.rchdliang.copymanga_neo.domain.model

/**
 * 登录结果
 */
data class LoginInfo(
    val token: String?,
    val userId: String?,
    val username: String?,
    val nickname: String?,
    val avatar: String?,
    val ticket: Float?,
    val rewardTicket: Float?
)

/**
 * 会员信息
 */
data class MemberInfo(
    val token: String?,
    val userId: String?,
    val username: String?,
    val nickname: String?,
    val avatar: String?,
    val ticket: Float?,
    val rewardTicket: Float?,
    val dayDownloads: Int?,
    val dayDownloadsRefresh: String?
)

/** 一次输入可以登录多个模式；部分失败仍保留成功模式的独立会话。 */
data class ModeLoginResult(
    val currentMode: RequestMode,
    val successfulModes: Set<RequestMode>,
    val failures: Map<RequestMode, String>
)
