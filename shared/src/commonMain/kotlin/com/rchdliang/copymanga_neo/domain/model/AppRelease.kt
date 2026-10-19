package com.rchdliang.copymanga_neo.domain.model

data class AppRelease(val tag: String, val version: String, val pageUrl: String)

object AppVersion {
    fun isNewer(latest: String, current: String): Boolean {
        fun numbers(value: String): List<Int> {
            val match = Regex("^(?:ver\\.|v)?([0-9]+(?:\\.[0-9]+)*)").find(value.trim())
                ?: error("应用版本格式无效")
            return match.groupValues[1].split('.').map { it.toInt() }
        }

        val left = numbers(latest)
        val right = numbers(current)
        for (index in 0 until maxOf(left.size, right.size)) {
            val comparison = (left.getOrElse(index) { 0 }).compareTo(right.getOrElse(index) { 0 })
            if (comparison != 0) return comparison > 0
        }
        return '-' in current && '-' !in latest
    }
}

object ChineseLanguageDefault {
    /** 显式字形优先；缺少字形时按中文地区推断，未知中文地区使用默认简体字形。 */
    fun enabled(languageTag: String): Boolean {
        val parts = languageTag.replace('_', '-').lowercase().split('-')
        if (parts.firstOrNull() != "zh") return false
        if ("hans" in parts) return true
        if ("hant" in parts) return false
        return parts.none { it in setOf("tw", "hk", "mo") }
    }
}
