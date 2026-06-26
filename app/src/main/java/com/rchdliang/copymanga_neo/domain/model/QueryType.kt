package com.rchdliang.copymanga_neo.domain.model

/**
 * 搜索查询类型
 */
enum class QueryType(val value: String) {
    LOCAL("local"),   // 汉化组
    NAME("name"),     // 漫画名称
    AUTHOR("author"); // 作者

    override fun toString(): String = value
}
