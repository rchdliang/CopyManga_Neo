package com.rchdliang.copymanga_neo.domain.model

/** 漫画筛选结果；分页使用服务端游标，避免短页导致提前结束。 */
@kotlinx.serialization.Serializable
data class ComicPage(
    val comics: List<ComicSummary>,
    val nextOffset: Int?,
    val total: Int? = null
)
