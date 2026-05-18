package com.rchdliang.copymanga_neo.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * 登录响应 DTO
 */
data class LoginResponse(
    @SerializedName("code") val code: Int,
    @SerializedName("message") val message: String?,
    @SerializedName("results") val results: LoginResult?
)

data class LoginResult(
    @SerializedName("token") val token: String?,
    @SerializedName("user_id") val userId: String?,
    @SerializedName("username") val username: String?,
    @SerializedName("nickname") val nickname: String?,
    @SerializedName("avatar") val avatar: String?,
    @SerializedName("datetime_created") val avatarTimeCreated: String?,
    @SerializedName("ticket") val ticket: Float?,
    @SerializedName("reward_ticket") val rewardTicket: Float?,
    @SerializedName("ads_vip_end") val adsVipEnd: String?,
    @SerializedName("post_vip_end") val postVipEnd: String?,
    @SerializedName("downloads") val downloads: Int?,
    @SerializedName("vip_downloads") val vipDownloads: Int?,
    @SerializedName("reward_downloads") val rewardDownloads: Int?,
    @SerializedName("invite_code") val inviteCode: String?,
    @SerializedName("invited") val invited: String?,
    @SerializedName("scy_answer") val scyAnswer: Boolean?
)

/**
 * 会员信息响应 DTO
 */
data class MemberInfoResponse(
    @SerializedName("code") val code: Int,
    @SerializedName("message") val message: String?,
    @SerializedName("results") val results: MemberInfoResult?
)

data class MemberInfoResult(
    @SerializedName("token") val token: String?,
    @SerializedName("user_id") val userId: String?,
    @SerializedName("username") val username: String?,
    @SerializedName("nickname") val nickname: String?,
    @SerializedName("avatar") val avatar: String?,
    @SerializedName("datetime_created") val avatarTimeCreated: String?,
    @SerializedName("ticket") val ticket: Float?,
    @SerializedName("reward_ticket") val rewardTicket: Float?,
    @SerializedName("ads_vip_end") val adsVipEnd: String?,
    @SerializedName("post_vip_end") val postVipEnd: String?,
    @SerializedName("downloads") val downloads: Int?,
    @SerializedName("vip_downloads") val vipDownloads: Int?,
    @SerializedName("reward_downloads") val rewardDownloads: Int?,
    @SerializedName("invite_code") val inviteCode: String?,
    @SerializedName("invited") val invited: String?,
    @SerializedName("scy_answer") val scyAnswer: Boolean?,
    @SerializedName("day_downloads") val dayDownloads: Int?,
    @SerializedName("day_downloads_refresh") val dayDownloadsRefresh: String?
)
