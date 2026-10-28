package com.rchdliang.copymanga_neo.domain.model

/**
 * 按优先级保留非空书名；书名可以与路由相同，不能据此判定资料缺失。
 * 保留 path 参数以兼容现有调用，但不再用它推断或填充书名。
 */
@Suppress("UNUSED_PARAMETER")
fun safeBookTitle(path: String, vararg candidates: String?): String =
    candidates.asSequence().mapNotNull { it?.trim() }
        .firstOrNull { it.isNotEmpty() }.orEmpty()

fun displayBookTitle(path: String, vararg candidates: String?): String =
    safeBookTitle(path, *candidates).ifBlank { "作品信息暂缺" }
