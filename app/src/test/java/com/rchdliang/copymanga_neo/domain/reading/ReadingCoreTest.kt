package com.rchdliang.copymanga_neo.domain.reading

import com.rchdliang.copymanga_neo.data.novel.cache.AtomicCacheFile
import com.rchdliang.copymanga_neo.data.novel.cache.VerifiedVolumeCache
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.nio.charset.Charset

/** Kotlin 实现的兼容性回归：锚点、请求字段和磁盘身份不能随语言转换改变。 */
class ReadingCoreTest {
    @get:Rule
    val temporary = TemporaryFolder()

    @Test
    fun utf8BomBlankAndTrailingLinesKeepOfficialIndices() {
        val document =
            NovelTextDocument.decode("\ufeff卷首\r\n\r\n章节\r\n中文😀\r\n".toByteArray(), " utf_8 ")
        assertEquals(listOf("卷首", "", "章节", "中文😀", ""), document.lines)
        assertEquals(
            listOf("甲", "乙", "丙", ""),
            NovelTextDocument.decode("甲\n乙\r丙\r".toByteArray(), "UTF8").lines
        )
        assertEquals(listOf(""), NovelTextDocument.decode(byteArrayOf(), "UTF-8").lines)
        assertNotEquals(
            document,
            NovelTextDocument.decode("\ufeff卷首\r\n\r\n章节\r\n中文😀\r\n".toByteArray(), "UTF-8")
        )
    }

    @Test
    fun gbkIsStrictAndPreservesEmptyLines() {
        val raw = "野火\r\n\r\n第二章\r\n".toByteArray(Charset.forName("GBK"))
        assertEquals(listOf("野火", "", "第二章", ""), NovelTextDocument.decode(raw, "GBK").lines)
        assertThrows(CharacterCodingException::class.java) {
            NovelTextDocument.decode(
                byteArrayOf(
                    0x81.toByte()
                ), "GBK"
            )
        }
        assertThrows(CharacterCodingException::class.java) {
            NovelTextDocument.decode(
                byteArrayOf(
                    0xc3.toByte(),
                    0x28
                ), "UTF-8"
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
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
        assertThrows(CharacterCodingException::class.java) {
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
        val document = NovelTextDocument.decode("中文😀\n".toByteArray(), "UTF-8")
        assertEquals(2, document.clampCharacter(0, 3))
        assertEquals(4, document.clampCharacter(0, 4))
        assertEquals(0, document.clampCharacter(0, Int.MIN_VALUE))
        assertEquals(4, document.clampCharacter(-1, Int.MAX_VALUE))
        assertEquals(1, document.clampLine(Int.MAX_VALUE))
        document.validateRange(0, 0)
        document.validateRange(0, 2)
        for ((start, end) in listOf(-1 to 1, 1 to 0, 0 to 3)) {
            assertThrows(IllegalArgumentException::class.java) {
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
        assertEquals(emptyDigest, AtomicCacheFile.digest(byteArrayOf()))
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
        assertThrows(IllegalArgumentException::class.java) {
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
        assertThrows(IllegalArgumentException::class.java) {
            NovelRequestSpec.search(
                true,
                -1,
                "",
                ""
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            NovelRequestSpec.search(
                true,
                0,
                "",
                "all"
            )
        }
    }

    @Test
    fun boundedReadDoesNotCloseCallerAndRejectsOverflow() {
        var closed = false
        val stream = object : ByteArrayInputStream(byteArrayOf(1, 2, 3)) {
            override fun close() {
                closed = true; super.close()
            }
        }
        assertArrayEquals(byteArrayOf(1, 2, 3), AtomicCacheFile.readBounded(stream, 3))
        assertFalse(closed)
        assertThrows(IOException::class.java) {
            AtomicCacheFile.readBounded(
                ByteArrayInputStream(
                    byteArrayOf(1, 2, 3, 4)
                ), 3
            )
        }
        assertArrayEquals(
            byteArrayOf(),
            AtomicCacheFile.readBounded(ByteArrayInputStream(byteArrayOf()), 0)
        )
    }

    @Test
    fun atomicReplacementCleansTemporaryFilesAndFailedWriteKeepsOriginal() {
        val dir = temporary.newFolder()
        val target = File(dir, "manifest.json")
        AtomicCacheFile.write(target, "original".toByteArray())
        AtomicCacheFile.write(target, "replacement".toByteArray())
        assertEquals("replacement", target.readText())
        assertEquals(listOf("manifest.json"), dir.list()!!.toList())
        assertThrows(IOException::class.java) {
            AtomicCacheFile.write(
                File(target, "child.json"),
                byteArrayOf(1)
            )
        }
        assertEquals("replacement", target.readText())
    }

    @Test
    fun failedManifestPublicationPreservesReadablePreviousRevision() {
        val dir = temporary.newFolder()
        val old = "旧正文\n第一章".toByteArray()
        val fresh = "新正文\n修订章节".toByteArray()
        VerifiedVolumeCache.publish(
            dir,
            old,
            "old manifest".toByteArray()
        ) { NovelTextDocument.decode(it, "UTF-8") }
        assertThrows(IOException::class.java) {
            VerifiedVolumeCache.publish(
                dir,
                fresh,
                "new manifest".toByteArray(),
                { NovelTextDocument.decode(it, "UTF-8") },
                { _, _ -> throw IOException("manifest commit failed") })
        }
        assertEquals("old manifest", File(dir, "manifest.json").readText())
        assertArrayEquals(
            old,
            VerifiedVolumeCache.read(
                dir,
                VerifiedVolumeCache.textName(old),
                AtomicCacheFile.digest(old),
                1024
            )
        )
        VerifiedVolumeCache.publish(
            dir,
            fresh,
            "new manifest".toByteArray()
        ) { NovelTextDocument.decode(it, "UTF-8").validateRange(0, 2) }
        assertEquals("new manifest", File(dir, "manifest.json").readText())
        assertArrayEquals(
            fresh,
            VerifiedVolumeCache.read(
                dir,
                VerifiedVolumeCache.textName(fresh),
                AtomicCacheFile.digest(fresh),
                1024
            )
        )
    }

    @Test
    fun validationFailurePublishesNothing() {
        val dir = temporary.newFolder()
        val malformed = byteArrayOf(0xc3.toByte(), 0x28)
        assertThrows(CharacterCodingException::class.java) {
            VerifiedVolumeCache.publish(
                dir,
                malformed,
                byteArrayOf()
            ) { NovelTextDocument.decode(it, "UTF-8") }
        }
        assertTrue(dir.list()!!.isEmpty())
        assertThrows(IllegalArgumentException::class.java) {
            VerifiedVolumeCache.publish(
                dir,
                "one".toByteArray(),
                byteArrayOf()
            ) { NovelTextDocument.decode(it, "UTF-8").validateRange(0, 9) }
        }
        assertTrue(dir.list()!!.isEmpty())
    }

    @Test
    fun cachedReadsRejectNullIdentityTraversalChecksumMismatchAndOversize() {
        val dir = temporary.newFolder()
        val body = "已缓存正文".toByteArray()
        val digest = AtomicCacheFile.digest(body)
        val name = VerifiedVolumeCache.textName(body)
        AtomicCacheFile.write(File(dir, name), body)
        assertArrayEquals(body, VerifiedVolumeCache.read(dir, name, digest, 1024))
        for ((name, hash) in listOf(
            null to digest, name to null, "../manifest.json" to digest,
            name to digest.uppercase(), "text.wrong.txt" to digest, "volume.txt" to digest
        )) {
            assertThrows(IOException::class.java) {
                VerifiedVolumeCache.read(
                    dir,
                    name,
                    hash,
                    1024
                )
            }
        }
        assertThrows(IOException::class.java) {
            VerifiedVolumeCache.read(
                dir,
                name,
                digest,
                1
            )
        }
        AtomicCacheFile.write(File(dir, name), "changed".toByteArray())
        assertThrows(IOException::class.java) {
            VerifiedVolumeCache.read(
                dir,
                name,
                digest,
                1024
            )
        }
    }
}
