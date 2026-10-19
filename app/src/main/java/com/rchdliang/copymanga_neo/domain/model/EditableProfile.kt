package com.rchdliang.copymanga_neo.domain.model

/** 编辑基准资料；未修改的头像和性别在提交时重新读取服务器当前值。 */
data class EditableProfile(
    val nickname: String,
    val avatar: String?,
    val avatarPath: String?,
    val gender: Int
)

/** 资料编辑接口的性别取值，与返回的 genders 配置一致。 */
enum class ProfileGender(val value: Int, val label: String) {
    UNKNOWN(0, "未知"),
    MALE(1, "男"),
    FEMALE(2, "女")
}
