package com.rchdliang.copymanga_neo.data.novel

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.rchdliang.copymanga_neo.domain.model.RequestMode
import com.rchdliang.copymanga_neo.domain.reading.NovelImagePositionDebugSample
import com.rchdliang.copymanga_neo.domain.reading.NovelTextDocument

/** 保留命中条目的所有原始字段；非零行号是待核实的线索，不代表已确认正文位置。 */
internal object NovelImagePositionDebugJson {
    fun sample(
        mode: RequestMode,
        path: String,
        volumeId: String,
        result: JsonObject
    ): NovelImagePositionDebugSample? {
        val volume = result.getAsJsonObject("volume") ?: return null
        val images = JsonArray()
        volume.getAsJsonArray("contents")?.forEach { entry ->
            if (!entry.isJsonObject) return@forEach
            val section = entry.asJsonObject
            if (integer(section, "content_type") != 2) return@forEach
            val start = integer(section, "start_lines") ?: return@forEach
            val end = integer(section, "end_lines") ?: return@forEach
            if (start >= 0 && end >= 0 && (start > 0 || end > 0)) images.add(section.deepCopy())
        }
        if (images.size() == 0) return null
        val book = result.getAsJsonObject("book")
        val payload = JsonObject().apply {
            addProperty("request_mode", mode.name)
            add("book", JsonObject().apply {
                addProperty("path_word", path)
                addProperty("name", book?.let { NovelJson.text(it, "name") }.orEmpty())
            })
            add("volume", JsonObject().apply {
                addProperty("id", NovelJson.text(volume, "id", volumeId))
                addProperty("name", NovelJson.text(volume, "name"))
            })
            add("contents", images)
        }
        val json = GsonBuilder().serializeNulls().disableHtmlEscaping().setPrettyPrinting()
            .create().toJson(payload)
        return NovelImagePositionDebugSample(NovelTextDocument.sha256(json), json)
    }

    /** 不将小数或损坏字段截断为行号；兼容 JSON 数字和整数字符串。 */
    private fun integer(value: JsonObject, key: String): Int? =
        runCatching {
            value[key]?.takeUnless { it.isJsonNull }?.asBigDecimal?.intValueExact()
        }.getOrNull()
}
