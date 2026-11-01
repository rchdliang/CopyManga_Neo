package com.rchdliang.copymanga_neo.multiplatform.platform

import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopNovelBriefTest {
    private val platform = DesktopPlatformHost()

    @Test
    fun plainChineseSynopsisRemainsVisible() {
        val brief = "從無情的絕路轉生重新開始？少年與少女的故事。"
        assertEquals(brief, platform.plainTextFromHtml(brief))
    }

    @Test
    fun htmlSynopsisKeepsParagraphsLineBreaksAndEntities() {
        assertEquals(
            "第一段 & 序章\n下一行\n\n第二段 <冒險>。",
            platform.plainTextFromHtml("<p>第一段 &amp; 序章<br>下一行</p><p>第二段 &lt;冒險&gt;。</p>").trim()
        )
    }

    @Test
    fun emptySynopsisRemainsEmpty() {
        assertEquals("", platform.plainTextFromHtml(""))
    }
}
