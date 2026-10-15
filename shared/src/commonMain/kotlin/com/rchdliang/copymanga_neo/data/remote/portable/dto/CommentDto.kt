package com.rchdliang.copymanga_neo.data.remote.portable.dto

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CommentReportConfigResponseDto(
    @SerialName("code") val code: Int? = null,
    @SerialName("message") val message: String? = null,
    @SerialName("results") val results: CommentReportConfigDto? = null
)

@Serializable
data class CommentReportConfigDto(
    @SerialName("report_type") val types: List<CommentReportTypeDto?>? = null,
    @SerialName("report_tip") val tip: String? = null
)

@Serializable
data class CommentReportTypeDto(
    @SerialName("type") val type: Int? = null,
    @SerialName("name") val name: String? = null
)

@Serializable
data class CommentResponseDto(
    @SerialName("code") val code: Int? = null,
    @SerialName("message") val message: String? = null,
    @SerialName("results") val results: CommentListDto? = null
)

@Serializable
data class CommentListDto(
    @SerialName("list") val list: List<CommentDto?>? = null,
    @SerialName("total") val total: JsonElement? = null,
    @SerialName("offset") val offset: Int? = null,
    @SerialName("limit") val limit: Int? = null
)

@Serializable
data class CommentDto(
    @SerialName("id") val id: JsonElement? = null,
    @SerialName("user_name") val username: String? = null,
    @SerialName("user_avatar") val avatar: String? = null,
    @SerialName("comment") val comment: String? = null,
    @SerialName("create_at") val createdAt: String? = null,
    @SerialName("count") val replyCount: JsonElement? = null,
    @SerialName("parent_user_name") val parentUsername: String? = null
)

@Serializable
data class CommentPostResponseDto(
    @SerialName("code") val code: Int? = null,
    @SerialName("message") val message: String? = null
)
