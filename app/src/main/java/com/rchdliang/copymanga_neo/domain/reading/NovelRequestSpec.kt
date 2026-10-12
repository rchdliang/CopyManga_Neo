package com.rchdliang.copymanga_neo.domain.reading

/** 仅发送已确认的官方 Web/App 查询字段，保留各端分页大小。 */
object NovelRequestSpec {
    fun browse(
        app: Boolean,
        offset: Int,
        ordering: String,
        theme: String,
        author: String
    ): Map<String, String> {
        require(ordering == "-datetime_updated" || ordering == "-popular") { "Unverified ordering" }
        return page(app, offset, if (app) 18 else 21).apply {
            put("ordering", ordering)
            if (!app) put("free_type", "1")
            if (theme.isNotEmpty()) put("theme", theme)
            if (author.isNotEmpty()) put("author", author)
        }
    }

    fun search(app: Boolean, offset: Int, query: String, type: String): Map<String, String> {
        require(type == "" || type == "name" || type == "author") { "Unverified search type" }
        return page(app, offset, if (app) 18 else 20).apply {
            put("q", query)
            put("q_type", type)
        }
    }

    private fun page(app: Boolean, offset: Int, limit: Int): MutableMap<String, String> {
        require(offset >= 0) { "Negative pagination offset" }
        return linkedMapOf<String, String>().apply {
            if (app) put("platform", "3")
            put("limit", limit.toString())
            put("offset", offset.toString())
        }
    }
}
