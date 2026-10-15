package com.rchdliang.copymanga_neo.data.novel.portable

import com.rchdliang.copymanga_neo.domain.model.RequestMode
import com.rchdliang.copymanga_neo.domain.reading.NovelTextDocument
import kotlinx.serialization.json.*
import kotlin.test.*

class NovelImagePositionDebugJsonTest {
    @Test fun capturesOnlyNonzeroExactCoordinatesAndPreservesRawFields() {
        val result=Json.parseToJsonElement("""{
          "book":{"name":"Book"},"volume":{"id":"volume","name":"One","contents":[
            {"content_type":2,"start_lines":"1.0","end_lines":2e0,"content":"https://cdn.example/image","extra":null},
            {"content_type":2,"start_lines":0,"end_lines":0},
            {"content_type":2,"start_lines":-1,"end_lines":2},
            {"content_type":2,"start_lines":1.5,"end_lines":2},
            {"content_type":2,"start_lines":2147483648,"end_lines":2},
            {"content_type":1,"start_lines":1,"end_lines":2}
          ]}}
        """).jsonObject
        val sample=assertNotNull(NovelImagePositionDebugJson.sample(RequestMode.WEB,"book-path","fallback",result))
        val payload=Json.parseToJsonElement(sample.json).jsonObject
        assertEquals("WEB",payload["request_mode"]?.jsonPrimitive?.content)
        assertEquals("book-path",payload["book"]?.jsonObject?.get("path_word")?.jsonPrimitive?.content)
        assertEquals(JsonArray(listOf(result["volume"]!!.jsonObject["contents"]!!.jsonArray.first())),payload["contents"])
        assertEquals(NovelTextDocument.sha256(sample.json),sample.id)
        assertEquals(sample,NovelImagePositionDebugJson.sample(RequestMode.WEB,"book-path","fallback",result))
    }

    @Test fun absentOrZeroCoordinatesDoNotProduceSample() {
        assertNull(NovelImagePositionDebugJson.sample(RequestMode.OFFICIAL_APP,"book","volume",buildJsonObject {}))
        val result=Json.parseToJsonElement("""{"volume":{"contents":[{"content_type":2,"start_lines":0,"end_lines":0}]}}""").jsonObject
        assertNull(NovelImagePositionDebugJson.sample(RequestMode.OFFICIAL_APP,"book","volume",result))
    }
}
