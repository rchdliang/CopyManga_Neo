package com.rchdliang.copymanga_neo.data.local

import com.google.gson.JsonParser
import com.rchdliang.copymanga_neo.domain.model.AppSettings
import com.rchdliang.copymanga_neo.domain.model.HomeRankPreferences
import com.rchdliang.copymanga_neo.domain.model.LocalReadingRecord
import com.rchdliang.copymanga_neo.domain.model.LocalReadingSnapshot
import com.rchdliang.copymanga_neo.domain.model.ReaderPreferences
import com.rchdliang.copymanga_neo.domain.model.ReaderToolsSettings
import com.rchdliang.copymanga_neo.domain.model.SettingsBackup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

/** 验证真实持久化编码，包含漫画和小说的新记录元数据。 */
class ReadingBackupCodecTest {
    @Test
    fun historyAndBookmarksRetainRealMetadata() {
        val records = listOf(
            LocalReadingRecord(
                "guest",
                "comic",
                "frieren",
                "c1",
                "第一话",
                title = "葬送的芙莉莲",
                cover = "https://example.com/comic.jpg",
                authors = "山田钟人 / 阿部司"
            ),
            LocalReadingRecord(
                "guest", "novel", "spice", "v1", "第一卷",
                title = "狼与香辛料", cover = "https://example.com/novel.jpg", authors = "支仓冻砂"
            )
        )
        val snapshot = LocalReadingSnapshot(records = records, bookmarks = records)
        assertEquals(snapshot, LocalReadingCodec.decode(LocalReadingCodec.encode(snapshot)))
        assertEquals("", LocalReadingRecord("guest", "comic", "route-only", "c", "").title)
    }

    @Test
    fun malformedMetadataIsRejected() {
        val json = LocalReadingCodec.encodeRecord(
            LocalReadingRecord(
                "guest",
                "novel",
                "book",
                "v1",
                "第一卷"
            )
        )
        json.addProperty("cover", 123)
        assertThrows(IllegalArgumentException::class.java) { LocalReadingCodec.decodeRecord(json) }
    }

    private val comic = LocalReadingRecord(
        "guest",
        "comic",
        "a",
        "chapter",
        "第一话",
        page = 4,
        fraction = 0.35,
        updatedAt = 100
    )
    private val novel = LocalReadingRecord(
        "user:a",
        "novel",
        "b",
        "3543",
        "第一卷",
        section = "s",
        line = 113,
        character = 4,
        completed = true,
        updatedAt = 200
    )

    @Test
    fun readingRoundTripRetainsAccountsAndDeletionMarkers() {
        val snapshot = LocalReadingSnapshot(
            listOf(comic, novel), listOf(novel),
            mapOf("old" to 10), mapOf("user:b" to 20), mapOf("book" to 30)
        )
        assertEquals(snapshot, LocalReadingCodec.decode(LocalReadingCodec.encode(snapshot)))
    }

    @Test
    fun invalidPositionsAreRejectedBeforeWriting() {
        for (field in listOf("page", "line", "character")) {
            val json =
                JsonParser.parseString(LocalReadingCodec.encode(LocalReadingSnapshot(listOf(comic)))).asJsonObject
            json.getAsJsonArray("records")[0].asJsonObject.addProperty(field, -1)
            assertThrows(IllegalArgumentException::class.java) { LocalReadingCodec.decode(json.toString()) }
        }
    }

    @Test
    fun stringAndFractionalPageAreRejected() {
        for (value in listOf("1.2", "\"2\"")) {
            val json =
                JsonParser.parseString(LocalReadingCodec.encode(LocalReadingSnapshot(listOf(comic)))).asJsonObject
            json.getAsJsonArray("records")[0].asJsonObject.add(
                "page",
                JsonParser.parseString(value)
            )
            assertThrows(Exception::class.java) { LocalReadingCodec.decode(json.toString()) }
        }
    }

    @Test
    fun readerToolsBackupContainsOnlyCurrentSettings() {
        val settings = ReaderToolsSettings(volumeKeys = true, autoIntervalSeconds = 12)
        val encoded = ReaderToolsBackupCodec.encode(settings)
        assertEquals(settings, ReaderToolsBackupCodec.decode(encoded))
        assertFalse(JsonParser.parseString(encoded).asJsonObject.has("bookmarks"))
    }

    @Test
    fun unsupportedSettingsVersionIsRejected() {
        val old = JsonParser.parseString(SettingsBackupCodec.encode(backup())).asJsonObject.apply {
            addProperty(
                "version",
                1
            )
        }
        assertThrows(IllegalArgumentException::class.java) { SettingsBackupCodec.decode(old.toString()) }
    }

    @Test
    fun versionTwoCarriesReadingDataWithoutCredentials() {
        val backup = backup()
        val encoded = SettingsBackupCodec.encode(backup)
        assertEquals(backup, SettingsBackupCodec.decode(encoded))
        assertFalse(encoded.contains("authorization")); assertFalse(encoded.contains("password")); assertFalse(
            encoded.contains("token")
        )
    }

    @Test
    fun unknownVersionAndDuplicateRecordsAreRejected() {
        val json =
            JsonParser.parseString(LocalReadingCodec.encode(LocalReadingSnapshot(listOf(comic)))).asJsonObject
        json.getAsJsonArray("records").add(json.getAsJsonArray("records")[0].deepCopy())
        assertThrows(IllegalArgumentException::class.java) { LocalReadingCodec.decode(json.toString()) }
        json.addProperty("version", 9)
        assertThrows(IllegalArgumentException::class.java) { LocalReadingCodec.decode(json.toString()) }
    }

    @Test
    fun perComicPreferencesRoundTripRetainsIndependentBooks() {
        val books = mapOf(
            "a" to ReaderPreferences("TOP_TO_BOTTOM", "FIT_WIDTH", "PORTRAIT"),
            "b" to ReaderPreferences("LEFT_TO_RIGHT", "FIT_HEIGHT", "LANDSCAPE")
        )
        val backup = backup().copy(comicPreferences = books)
        assertEquals(backup, SettingsBackupCodec.decode(SettingsBackupCodec.encode(backup)))
    }

    @Test
    fun malformedPerComicPreferencesRejectWholeImport() {
        val backup = backup().copy(comicPreferences = mapOf("a" to ReaderPreferences()))
        val json = JsonParser.parseString(SettingsBackupCodec.encode(backup)).asJsonObject
        json.getAsJsonObject("comicPreferences").getAsJsonObject("a")
            .addProperty("direction", "invalid")
        assertThrows(IllegalArgumentException::class.java) { SettingsBackupCodec.decode(json.toString()) }
    }

    @Test
    fun missingCurrentBackupSectionsAreRejected() {
        for (field in listOf("localReading", "readerTools", "comicPreferences")) {
            val json = JsonParser.parseString(SettingsBackupCodec.encode(backup())).asJsonObject
            json.remove(field)
            assertThrows(Exception::class.java) { SettingsBackupCodec.decode(json.toString()) }
        }
    }

    @Test
    fun perComicRollbackKeepsSubsequentEditsAndRemovesImportedOnlyBook() {
        val before = mapOf("a" to ComicPreferencesCodec.encode(ReaderPreferences()))
        val applied = mapOf(
            "a" to ComicPreferencesCodec.encode(ReaderPreferences("TOP_TO_BOTTOM")),
            "b" to ComicPreferencesCodec.encode(ReaderPreferences())
        )
        val later = ComicPreferencesCodec.encode(ReaderPreferences("LEFT_TO_RIGHT"))
        val current = applied + ("a" to later) + ("c" to later)
        val restored = com.rchdliang.copymanga_neo.domain.reading.ImportRollbackPolicy.restore(
            before,
            applied,
            current
        )
        assertEquals(later, restored["a"]); assertEquals(
            later,
            restored["c"]
        ); assertFalse(restored.containsKey("b"))
    }

    private fun backup() = SettingsBackup(
        AppSettings(), ReaderPreferences(), HomeRankPreferences(),
        LocalReadingCodec.encode(LocalReadingSnapshot(listOf(comic, novel))),
        ReaderToolsBackupCodec.encode(ReaderToolsSettings()), emptyMap()
    )
}
