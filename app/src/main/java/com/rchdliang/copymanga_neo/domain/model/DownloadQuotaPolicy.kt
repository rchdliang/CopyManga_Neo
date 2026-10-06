package com.rchdliang.copymanga_neo.domain.model

/** 只识别明确的下载额度不足，认证、风控及章节权限错误不能触发阅读回退。 */
object DownloadQuotaPolicy {
    fun isExhausted(message: String?): Boolean {
        val text = message ?: return false
        return listOf("下载", "下載").any(text::contains) &&
                listOf(
                    "次数",
                    "次數",
                    "额度",
                    "額度",
                    "限额",
                    "限額",
                    "上限"
                ).any(text::contains) &&
                listOf(
                    "不足",
                    "用完",
                    "耗尽",
                    "耗盡",
                    "超过",
                    "超過",
                    "超出",
                    "达到",
                    "達到",
                    "已达",
                    "已達"
                ).any(text::contains)
    }
}
