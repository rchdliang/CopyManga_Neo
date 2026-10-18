package com.rchdliang.copymanga_neo.multiplatform.ui.detail

/** 两个阅读入口共用目标；只有本机确认停在章末时，才前进到同组的下一章。 */
internal fun resolveStartChapter(state: DetailUiState): StartChapterInfo? {
    val browse = state.browse
    val uuid = browse?.chapterUuid?.takeIf { it.isNotBlank() }
    if (uuid != null) {
        val group = state.browseGroup ?: state.selectedGroup.ifBlank { "default" }
        val completeGroup = state.chapterCatalog?.chaptersByGroup?.get(group)
        val chapters = (completeGroup ?: state.chapters.filter { (it.groupPathWord ?: "default") == group })
            .sortedBy { it.index }
        val current = chapters.firstOrNull { it.uuid == uuid }
        val position = state.resumePosition?.takeIf { it.kind == "comic" && it.chapter == uuid && it.group == group }
        if (current != null && position?.atChapterEnd == true) {
            // 本地下载列表可能缺章，只能按明确关系前进，不能按数组位置跳过缺失章节。
            val next = if (!current.nextChapter.isNullOrBlank())
                chapters.firstOrNull { it.uuid == current.nextChapter }
            else if (completeGroup != null || (state.isChapterCatalogReady && !state.hasLocalCache && !state.isLoadingChapters && !state.isLoading))
                chapters.getOrNull(chapters.indexOf(current) + 1)
            else null
            if (next != null && next.uuid != uuid) return StartChapterInfo(
                next.uuid, next.name, group, isContinue = true, initialPage = 0)
        }
        return StartChapterInfo(uuid, current?.name ?: browse.chapterName?.takeIf { it.isNotBlank() } ?: "上次阅读章节",
            group, isContinue = true)
    }
    if (state.isProgressLoading || state.isLoading || state.isLoadingChapters || !state.isChapterCatalogReady) return null
    val first = state.chapters.minByOrNull { it.index } ?: return null
    return StartChapterInfo(first.uuid, first.name, first.groupPathWord ?: "default", isContinue = false)
}
