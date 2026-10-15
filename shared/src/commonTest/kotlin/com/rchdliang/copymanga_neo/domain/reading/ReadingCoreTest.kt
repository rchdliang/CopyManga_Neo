package com.rchdliang.copymanga_neo.domain.reading

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.test.Test

/** Kotlin 实现的兼容性回归：锚点、请求字段和磁盘身份不能随语言转换改变。 */
class ReadingCoreTest {
    @Test
    fun utf8BomBlankAndTrailingLinesKeepOfficialIndices() {
        val document =
            NovelTextDocument.decode("\ufeff卷首\r\n\r\n章节\r\n中文😀\r\n".encodeToByteArray(), " utf_8 ")
        assertEquals(listOf("卷首", "", "章节", "中文😀", ""), document.lines)
        assertEquals(
            listOf("甲", "乙", "丙", ""),
            NovelTextDocument.decode("甲\n乙\r丙\r".encodeToByteArray(), "UTF8").lines
        )
        assertEquals(listOf(""), NovelTextDocument.decode(byteArrayOf(), "UTF-8").lines)
        assertNotEquals(
            document,
            NovelTextDocument.decode("\ufeff卷首\r\n\r\n章节\r\n中文😀\r\n".encodeToByteArray(), "UTF-8")
        )
    }

    @Test
    fun gbkIsStrictAndPreservesEmptyLines() {
        val raw = byteArrayOf(0xd2.toByte(), 0xb0.toByte(), 0xbb.toByte(), 0xf0.toByte(), 13, 10, 13, 10, 0xb5.toByte(), 0xda.toByte(), 0xb6.toByte(), 0xfe.toByte(), 0xd5.toByte(), 0xc2.toByte(), 13, 10)
        assertEquals(listOf("野火", "", "第二章", ""), NovelTextDocument.decode(raw, "GBK").lines)
        assertFailsWith<CharacterCodingException> {
            NovelTextDocument.decode(
                byteArrayOf(
                    0x81.toByte()
                ), "GBK"
            )
        }
        assertFailsWith<CharacterCodingException> {
            NovelTextDocument.decode(
                byteArrayOf(
                    0xc3.toByte(),
                    0x28
                ), "UTF-8"
            )
        }
        assertFailsWith<IllegalArgumentException> {
            NovelTextDocument.decode(
                byteArrayOf(),
                "not-a-real-encoding"
            )
        }
    }

    @Test
    fun gb18030FourByteCharactersKeepOfficialLinesAndUtf16Anchors() {
        // “中文”后接 GB18030 四字节字符 U+10000，不能以 GBK 代替解码。
        val raw = byteArrayOf(
            0xd6.toByte(), 0xd0.toByte(), 0xce.toByte(), 0xc4.toByte(),
            0x90.toByte(), 0x30, 0x81.toByte(), 0x30,
            0x0d, 0x0a, 0x0d, 0x0a, 0x65, 0x6e, 0x64, 0x0d, 0x0a
        )
        for (encoding in listOf("GB18030", " gb18030 ", "GB-18030", "gb_18030")) {
            val document = NovelTextDocument.decode(raw, encoding)
            assertEquals(listOf("中文\uD800\uDC00", "", "end", ""), document.lines)
            assertEquals(2, document.clampCharacter(0, 3))
            assertEquals(4, document.clampCharacter(0, 4))
            document.validateRange(0, 4)
        }
        assertFailsWith<CharacterCodingException> {
            NovelTextDocument.decode(byteArrayOf(0x90.toByte(), 0x30, 0x81.toByte()), "GB18030")
        }
    }

    @Test
    fun systemCharsetsDecodeUtf16BomAndLatin1WithoutAWhitelist() {
        val utf16 = byteArrayOf(
            0xff.toByte(), 0xfe.toByte(),
            0x32, 0x75, 0x0d, 0x00, 0x0a, 0x00,
            0x59, 0x4e, 0x0d, 0x00, 0x0a, 0x00
        )
        assertEquals(listOf("甲", "乙", ""), NovelTextDocument.decode(utf16, "UTF-16").lines)
        assertEquals(listOf("甲", "乙", ""), NovelTextDocument.decode(utf16, "UTF-16LE").lines)
        val latin1 = byteArrayOf(0x43, 0x61, 0x66, 0xe9.toByte(), 0x0a)
        assertEquals(listOf("Café", ""), NovelTextDocument.decode(latin1, "ISO-8859-1").lines)
    }

    @Test
    fun textAnchorUsesUtf16AndDoesNotSplitSurrogates() {
        val document = NovelTextDocument.decode("中文😀\n".encodeToByteArray(), "UTF-8")
        assertEquals(2, document.clampCharacter(0, 3))
        assertEquals(4, document.clampCharacter(0, 4))
        assertEquals(0, document.clampCharacter(0, Int.MIN_VALUE))
        assertEquals(4, document.clampCharacter(-1, Int.MAX_VALUE))
        assertEquals(1, document.clampLine(Int.MAX_VALUE))
        document.validateRange(0, 0)
        document.validateRange(0, 2)
        for ((start, end) in listOf(-1 to 1, 1 to 0, 0 to 3)) {
            assertFailsWith<IllegalArgumentException> {
                document.validateRange(
                    start,
                    end
                )
            }
        }
    }

    @Test
    fun persistedKeysAndDigestsAreByteForByteCompatible() {
        val emptyDigest = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        assertEquals(emptyDigest, ReadingContentHash.sha256(byteArrayOf()))
        assertEquals(
            "3543:1:113:1510:$emptyDigest",
            NovelTextDocument.sectionKey("3543", 1, 113, 1510, null)
        )
        assertEquals(
            NovelTextDocument.sectionKey("3543", 1, 113, 1510, null),
            NovelTextDocument.sectionKey("3543", 1, 113, 1510, "")
        )
        assertEquals("2:😀3:a:b0:", ReadingMergePolicy.key("😀", "a:b", ""))
        assertNotEquals(ReadingMergePolicy.key("a", "b:c"), ReadingMergePolicy.key("a:b", "c"))
        assertNotEquals(
            NovelTextDocument.sectionKey("v", 2, 0, 0, "https://a/1"),
            NovelTextDocument.sectionKey("v", 2, 0, 0, "https://a/2")
        )
    }

    @Test
    fun pixelAnchorsClampScaleAndRetainHalfUpRounding() {
        assertEquals(
            300,
            ReadingPositionPolicy.offset(ReadingPositionPolicy.fraction(600, 2000), 1000)
        )
        assertEquals(1, ReadingPositionPolicy.offset(0.5, 1))
        for (invalid in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            assertEquals(0, ReadingPositionPolicy.offset(invalid, 1000))
        }
        assertEquals(0, ReadingPositionPolicy.offset(0.5, -1))
        assertEquals(100, ReadingPositionPolicy.offset(3.0, 100))
        assertEquals(0, ReadingPositionPolicy.offset(-1.0, 100))
        assertEquals(0.0, ReadingPositionPolicy.fraction(1, 0), 0.0)
        assertEquals(
            Int.MAX_VALUE - 1,
            ReadingPositionPolicy.clampPage(Int.MAX_VALUE, Int.MAX_VALUE)
        )
        assertEquals(0, ReadingPositionPolicy.clampPage(-1, 5))
        assertEquals(0, ReadingPositionPolicy.clampPage(5, 0))
    }

    @Test
    fun completionRequiresSettledLastImageBottom() {
        assertTrue(ReadingPositionPolicy.completed(5, 6, true, true))
        assertFalse(ReadingPositionPolicy.completed(4, 6, true, true))
        assertFalse(ReadingPositionPolicy.completed(5, 6, false, true))
        assertFalse(ReadingPositionPolicy.completed(5, 6, true, false))
        assertFalse(ReadingPositionPolicy.completed(0, 0, true, true))
    }

    @Test
    fun deletionCutoffAndOwnerIsolationRemainStrict() {
        assertTrue(ReadingMergePolicy.accepts(101, 101, 100))
        assertFalse(ReadingMergePolicy.accepts(100, 99, 100))
        assertFalse(ReadingMergePolicy.accepts(101, 102, 100))
        assertFalse(ReadingMergePolicy.survives(100, 100))
        assertTrue(ReadingMergePolicy.survives(101, 100))
        val deleted = mapOf(ReadingMergePolicy.key("guest", "comic", "book") to 100L)
        assertFalse(ReadingMergePolicy.canUseCoarse("guest", "comic", "book", emptyMap(), deleted))
        assertTrue(ReadingMergePolicy.canUseCoarse("guest", "novel", "book", emptyMap(), deleted))
        assertTrue(
            ReadingMergePolicy.canUseCoarse(
                "user:a",
                "comic",
                "book",
                mapOf("guest" to 100L),
                deleted
            )
        )
        assertFalse(
            ReadingMergePolicy.canUseCoarse(
                "guest",
                "comic",
                "other",
                mapOf("guest" to 100L),
                emptyMap()
            )
        )
    }

    @Test
    fun rollbackPreservesLaterEditsAndDeletionWithoutMutatingInputs() {
        val before = linkedMapOf("a" to "old", "untouched" to "keep")
        val applied = linkedMapOf("a" to "imported", "untouched" to "keep", "b" to "import-added")
        assertEquals(before, ImportRollbackPolicy.restore(before, applied, applied))
        val current =
            applied.toMutableMap().apply { put("a", "later-edit"); put("c", "later-added") }
        assertEquals(
            linkedMapOf("a" to "later-edit", "untouched" to "keep", "c" to "later-added"),
            ImportRollbackPolicy.restore(before, applied, current)
        )
        assertEquals("import-added", current["b"])
        current.remove("a")
        assertFalse(ImportRollbackPolicy.restore(before, applied, current).containsKey("a"))
        assertEquals(current, ImportRollbackPolicy.restore(before, before, current))
    }

    @Test
    fun rollbackDistinguishesMissingFromNullAndPreservesOrder() {
        val before = linkedMapOf<String?, String?>("old-null" to null, null to "old")
        val applied = linkedMapOf<String?, String?>(null to "new", "new-null" to null)
        val restored = ImportRollbackPolicy.restore(before, applied, applied)
        assertTrue(restored.containsKey("old-null"))
        assertFalse(restored.containsKey("new-null"))
        assertEquals(before, restored)
        assertEquals(listOf(null, "old-null"), restored.keys.toList())
        assertEquals(
            mapOf("new-null" to "later"), ImportRollbackPolicy.restore(
                emptyMap(), mapOf("new-null" to null), mapOf("new-null" to "later")
            )
        )
    }

    @Test
    fun browseContractsKeepAppAndWebLimitsAndFieldOrder() {
        assertEquals(
            linkedMapOf(
                "platform" to "3", "limit" to "18", "offset" to "18",
                "ordering" to "-popular", "theme" to "fantasy", "author" to "writer"
            ),
            NovelRequestSpec.browse(true, 18, "-popular", "fantasy", "writer")
        )
        val web = NovelRequestSpec.browse(false, 21, "-datetime_updated", "", "")
        assertEquals(listOf("limit", "offset", "ordering", "free_type"), web.keys.toList())
        assertEquals("21", web["limit"])
        assertEquals("1", web["free_type"])
        assertFailsWith<IllegalArgumentException> {
            NovelRequestSpec.browse(
                false,
                0,
                "popular",
                "",
                ""
            )
        }
    }

    @Test
    fun searchContractsKeepEmptyTypeAndDoNotInventFilters() {
        for (type in listOf("", "name", "author")) {
            val web = NovelRequestSpec.search(false, 20, "中文", type)
            assertEquals(
                linkedMapOf(
                    "limit" to "20",
                    "offset" to "20",
                    "q" to "中文",
                    "q_type" to type
                ), web
            )
            assertEquals("18", NovelRequestSpec.search(true, 0, "中文", type)["limit"])
        }
        assertFailsWith<IllegalArgumentException> {
            NovelRequestSpec.search(
                true,
                -1,
                "",
                ""
            )
        }
        assertFailsWith<IllegalArgumentException> {
            NovelRequestSpec.search(
                true,
                0,
                "",
                "all"
            )
        }
    }


    @Test
    fun portableSha256MatchesStandardVectorsAcrossPaddingBoundaries() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            ReadingContentHash.sha256("abc".encodeToByteArray())
        )
        assertEquals(
            "248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1",
            ReadingContentHash.sha256("abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq".encodeToByteArray())
        )
        assertEquals(
            "cdc76e5c9914fb9281a1c7e284d73e67f1809a48a497200e046d39ccc7112cd0",
            ReadingContentHash.sha256(ByteArray(1_000_000) { 97 })
        )
    }

    @Test
    fun utf16RejectsTruncatedAndUnpairedSurrogates() {
        for (bytes in listOf(
            byteArrayOf(0),
            byteArrayOf(0x00, 0xd8.toByte()),
            byteArrayOf(0x00, 0xdc.toByte()),
            byteArrayOf(0x00, 0xd8.toByte(), 0x41, 0x00)
        )) {
            assertFailsWith<CharacterCodingException> { NovelTextDocument.decode(bytes, "UTF-16LE") }
        }
        // UTF-16 without a BOM defaults to big-endian, like the original JVM decoder.
        assertEquals(listOf("甲"), NovelTextDocument.decode(byteArrayOf(0x75, 0x32), "UTF-16").lines)
        assertEquals(listOf("\u0080"), NovelTextDocument.decode(byteArrayOf(0x80.toByte()), "ISO-8859-1").lines)
        assertEquals(listOf("\u0080"), NovelTextDocument.decode(byteArrayOf(0x80.toByte()), "ISO_8859-1:1987").lines)
        assertFailsWith<CharacterCodingException> { NovelTextDocument.decode(byteArrayOf(0x80.toByte()), "ANSI_X3.4-1968") }
    }
}
