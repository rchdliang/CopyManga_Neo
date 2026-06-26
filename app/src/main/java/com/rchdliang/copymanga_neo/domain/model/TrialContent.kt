package com.rchdliang.copymanga_neo.domain.model

/**
 * 试看内容结果
 */
data class TrialContent(
    val showApp: Boolean,
    val isLock: Boolean,
    val isLogin: Boolean,
    val isVip: Boolean,
    val isBanned: Boolean
)
