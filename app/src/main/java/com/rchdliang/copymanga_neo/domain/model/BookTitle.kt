package com.rchdliang.copymanga_neo.domain.model

/** 路由标识不能作为书名；缺失时持久化空值，由界面显示可读状态。 */
fun safeBookTitle(path: String, vararg candidates: String?): String =
    candidates.asSequence().mapNotNull { it?.trim() }
        .firstOrNull { it.isNotEmpty() && !it.equals(path.trim(), ignoreCase = true) }.orEmpty()

fun displayBookTitle(path: String, vararg candidates: String?): String =
    safeBookTitle(path, *candidates).ifBlank { "作品信息暂缺" }
