package com.rchdliang.copymanga_neo.domain.model

/** 使用服务器当前头像路径及性别，单独改昵称不会重复提交未保存的头像。 */
data class EditableProfile(
    val nickname: String,
    val avatar: String?,
    val avatarPath: String?,
    val gender: Int
)
