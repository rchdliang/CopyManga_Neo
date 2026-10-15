package com.rchdliang.copymanga_neo.domain.model

/** 网页分享目标；路径段和参数在生成链接时分别编码。 */
@kotlinx.serialization.Serializable
data class ShareTarget(
    val title: String,
    val pathSegments: List<String>,
    val query: Map<String, String> = emptyMap()
) {
    companion object {
        fun comic(pathWord: String, title: String) =
            ShareTarget(title, listOf("comic", pathWord))

        fun novel(pathWord: String, title: String) =
            ShareTarget(title, listOf("book", pathWord))

        fun chapter(comic: String, chapter: String, title: String) =
            ShareTarget(title, listOf("comic", comic, "chapter", chapter))

        fun topic(pathWord: String, title: String) =
            ShareTarget(title, listOf("topic", pathWord))

        fun page(path: String, title: String) = ShareTarget(title, listOf(path))

        fun rank(audience: String, period: String) = ShareTarget(
            "${if (audience == "female") "女榜" else "男榜"} · ${
                when (period) {
                    "week" -> "周榜"
                    "month" -> "月榜"
                    "total" -> "总榜"
                    else -> "日榜"
                }
            }",
            listOf("rank"), mapOf("type" to audience, "table" to period)
        )

        fun comics(
            title: String, theme: String? = null, top: String? = null,
            ordering: String = "-datetime_updated"
        ) = ShareTarget(title, listOf("comics"), buildMap {
            theme?.takeIf { it.isNotBlank() }?.let { put("theme", it) }
            when (top) {
                "japan" -> put("region", "0")
                "korea" -> put("region", "1")
                "west" -> put("region", "2")
                "finish" -> put("status", "1")
            }
            if (ordering != "-datetime_updated") put("ordering", ordering)
        })

        fun search(query: String, type: QueryType?) = ShareTarget(
            "搜索：$query", listOf("search"),
            mapOf("q" to query, "q_type" to type?.value.orEmpty())
        )

        /** H5 搜索页只声明内容类型；关键词及查询类型保留在分享文字中。 */
        fun novelSearch(query: String, type: QueryType?): ShareTarget {
            require(query.isNotBlank() && type != QueryType.LOCAL)
            val typeLabel = when (type) {
                QueryType.NAME -> "名称"
                QueryType.AUTHOR -> "作者"
                else -> "全部"
            }
            return ShareTarget(
                "轻小说搜索（$typeLabel）：$query", listOf("h5", "searchContent"),
                mapOf("type" to "novel")
            )
        }
    }
}
