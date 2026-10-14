package com.rchdliang.copymanga_neo.domain.reading

import com.google.gson.JsonParser
import com.rchdliang.copymanga_neo.data.novel.NovelJson
import com.rchdliang.copymanga_neo.domain.model.NovelReaderPreferences
import com.rchdliang.copymanga_neo.domain.model.NovelSection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 插图位置、原始行号与字符锚点是阅读器跨布局恢复的协议边界。 */
class NovelReaderDocumentTest {
    private fun image(start: Int, end: Int, key: String = "image") =
        NovelSection(key, key, 2, start, end, "https://example.com/$key.jpg")

    private fun text() = NovelTextDocument.decode("甲\n乙\n中文😀".toByteArray(), "UTF-8")

    @Test
    fun imagePositionFollowsOneZeroAndBothNonzeroRules() {
        assertNull(inlineImageLine(image(0, 0), 10))
        assertEquals(3, inlineImageLine(image(0, 3), 10))
        assertEquals(3, inlineImageLine(image(3, 0), 10))
        assertEquals(2, inlineImageLine(image(2, 4), 10))
        assertEquals(4, inlineImageLine(image(4, 2), 10))
        assertEquals(2, inlineImageLine(image(2, 999), 10))
    }

    @Test
    fun invalidPositionsKeepImageEntranceWithoutInserting() {
        val json = JsonParser.parseString(
            """
            {"id":"volume","contents":[
              {"name":"a","content_type":2,"start_lines":null,"end_lines":2,"content":"https://example.com/a.jpg"},
              {"name":"b","content_type":2,"start_lines":2,"end_lines":null,"content":"https://example.com/b.jpg"},
              {"name":"c","content_type":2,"start_lines":-1,"end_lines":2,"content":"https://example.com/c.jpg"},
              {"name":"d","content_type":2,"start_lines":2,"end_lines":-1,"content":"https://example.com/d.jpg"},
              {"name":"e","content_type":2,"start_lines":"","end_lines":2,"content":"https://example.com/e.jpg"},
              {"name":"f","content_type":2,"start_lines":1.5,"end_lines":2,"content":"https://example.com/f.jpg"}
            ]}
        """
        ).asJsonObject
        val source = text()
        val document = NovelReaderDocument(source, NovelJson.sections(json, source))
        assertEquals(6, document.images.size)
        assertTrue(document.entries.none { it is NovelReaderEntry.Image })
        assertEquals(
            source.lines,
            document.entries.filterIsInstance<NovelReaderEntry.Text>().map { it.text })
    }

    @Test
    fun insertedImagesDoNotReplaceTextOrChangeRawLineIndices() {
        val source = text()
        val document = NovelReaderDocument(
            source,
            listOf(image(2, 0, "a"), image(0, 2, "b"), image(3, 0, "end"))
        )
        assertEquals(
            listOf(0, 1, 2),
            document.entries.filterIsInstance<NovelReaderEntry.Text>().map { it.line })
        assertEquals(
            source.lines,
            document.entries.filterIsInstance<NovelReaderEntry.Text>().map { it.text })
        assertEquals(0, document.textItem(0))
        assertEquals(1, document.textItem(1))
        assertEquals(4, document.textItem(2))
        assertEquals(2, document.imageItem("a"))
        assertEquals(3, document.imageItem("b"))
        assertEquals(5, document.imageItem("end"))
        assertEquals(
            listOf("a", "b", "end"),
            document.entries.filterIsInstance<NovelReaderEntry.Image>().map { it.section.key })
    }

    @Test
    fun outOfRangeImagesRemainAvailableOnlyThroughEntrance() {
        val document = NovelReaderDocument(
            text(),
            listOf(image(0, 4), image(0, 0, "unknown"), image(-1, 2, "negative"))
        )
        assertEquals(3, document.images.size)
        assertEquals(3, document.entries.size)
        assertNull(document.imageItem("image"))
        assertNull(inlineImageLine(image(1, 0).copy(image = null), 3))
        assertNull(inlineImageLine(image(1, 0).copy(type = 1), 3))
    }

    @Test
    fun duplicateImageIdentityDoesNotCreateDuplicateLazyKeys() {
        val document = NovelReaderDocument(text(), listOf(image(1, 0), image(1, 0)))
        assertEquals(1, document.images.size)
        assertEquals(document.entries.size, document.entries.map { it.key }.distinct().size)
    }

    @Test
    fun pageRestoreUsesCharacterInsteadOfOnlyParagraphStart() {
        val entry = NovelReaderEntry.Text(2, "abcdefghijkl")
        val pages = listOf(
            NovelReaderPage(listOf(NovelPageBlock(entry, 0, "abcd", 20))),
            NovelReaderPage(listOf(NovelPageBlock(entry, 4, "efgh", 20))),
            NovelReaderPage(listOf(NovelPageBlock(entry, 8, "ijkl", 20)))
        )
        assertEquals(0, novelPageFor(pages, 2, 0, "chapter"))
        assertEquals(1, novelPageFor(pages, 2, 4, ""))
        assertEquals(2, novelPageFor(pages, 2, 10, ""))
    }

    @Test
    fun inlineImageAnchorRestoresItsPageWithoutConfusingAdjacentText() {
        val image = NovelReaderEntry.Image(image(2, 0), 2)
        val text = NovelReaderEntry.Text(2, "正文")
        val pages = listOf(
            NovelReaderPage(listOf(NovelPageBlock(image, heightPx = 100))),
            NovelReaderPage(listOf(NovelPageBlock(text, text = "正文", heightPx = 20)))
        )
        assertEquals(0, novelPageFor(pages, 2, 0, "image"))
        assertEquals(1, novelPageFor(pages, 2, 0, "chapter"))
    }

    @Test
    fun portraitIllustrationInLandscapeCannotBePannedIntoEmptyHorizontalSpace() {
        val limits = novelImagePanLimits(1280, 600, 800, 1200, 2f)
        assertEquals(0f, limits.first, .01f)
        assertEquals(300f, limits.second, .01f)
        assertEquals(0f to 0f, novelImagePanLimits(1280, 600, 800, 1200, 1f))
    }

    @Test
    fun zoomPanLimitsUseFittedPictureInsteadOfTheWholeViewport() {
        val limits = novelImagePanLimits(412, 620, 800, 1200, 2f)
        assertEquals(206f, limits.first, .01f)
        assertEquals(308f, limits.second, .01f)
        assertEquals(0f to 0f, novelImagePanLimits(0, 0, 800, 1200, 2f))
    }

    @Test
    fun settingsLoadedFromInvalidStorageAreNormalized() {
        val settings = NovelReaderPreferences(
            fontSize = Float.NaN, lineHeight = Float.POSITIVE_INFINITY,
            margin = -1f, mode = "unknown", columns = "unknown"
        ).normalized()
        assertEquals(18f, settings.fontSize, 0f)
        assertEquals(1.65f, settings.lineHeight, 0f)
        assertEquals(8f, settings.margin, 0f)
        assertEquals("SCROLL", settings.mode)
        assertEquals("AUTO", settings.columns)
    }
}
