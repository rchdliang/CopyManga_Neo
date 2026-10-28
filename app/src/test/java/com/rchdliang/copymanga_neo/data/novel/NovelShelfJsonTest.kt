package com.rchdliang.copymanga_neo.data.novel

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Test

class NovelShelfJsonTest {
    private fun entry(extra: String) = NovelJson.shelfPage(
        JsonParser.parseString("""
            {"offset":0,"limit":20,"total":1,"list":[{"uuid":42,"book":{
              "uuid":"book-id","path_word":"book","name":"小说","cover":""$extra
            }}]}
        """.trimIndent()).asJsonObject, 0
    ).entries.single()

    @Test fun readsLatestVolumeFromBookWithoutConfusingCollectionId() {
        val entry = entry("""
            , "last_chapter_id":"volume-2", "last_chapter_name":"第二卷"
        """.trimIndent())
        assertEquals(42, entry.collectId)
        assertEquals("book-id", entry.book.uuid)
        assertEquals("volume-2", entry.lastChapterId)
        assertEquals("第二卷", entry.lastChapterName)
    }

    @Test fun absentAndNullLatestVolumeRemainUnknown() {
        listOf("", """, "last_chapter_id":null, "last_chapter_name":null""").forEach {
            val entry = entry(it)
            assertEquals("", entry.lastChapterId)
            assertEquals("", entry.lastChapterName)
        }
    }
}
