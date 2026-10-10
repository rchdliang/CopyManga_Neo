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

/** 只恢复一种模式；旧多模式包的其余项向用户明确说明未导入。 */
data class CredentialImportResult(
    val memberInfo: MemberInfo,
    val currentMode: RequestMode,
    val sessionVersion: Long,
    val ignoredModes: Set<RequestMode> = emptySet()
)
