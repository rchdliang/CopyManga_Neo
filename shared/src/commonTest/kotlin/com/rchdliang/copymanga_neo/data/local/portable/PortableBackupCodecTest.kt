package com.rchdliang.copymanga_neo.data.local.portable

import com.rchdliang.copymanga_neo.domain.model.*
import kotlin.test.*
import kotlinx.serialization.json.*

class PortableBackupCodecTest {
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
