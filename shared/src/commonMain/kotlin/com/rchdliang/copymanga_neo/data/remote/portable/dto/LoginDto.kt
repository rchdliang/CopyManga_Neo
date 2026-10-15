package com.rchdliang.copymanga_neo.data.remote.portable.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 登录响应 DTO
 */
@Serializable
data class LoginResponse(
    @SerialName("code") val code: Int,
    @SerialName("message") val message: String? = null,
    @SerialName("results") val results: LoginResult? = null
)

@Serializable
data class LoginResult(
    @SerialName("token") val token: String? = null,
    @SerialName("user_id") val userId: String? = null,
    @SerialName("username") val username: String? = null,
    @SerialName("nickname") val nickname: String? = null,
    @SerialName("avatar") val avatar: String? = null,
    @SerialName("datetime_created") val avatarTimeCreated: String? = null,
    @SerialName("ticket") val ticket: Float? = null,
    @SerialName("reward_ticket") val rewardTicket: Float? = null,
    @SerialName("ads_vip_end") val adsVipEnd: String? = null,
    @SerialName("post_vip_end") val postVipEnd: String? = null,
    @SerialName("downloads") val downloads: Int? = null,
    @SerialName("vip_downloads") val vipDownloads: Int? = null,
    @SerialName("reward_downloads") val rewardDownloads: Int? = null,
    @SerialName("invite_code") val inviteCode: String? = null,
    @SerialName("invited") val invited: String? = null,
    @SerialName("scy_answer") val scyAnswer: Boolean? = null
)

/**
 * 会员信息响应 DTO
 */
@Serializable
data class MemberInfoResponse(
    @SerialName("code") val code: Int,
    @SerialName("message") val message: String? = null,
    @SerialName("results") val results: MemberInfoResult? = null
)

@Serializable
data class MemberInfoResult(
    @SerialName("token") val token: String? = null,
    @SerialName("user_id") val userId: String? = null,
    @SerialName("username") val username: String? = null,
    @SerialName("nickname") val nickname: String? = null,
    @SerialName("avatar") val avatar: String? = null,
    @SerialName("datetime_created") val avatarTimeCreated: String? = null,
    @SerialName("ticket") val ticket: Float? = null,
    @SerialName("reward_ticket") val rewardTicket: Float? = null,
    @SerialName("ads_vip_end") val adsVipEnd: String? = null,
    @SerialName("post_vip_end") val postVipEnd: String? = null,
    @SerialName("downloads") val downloads: Int? = null,
    @SerialName("vip_downloads") val vipDownloads: Int? = null,
    @SerialName("reward_downloads") val rewardDownloads: Int? = null,
    @SerialName("invite_code") val inviteCode: String? = null,
    @SerialName("invited") val invited: String? = null,
    @SerialName("scy_answer") val scyAnswer: Boolean? = null,
    @SerialName("day_downloads") val dayDownloads: Int? = null,
    @SerialName("day_downloads_refresh") val dayDownloadsRefresh: String? = null
)
