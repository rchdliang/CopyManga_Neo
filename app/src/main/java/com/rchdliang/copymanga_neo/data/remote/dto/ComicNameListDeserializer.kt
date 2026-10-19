package com.rchdliang.copymanga_neo.data.remote.dto

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import java.lang.reflect.Type

/** 详情关联信息兼容名称字符串和包含 name/path_word 的对象，空条目不影响其他资料。 */
class ComicNameListDeserializer : JsonDeserializer<List<String>> {
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): List<String> {
        if (!json.isJsonArray) throw JsonParseException("漫画关联信息应为数组")
        return json.asJsonArray.mapNotNull { item ->
            if (item.isJsonNull) return@mapNotNull null
            val name = if (item.isJsonObject) item.asJsonObject.get("name") else item
            if (name == null || !name.isJsonPrimitive || !name.asJsonPrimitive.isString)
                throw JsonParseException("漫画关联信息缺少字符串名称")
            name.asString
        }
    }
}
