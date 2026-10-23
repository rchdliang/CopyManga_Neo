package com.rchdliang.copymanga_neo.ui.components

import com.rchdliang.copymanga_neo.domain.repository.RiskControlException

/** Do not surface arbitrary transport/server exceptions, URLs or class names. */
internal fun userFacingError(error: Throwable?, fallback: String): String {
    if (error is RiskControlException) return "请求暂时受限，请稍后重试"
    return when (error?.message) {
        "下载存储无法读取，原数据已保留，下载操作已停用；修复存储后请重新启动",
        "下载队列无法恢复，原记录已保留，下载操作已停用；修复存储后请重新启动",
        "本地漫画下载记录无法读取，原数据已保留；修复存储后请重新启动" -> error.message!!
        "请先登录", "当前未登录" -> "请先登录后重试"
        "登录已过期，请重新登录", "登录状态已失效" -> "登录已过期，请重新登录"
        "请输入昵称", "昵称不能为空" -> "请输入昵称"
        "请选择小于 10 MB 的图片", "无法打开所选图片", "图片转换失败" -> error.message!!
        "账号或请求模式已变化，小说下载已保留", "账号或请求模式已变化，小说缓存已保留" -> "账号或请求模式已变化，离线内容已保留"
        else -> fallback
    }
}
