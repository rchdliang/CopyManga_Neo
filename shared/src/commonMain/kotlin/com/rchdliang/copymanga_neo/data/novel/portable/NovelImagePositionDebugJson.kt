package com.rchdliang.copymanga_neo.data.novel.portable

import com.rchdliang.copymanga_neo.domain.model.RequestMode
import com.rchdliang.copymanga_neo.domain.reading.NovelImagePositionDebugSample
import com.rchdliang.copymanga_neo.domain.reading.NovelTextDocument
import kotlinx.serialization.json.*

/** Raw nonzero image coordinates are diagnostic clues, not confirmed text positions. */
internal object NovelImagePositionDebugJson {

    fun sample(mode:RequestMode,path:String,volumeId:String,result:JsonObject):NovelImagePositionDebugSample? {
        val volume=result["volume"] as? JsonObject ?: return null
        val images=(volume["contents"] as? JsonArray).orEmpty().mapNotNull { entry ->
            val section=entry as? JsonObject ?: return@mapNotNull null
            fun integer(key:String)=(section[key] as? JsonPrimitive)?.contentOrNull?.let(PortableNovelJson::exactInt)
            if(integer("content_type")!=2)return@mapNotNull null
            val start=integer("start_lines") ?: return@mapNotNull null
            val end=integer("end_lines") ?: return@mapNotNull null
            section.takeIf { start>=0 && end>=0 && (start>0 || end>0) }
        }
        if(images.isEmpty())return null
        val book=result["book"] as? JsonObject
        val payload=buildJsonObject {
            put("request_mode",mode.name)
            put("book",buildJsonObject {
                put("path_word",path)
                put("name",book?.let { PortableNovelJson.text(it,"name") }.orEmpty())
            })
            put("volume",buildJsonObject {
                put("id",PortableNovelJson.text(volume,"id",volumeId))
                put("name",PortableNovelJson.text(volume,"name"))
            })
            put("contents",JsonArray(images))
        }
        val json=prettyRaw(payload)
        return NovelImagePositionDebugSample(NovelTextDocument.sha256(json),json)
    }

    // Json's encoder coerces unquoted numeric literals through Double (2e0 -> 2.0), unlike Gson's
    // lazy-number tree writer. Format the immutable tree without changing original field lexemes.
    private fun prettyRaw(value:JsonElement,depth:Int=0):String {
        val indent="  ".repeat(depth)
        val childIndent="$indent  "
        return when(value) {
            is JsonObject -> if(value.isEmpty())"{}" else value.entries.joinToString(",\n","{\n","\n$indent}") { (key,child)->
                childIndent+JsonPrimitive(key).toString()+": "+prettyRaw(child,depth+1)
            }
            is JsonArray -> if(value.isEmpty())"[]" else value.joinToString(",\n","[\n","\n$indent]") {
                childIndent+prettyRaw(it,depth+1)
            }
            else -> value.toString()
        }
    }
}
