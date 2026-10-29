package com.rchdliang.copymanga_neo.multiplatform.ui.novel

import java.io.File
import java.nio.ByteBuffer
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NovelFontNamesTest {
    @Test
    fun simplifiedChineseNameWinsOverEnglishAndTraditionalNames() {
        val font = fontFile(listOf(
            Record(1, 0x0409, "User Facing Family"), Record(1, 0x0404, "繁體名稱"),
            Record(1, 0x0804, "简体名称"), Record(2, 0x0804, "常规"),
            Record(6, 0x0409, "Internal-PostScript-Identifier")
        ))
        assertEquals("简体名称", novelFontDisplayName(novelFontNames(font, 0)))
    }

    @Test
    fun typographicNamesAndStylesAreUsedInsteadOfFileNames() {
        val font = fontFile(listOf(
            Record(1, 0x0409, "Roboto Black"), Record(2, 0x0409, "Regular"),
            Record(16, 0x0409, "Roboto"), Record(17, 0x0409, "Black")
        ))
        assertEquals("Roboto · Black", novelFontDisplayName(novelFontNames(font, 0)))
        for (style in listOf("Bold", "Light", "Italic", "ExtraCondensed Thin")) {
            assertEquals("Roboto · $style", novelFontDisplayName(listOf(
                NovelFontName("Roboto", "en", 16), NovelFontName(style, "en", 17)
            )))
        }
        assertEquals("JetBrains Mono", novelFontDisplayName(listOf(NovelFontName("JetBrains Mono", "en"))))
        assertEquals("Noto 黑体（简体中文）", novelFontDisplayName(listOf(NovelFontName("Noto Sans CJK SC", "en"))))
    }

    @Test
    fun formatOneLanguageTagsAreReadWithoutChangingTheSourceBuffer() {
        val font = fontFile(listOf(Record(1, 0x0409, "English Family"), Record(16, 0x8000, "本地字体")), "zh-Hans")
        font.position(7)
        assertEquals("本地字体", novelFontDisplayName(novelFontNames(font, 0)))
        assertEquals(7, font.position())
    }

    @Test
    fun unnamedOrDamagedFontsDoNotExposeInternalIdentifiers() {
        assertNull(novelFontDisplayName(novelFontNames(fontFile(listOf(Record(6, 0x0409, "InternalFont-Regular"))), 0)))
        assertTrue(novelFontNames(ByteBuffer.allocate(3), 0).isEmpty())
        val corrupt = fontFile(listOf(Record(1, 0x0409, "Family"))).apply { putInt(20, Int.MAX_VALUE) }
        assertTrue(novelFontNames(corrupt, 0).isEmpty())
    }

    @Test
    fun realWindowsCollectionAndDesktopListUseLocalizedNames() {
        val file = File("C:/Windows/Fonts/simsun.ttc")
        assumeTrue("Windows font collection fixture", file.isFile)
        val buffer = ByteBuffer.wrap(file.readBytes())
        assertEquals("宋体", novelFontDisplayName(novelFontNames(buffer, 0)))
        assertEquals("新宋体", novelFontDisplayName(novelFontNames(buffer, 1)))
        val fonts = runBlocking { DesktopNovelFonts.list() }
        assertEquals("宋体", fonts.first { it.id in setOf("desktop:SimSun", "desktop:宋体") }.name)
        assertEquals("新宋体", fonts.first { it.id in setOf("desktop:NSimSun", "desktop:新宋体") }.name)
    }

    private data class Record(val kind: Int, val language: Int, val value: String)

    private fun fontFile(records: List<Record>, languageTag: String? = null): ByteBuffer {
        val strings = records.map { it.value.toByteArray(Charsets.UTF_16BE) }
        val tag = languageTag?.toByteArray(Charsets.UTF_16BE)
        val storage = 6 + records.size * 12 + if (tag == null) 0 else 6
        val length = storage + strings.sumOf { it.size } + (tag?.size ?: 0)
        val data = ByteBuffer.allocate(28 + length)
        data.putInt(0x00010000).putShort(1).position(12)
        data.putInt(0x6e616d65).putInt(0).putInt(28).putInt(length)
        data.putShort(if (tag == null) 0 else 1).putShort(records.size.toShort()).putShort(storage.toShort())
        var offset = 0
        records.forEachIndexed { index, record ->
            data.putShort(3).putShort(1).putShort(record.language.toShort()).putShort(record.kind.toShort())
                .putShort(strings[index].size.toShort()).putShort(offset.toShort())
            offset += strings[index].size
        }
        if (tag != null) data.putShort(1).putShort(tag.size.toShort()).putShort(offset.toShort())
        strings.forEach(data::put)
        if (tag != null) data.put(tag)
        data.position(0)
        return data
    }
}
