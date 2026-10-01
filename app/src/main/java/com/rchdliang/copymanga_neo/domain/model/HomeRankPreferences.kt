package com.rchdliang.copymanga_neo.domain.model

/** 首页排行榜偏好，非法或缺失的存储值回退到男榜日榜。 */
data class HomeRankPreferences(val audience: String = "male", val period: String = "day") {
    fun normalized() = HomeRankPreferences(
        audience.takeIf { it in setOf("male", "female") } ?: "male",
        period.takeIf { it in setOf("day", "week", "month", "total") } ?: "day"
    )
}
