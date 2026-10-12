package com.rchdliang.copymanga_neo.data.local

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.rchdliang.copymanga_neo.domain.model.ReaderPreferences

internal object ComicPreferencesCodec {
    fun encode(value: ReaderPreferences): String {
        require(value == value.normalized()) { "逐漫画阅读设置无效" }
        return JsonObject().apply {
            addProperty("direction", value.readingDirection)
            addProperty("scale", value.pageScale)
            addProperty("orientation", value.screenOrientation)
        }.toString()
    }

    fun decode(raw: String): ReaderPreferences {
        val json = JsonParser.parseString(raw).asJsonObject
        fun string(key: String): String {
            val value = json[key]
            require(value?.isJsonPrimitive == true && value.asJsonPrimitive.isString) { "逐漫画阅读设置字段无效" }
            return value.asString
        }
        return ReaderPreferences(string("direction"), string("scale"), string("orientation")).also {
            require(it == it.normalized()) { "逐漫画阅读设置无效" }
        }
    }
}
