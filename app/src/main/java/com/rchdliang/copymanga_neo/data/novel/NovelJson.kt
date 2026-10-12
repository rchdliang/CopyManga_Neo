package com.rchdliang.copymanga_neo.data.novel

import com.google.gson.JsonObject
import com.rchdliang.copymanga_neo.domain.model.NovelBook
import com.rchdliang.copymanga_neo.domain.model.NovelSection
import com.rchdliang.copymanga_neo.domain.model.NovelShelfEntry
import com.rchdliang.copymanga_neo.domain.model.NovelShelfPage
import com.rchdliang.copymanga_neo.domain.model.NovelTag
import com.rchdliang.copymanga_neo.domain.model.NovelVolume
import com.rchdliang.copymanga_neo.domain.reading.NovelTextDocument

internal object NovelJson {
    fun text(o: JsonObject, key: String, fallback: String = "") =
        o[key]?.takeUnless { it.isJsonNull }?.asString ?: fallback

    fun tags(o: JsonObject, key: String) = o.getAsJsonArray(key)
        ?.map { e -> e.asJsonObject.let { NovelTag(text(it, "name"), text(it, "path_word")) } }
        .orEmpty()

    fun book(o: JsonObject) = NovelBook(
        text(o, "path_word"),
        text(o, "name"),
        text(o, "cover"),
        tags(o, "author"),
        tags(o, "theme"),
        text(o, "brief"),
        o["status"]?.takeIf { it.isJsonObject }?.asJsonObject?.let { text(it, "display") }
            .orEmpty(),
        text(o, "datetime_updated"),
        uuid = text(o, "uuid"),
        popular = text(o, "popular").toIntOrNull()
    )

    fun volume(o: JsonObject) = NovelVolume(
        text(o, "id"), text(o, "name"), o["index"]?.asInt ?: 0,
        text(o, "prev").takeIf { it.isNotBlank() }, text(o, "next").takeIf { it.isNotBlank() })

    /** 书架外层 uuid 是收藏记录，不能用内层 book.uuid 代替。 */
    fun shelfPage(result: JsonObject, offset: Int): NovelShelfPage {
        require(result["offset"]?.asInt == offset) { "小说书架分页位置无效" }
        val rows = requireNotNull(result.getAsJsonArray("list")) { "小说书架缺少列表" }
        val entries = rows.map { element ->
            val row = element.asJsonObject
            val collectId = requireNotNull(row["uuid"]) { "小说收藏记录缺少 ID" }.asInt
            require(collectId > 0) { "小说收藏记录 ID 无效" }
            NovelShelfEntry(collectId, book(requireNotNull(row.getAsJsonObject("book"))))
        }
        return NovelShelfPage(entries, result["total"].asInt, offset, result["limit"].asInt)
    }

    fun sections(o: JsonObject, doc: NovelTextDocument): List<NovelSection> =
        o.getAsJsonArray("contents").map { e ->
            val s = e.asJsonObject
            val type = s["content_type"].asInt
            require(type == 1 || type == 2) { "未知的官方目录类型" }
            val start = s["start_lines"].asInt
            val end = s["end_lines"].asInt
            if (type == 1) doc.validateRange(start, end)
            val image = text(s, "content").takeIf { it.isNotBlank() }
            if (type == 2) require(image != null) { "插图地址缺失" }
            NovelSection(
                NovelTextDocument.sectionKey(text(o, "id"), type, start, end, image),
                text(s, "name"),
                type,
                start,
                end,
                image
            )
        }
}
