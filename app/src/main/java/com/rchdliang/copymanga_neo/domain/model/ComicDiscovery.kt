package com.rchdliang.copymanga_neo.domain.model

/** 漫画专题摘要。 */
data class ComicTopic(
    val pathWord: String,
    val title: String,
    val cover: String?,
    val brief: String?
)

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
