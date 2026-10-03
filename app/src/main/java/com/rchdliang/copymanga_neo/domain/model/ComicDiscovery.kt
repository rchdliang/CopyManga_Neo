package com.rchdliang.copymanga_neo.domain.model

/** 漫画专题摘要。 */
data class ComicTopic(
    val pathWord: String,
    val title: String,
    val cover: String?,
    val brief: String?
) {
    /** 仅移除原图扩展名之后的缩略图后缀，保留原图扩展名、查询参数和片段。 */
    val fullResolutionCover: String?
        get() = cover?.replace(
            Regex(
                "(\\.(?:jpg|jpeg|png|webp))\\.\\d+x\\d+\\.(?:jpg|jpeg|png|webp)(?=[?#]|$)",
                RegexOption.IGNORE_CASE
            ),
            "$1"
        )
}

/** 专题分页。 */
data class ComicTopicPage(
    val topics: List<ComicTopic>,
    val nextOffset: Int?
)

/** 服务端筛选项。 */
data class ComicFilterOption(val name: String, val pathWord: String, val count: Int? = null) {
    val enabled: Boolean get() = count != 0
    val label: String get() = count?.let { "$name ($it)" } ?: name
}

data class ComicFilterOptions(
    val themes: List<ComicFilterOption>,
    val tops: List<ComicFilterOption>,
    val orderings: List<ComicFilterOption>
)
