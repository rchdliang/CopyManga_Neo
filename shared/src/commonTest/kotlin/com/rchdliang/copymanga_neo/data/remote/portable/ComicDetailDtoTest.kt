package com.rchdliang.copymanga_neo.data.remote.portable

import com.rchdliang.copymanga_neo.data.remote.portable.dto.ComicDetailResponse
import com.rchdliang.copymanga_neo.data.repository.portable.toInfo
import kotlinx.serialization.json.*
import kotlin.test.*

class ComicDetailDtoTest {
    private val raw = """
            {"code":200,"message":"success","results":{
              "is_banned":false,"is_lock":false,"is_login":true,"is_mobile_bind":false,"is_vip":false,
              "popular":0,"groups":{},"comic":{
                "uuid":"id","b_404":false,"b_hidden":false,"ban":0,"name":"19。",
                "alias":null,"path_word":"19du","close_comment":false,"close_roast":false,
                "free_type":{"display":"","value":0},"restrict":{"display":"","value":0},
                "reclass":{"display":"","value":0},"region":{"display":"","value":0},
                "status":{"display":"连载中","value":0}
              }}}
        """.trimIndent()

    @Test fun nullOrMissingAliasDoesNotPreventReadingComicDetails() {
        for (response in listOf(raw, raw.replace("\"alias\":null,", ""), raw.replace("\"alias\":null", "\"alias\":\"别名\""))) {
            val detail = Json.decodeFromString(ComicDetailResponse.serializer(), response).results.comicDetail.toInfo()
            assertEquals("19。", detail.name)
            assertEquals(if ("别名" in response) "别名" else "", detail.comicAlias)
        }
    }

    private fun comicWith(field: String, value: String) = Json.decodeFromString(
        ComicDetailResponse.serializer(), raw.replace("\"alias\":null,", "\"alias\":null,\"$field\":$value,")
    ).results.comicDetail.toInfo()

    @Test fun objectParodiesFromScreenshotPreserveTheirDisplayNames() {
        val comic = comicWith("parodies", """[{"name":"Love Live!","path_word":"love_live"}]""")
        assertEquals(listOf("Love Live!"), comic.parodies?.map { it.name })
        assertEquals("love_live", comic.parodies?.single()?.pathWord)
        val legacy = comicWith("parodies", """["Love Live!"]""")
        assertEquals("Love Live!", legacy.parodies?.single()?.name)
        assertNull(legacy.parodies?.single()?.pathWord)
        assertEquals("19。", comic.name)
    }

    @Test fun relatedNamesAcceptStringsObjectsEmptyNullAndMissingLists() {
        val cases = listOf(
            """["旧名称"]""" to listOf("旧名称"),
            """[{"name":"新名称","path_word":"new"}]""" to listOf("新名称"),
            """["旧名称",null,{"name":"新名称","path_word":"new"}]""" to listOf("旧名称", "新名称"),
            "[]" to emptyList(), "null" to null
        )
        for (field in listOf("parodies", "females", "males", "clubs")) {
            for ((json, expected) in cases) {
                val comic = comicWith(field, json)
                val actual = when (field) {
                    "parodies" -> comic.parodies?.map { it.name }
                    "females" -> comic.females
                    "males" -> comic.males
                    else -> comic.clubs
                }
                assertEquals(expected, actual, "$field: $json")
            }
        }
        val missing = Json.decodeFromString(ComicDetailResponse.serializer(), raw).results.comicDetail
        assertNull(missing.parodies)
        assertNull(missing.females)
        assertNull(missing.males)
        assertNull(missing.clubs)
    }

    @Test fun malformedNamesAreNotCoercedIntoVisibleText() {
        for (value in listOf("[42]", """[{"name":false}]""", """[{"path_word":"missing"}]""", "{}")) {
            assertFailsWith<kotlinx.serialization.SerializationException> { comicWith("parodies", value) }
        }
    }
}
