package com.rchdliang.copymanga_neo.data.remote.portable

import com.rchdliang.copymanga_neo.data.remote.portable.dto.ComicDetailResponse
import com.rchdliang.copymanga_neo.data.repository.portable.toInfo
import kotlinx.serialization.json.*
import kotlin.test.*

class ComicDetailDtoTest {
    @Test fun nullOrMissingAliasDoesNotPreventReadingComicDetails() {
        val raw = """
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
        for (response in listOf(raw, raw.replace("\"alias\":null,", ""), raw.replace("\"alias\":null", "\"alias\":\"别名\""))) {
            val detail = Json.decodeFromString(ComicDetailResponse.serializer(), response).results.comicDetail.toInfo()
            assertEquals("19。", detail.name)
            assertEquals(if ("别名" in response) "别名" else "", detail.comicAlias)
        }
    }
}
