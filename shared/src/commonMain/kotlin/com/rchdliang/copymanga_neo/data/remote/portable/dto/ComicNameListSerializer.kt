package com.rchdliang.copymanga_neo.data.remote.portable.dto

import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.*

/** 详情关联信息可能返回名称字符串，也可能返回包含 name/path_word 的对象。仅归一化这些名称列表。 */
object ComicNameListSerializer : JsonTransformingSerializer<List<String>?>(
    ListSerializer(String.serializer()).nullable
) {
    override fun transformDeserialize(element: JsonElement): JsonElement {
        if (element == JsonNull) return JsonNull
        val items = element as? JsonArray
            ?: throw SerializationException("漫画关联信息应为数组")
        return JsonArray(items.mapNotNull { item ->
            if (item == JsonNull) return@mapNotNull null
            val name = (if (item is JsonObject) item["name"] else item) as? JsonPrimitive
            if (name == null || !name.isString) throw SerializationException("漫画关联信息缺少字符串名称")
            name
        })
    }
}
