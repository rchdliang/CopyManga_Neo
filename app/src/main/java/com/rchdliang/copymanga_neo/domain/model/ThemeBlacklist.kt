package com.rchdliang.copymanga_neo.domain.model

/** 优先比较题材标识；接口缺少标识时才使用名称。 */
fun Theme.matches(other: Theme): Boolean {
    val path = pathWord?.trim()?.takeIf { it.isNotEmpty() }
    val otherPath = other.pathWord?.trim()?.takeIf { it.isNotEmpty() }
    return if (path != null && otherPath != null) {
        path == otherPath
    } else {
        name.isNotBlank() && name.trim() == other.name.trim()
    }
}

fun List<Theme>.blocks(theme: Theme): Boolean = any { it.matches(theme) }

/** 仅过滤明确命中黑名单的漫画，保留服务端原始顺序。 */
fun List<ComicSummary>.withoutBlockedThemes(blockedThemes: List<Theme>): List<ComicSummary> =
    if (blockedThemes.isEmpty()) this
    else filterNot { comic -> comic.theme.orEmpty().any { blockedThemes.blocks(it) } }
