package com.rchdliang.copymanga_neo.domain.model

/** 识别官方配额下载接口返回的额度不足提示。 */
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
