package com.rchdliang.copymanga_neo.data.local.portable

import com.rchdliang.copymanga_neo.domain.model.*
import kotlin.test.*
import kotlinx.serialization.json.*

class PortableBackupCodecTest {
    @Test fun downloadPreferencesKeepVersionTwoCompatibilityAndStrictIntegerValidation() {
        val value = SettingsBackup(AppSettings(comicImageConcurrency = 8, comicDownloadLimitKiB = 1536),
            ReaderPreferences(), HomeRankPreferences(),
            PortableBackupCodec.reading(LocalReadingSnapshot()).toString(),
            PortableBackupCodec.tools(ReaderToolsSettings()).toString(), emptyMap())
        val raw = PortableBackupCodec.encode(value)
        assertEquals(value, PortableBackupCodec.decode(raw))
        val root = Json.parseToJsonElement(raw).jsonObject
        fun withSettings(settings: JsonObject) = JsonObject(root + ("settings" to settings)).toString()
        val app = root.getValue("settings").jsonObject
        val old = PortableBackupCodec.decode(withSettings(JsonObject(app - "comicImageConcurrency" - "comicDownloadLimitKiB")))
        assertEquals(3, old.settings.comicImageConcurrency)
        assertEquals(0, old.settings.comicDownloadLimitKiB)
        for (invalid in listOf(JsonPrimitive("3"), JsonPrimitive(1.5), JsonPrimitive(0), JsonPrimitive(11), JsonNull)) {
            assertFails { PortableBackupCodec.decode(withSettings(JsonObject(app + ("comicImageConcurrency" to invalid)))) }
        }
        assertFails { PortableBackupCodec.decode(withSettings(JsonObject(app + ("comicDownloadLimitKiB" to JsonPrimitive(-1))))) }
    }
    @Test fun androidReadingSchemaPreservesSourceAnchors() {
        val record = LocalReadingRecord("guest", "novel", "book", "volume", "第一卷", line = 5, character = 7, updatedAt = 1234, cover = "https://example.test/c.jpg")
        val snapshot = LocalReadingSnapshot(listOf(record), listOf(record))
        val raw = PortableBackupCodec.reading(snapshot)
        assertEquals(JsonPrimitive(1234), raw.getValue("records").jsonArray[0].jsonObject["at"])
        assertNull(raw.getValue("records").jsonArray[0].jsonObject["updatedAt"])
        assertEquals(snapshot, PortableBackupCodec.reading(raw))
    }
    @Test fun malformedAnchorsAreRejected() {
        val record = LocalReadingRecord("guest", "novel", "book", "vol", "卷", character = -1, updatedAt = 12)
        assertFails { PortableBackupCodec.reading(PortableBackupCodec.reading(LocalReadingSnapshot(records = listOf(record)))) }
    }
    @Test fun fullVersionTwoBackupRoundTripsWithoutCredentials() {
        val value = SettingsBackup(AppSettings(), ReaderPreferences(), HomeRankPreferences(),
            PortableBackupCodec.reading(LocalReadingSnapshot()).toString(), PortableBackupCodec.tools(ReaderToolsSettings()).toString(),
            mapOf("book" to ReaderPreferences("LEFT_TO_RIGHT", "FIT_WIDTH", "AUTO")))
        val text = PortableBackupCodec.encode(value)
        assertEquals(value, PortableBackupCodec.decode(text))
        assertFalse(text.contains("password", ignoreCase = true))
        assertFalse(text.contains("token", ignoreCase = true))
    }
    @Test fun unknownVersionIsRejectedBeforeMutation() {
        assertFails { PortableBackupCodec.decode("""{"format":"copymanga-neo-settings","version":999}""") }
    }
}
