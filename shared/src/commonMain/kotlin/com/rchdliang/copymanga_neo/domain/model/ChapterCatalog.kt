package com.rchdliang.copymanga_neo.domain.model

/** 一次返回全部汉化组的完整目录；列表顺序沿用服务端，不按章节名称猜测排序。 */
@kotlinx.serialization.Serializable
data class ChapterCatalog(
    val groups: List<ChapterGroup>,
    val chaptersByGroup: Map<String, List<ChapterInfo>>
) {
    fun findGroup(chapterUuid: String?): String? {
        if (chapterUuid.isNullOrBlank()) return null
        return chaptersByGroup.entries.singleOrNull { (_, chapters) ->
            chapters.any { it.uuid == chapterUuid }
        }?.key
    }
}
