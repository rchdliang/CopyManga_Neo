package com.rchdliang.copymanga_neo.domain.model

/** 漫画专题摘要。 */
data class ComicTopic(
    val pathWord: String,
    val title: String,
    val cover: String?,
    val brief: String?
) {
    /** 移除专题接口附加的缩略图后缀，保留查询参数和片段。 */
    val fullResolutionCover: String?
        get() = cover?.replace(Regex("\\.200x80\\.jpg(?=[?#]|$)"), "")
}

/** 专题分页。 */
data class ComicTopicPage(
    val topics: List<ComicTopic>,
    val nextOffset: Int?
)

/** 服务端筛选项。 */
data class ComicFilterOption(val name: String, val pathWord: String)

data class ComicFilterOptions(
    val themes: List<ComicFilterOption>,
    val tops: List<ComicFilterOption>,
    val orderings: List<ComicFilterOption>
)
