package com.rchdliang.copymanga_neo.data.remote.portable.dto

import com.rchdliang.copymanga_neo.domain.model.ComicOriginalWork
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.json.*

/** 兼容旧名称列表，保留对象中的原作标识；不根据显示名称猜测 path_word。 */
object ComicOriginalWorkListSerializer : JsonTransformingSerializer<List<ComicOriginalWork>?>(
    ListSerializer(ComicOriginalWork.serializer()).nullable
) {
    override fun transformDeserialize(element: JsonElement): JsonElement {
        if (element == JsonNull) return JsonNull
        val items = element as? JsonArray ?: throw SerializationException("漫画原作信息应为数组")
        return JsonArray(items.mapNotNull { item ->
            if (item == JsonNull) return@mapNotNull null
            val name = (if (item is JsonObject) item["name"] else item) as? JsonPrimitive
            if (name == null || !name.isString) throw SerializationException("漫画原作信息缺少字符串名称")
            val path = (item as? JsonObject)?.let { it["path_word"] ?: it["pathWord"] } as? JsonPrimitive
            buildJsonObject {
                put("name", name)
                if (path != null && path.isString && path.content.isNotBlank()) put("pathWord", path)
            }
        })
    }
}
