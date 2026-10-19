package com.rchdliang.copymanga_neo.data.remote.dto

import com.google.gson.*
import com.rchdliang.copymanga_neo.domain.model.ComicOriginalWork
import java.lang.reflect.Type

/** 兼容旧名称列表，并保留服务端给出的原作筛选标识。 */
class ComicOriginalWorkListDeserializer : JsonDeserializer<List<ComicOriginalWork>> {
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): List<ComicOriginalWork> {
        if (!json.isJsonArray) throw JsonParseException("漫画原作信息应为数组")
        return json.asJsonArray.mapNotNull { item ->
            if (item.isJsonNull) return@mapNotNull null
            val name = if (item.isJsonObject) item.asJsonObject.get("name") else item
            if (name == null || !name.isJsonPrimitive || !name.asJsonPrimitive.isString)
                throw JsonParseException("漫画原作信息缺少字符串名称")
            val path = if (item.isJsonObject) item.asJsonObject.get("path_word") else null
            ComicOriginalWork(name.asString, path?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
                ?.asString?.takeIf(String::isNotBlank))
        }
    }
}
