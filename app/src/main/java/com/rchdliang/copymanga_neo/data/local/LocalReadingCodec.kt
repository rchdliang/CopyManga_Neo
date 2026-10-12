package com.rchdliang.copymanga_neo.data.local

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.rchdliang.copymanga_neo.domain.model.LocalReadingRecord
import com.rchdliang.copymanga_neo.domain.model.LocalReadingSnapshot

/** Explicit fields: backup and installed release builds share a stable, validated schema. */
object LocalReadingCodec {
    const val MAX_BYTES = 8 * 1024 * 1024
    fun encodeRecord(r: LocalReadingRecord) = JsonObject().apply {
        addProperty("owner", r.owner); addProperty("kind", r.kind); addProperty("path", r.path)
        addProperty("chapter", r.chapter); addProperty("name", r.name); addProperty(
        "group",
        r.group
    )
        addProperty("title", r.title); addProperty("page", r.page); addProperty(
        "fraction",
        r.fraction
    )
        addProperty("cover", r.cover); addProperty("authors", r.authors)
        addProperty("section", r.section); addProperty("line", r.line); addProperty(
        "character",
        r.character
    )
        addProperty("completed", r.completed); addProperty("at", r.updatedAt)
    }

    fun decodeRecord(o: JsonObject): LocalReadingRecord {
        fun string(k: String): String {
            val value = o[k]
            require(value?.isJsonPrimitive == true && value.asJsonPrimitive.isString) { "阅读备份字段无效：$k" }
            return value.asString
        }

        fun integer(k: String): Int {
            val value = o[k]
            require(value?.isJsonPrimitive == true && value.asJsonPrimitive.isNumber)
            return value.asString.toIntOrNull() ?: error("阅读位置必须为整数")
        }

        val at = o["at"]
        require(at?.isJsonPrimitive == true && at.asJsonPrimitive.isNumber)
        val timestamp = at.asString.toLongOrNull() ?: error("阅读时间无效")
        val fraction = o["fraction"]
        require(fraction?.isJsonPrimitive == true && fraction.asJsonPrimitive.isNumber)
        val complete = o["completed"]
        require(complete?.isJsonPrimitive == true && complete.asJsonPrimitive.isBoolean)
        val r = LocalReadingRecord(
            string("owner"), string("kind"), string("path"), string("chapter"),
            string("name"), string("group"), string("title"), integer("page"), fraction.asDouble,
            string("section"), integer("line"), integer("character"), complete.asBoolean, timestamp,
            cover = string("cover"), authors = string("authors")
        )
        require(r.owner.isNotBlank() && r.path.isNotBlank() && r.chapter.isNotBlank())
        require(r.kind in setOf("comic", "novel") && r.page >= 0 && r.line >= 0 && r.character >= 0)
        require(r.fraction.isFinite() && r.fraction in 0.0..1.0 && r.updatedAt in 0L..253402300799999L)
        require(
            listOf(
                r.owner,
                r.path,
                r.chapter,
                r.name,
                r.title,
                r.section,
                r.group,
                r.cover,
                r.authors
            ).all { it.length <= 8192 })
        return r
    }

    fun encode(s: LocalReadingSnapshot): String = JsonObject().apply {
        addProperty("version", 1)
        add("records", JsonArray().apply { s.records.forEach { add(encodeRecord(it)) } })
        add("bookmarks", JsonArray().apply { s.bookmarks.forEach { add(encodeRecord(it)) } })
        add(
            "deleted",
            JsonObject().apply { s.deleted.forEach { (key, at) -> addProperty(key, at) } })
        add(
            "clearedOwners",
            JsonObject().apply { s.clearedOwners.forEach { (key, at) -> addProperty(key, at) } })
        add(
            "deletedBooks",
            JsonObject().apply { s.deletedBooks.forEach { (key, at) -> addProperty(key, at) } })
    }.toString()

    fun decode(raw: String): LocalReadingSnapshot {
        require(raw.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "阅读备份过大" }
        val o = JsonParser.parseString(raw).asJsonObject
        require(o["version"]?.asJsonPrimitive?.isNumber == true && o["version"].asString == "1") { "不支持的阅读数据版本" }
        val records = o.getAsJsonArray("records").map { decodeRecord(it.asJsonObject) }
        val marks = o.getAsJsonArray("bookmarks").map { decodeRecord(it.asJsonObject) }
        require(records.size + marks.size <= 50000)
        require(records.map { it.key }.distinct().size == records.size)
        require(marks.map { listOf(it.key, it.page, it.section, it.line, it.character) }
            .distinct().size == marks.size)
        return LocalReadingSnapshot(
            records, marks,
            timestamps(o, "deleted"), timestamps(o, "clearedOwners"), timestamps(o, "deletedBooks")
        )
    }

    private fun timestamps(o: JsonObject, key: String) =
        o.getAsJsonObject(key).entrySet().associate {
            require(it.value.isJsonPrimitive && it.value.asJsonPrimitive.isNumber)
            val at = it.value.asString.toLongOrNull() ?: error("阅读删除标记无效")
            require(at in 0L..253402300799999L)
            it.key to at
        }
}
