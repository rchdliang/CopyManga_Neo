package com.rchdliang.copymanga_neo.data.remote.dto

import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName

data class CommentReportConfigResponseDto(
    @SerializedName("code") val code: Int?,
    @SerializedName("message") val message: String?,
    @SerializedName("results") val results: CommentReportConfigDto?
)

data class CommentReportConfigDto(
    @SerializedName("report_type") val types: List<CommentReportTypeDto?>?,
    @SerializedName("report_tip") val tip: String?
)

data class CommentReportTypeDto(
    @SerializedName("type") val type: Int?,
    @SerializedName("name") val name: String?
)

data class CommentResponseDto(
    @SerializedName("code") val code: Int?,
    @SerializedName("message") val message: String?,
    @SerializedName("results") val results: CommentListDto?
)

data class CommentListDto(
    @SerializedName("list") val list: List<CommentDto?>?,
    @SerializedName("total") val total: JsonElement?,
    @SerializedName("offset") val offset: Int?,
    @SerializedName("limit") val limit: Int? = null
)

data class CommentDto(
    @SerializedName("id") val id: JsonElement?,
    @SerializedName("user_name") val username: String?,
    @SerializedName("user_avatar") val avatar: String?,
    @SerializedName("comment") val comment: String?,
    @SerializedName("create_at") val createdAt: String?,
    @SerializedName("count") val replyCount: JsonElement?,
    @SerializedName("parent_user_name") val parentUsername: String?,
    @SerializedName("user_id") val userId: String? = null
)

data class CommentPostResponseDto(
    @SerializedName("code") val code: Int?,
    @SerializedName("message") val message: String?
)
