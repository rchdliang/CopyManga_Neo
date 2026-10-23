package com.rchdliang.copymanga_neo.data.local

import com.rchdliang.copymanga_neo.domain.model.ReaderToolsSettings
import org.junit.Assert.*
import org.junit.Test

class ReaderSpreadPreferencesTest {
    @Test fun existingBackupUsesSinglePageAndAnimationDefaults() {
        val legacy = """{"inlineComments":true,"continuousChapters":true,"volumeKeys":false,"autoInterval":8,"imageRetries":2,"imageTimeout":15,"preload":2}"""
        assertEquals(ReaderToolsSettings(), ReaderToolsBackupCodec.decode(legacy))
    }
    @Test fun localBoundariesAndLayoutRoundTripWithoutChangingRawPageNumbers() {
        val settings = ReaderToolsSettings(pageLayout = "AUTO", pageAnimation = false,
            pairingRestarts = mapOf("chapter-a" to listOf(2, 9), "chapter-b" to listOf(4)))
        assertEquals(settings, ReaderToolsBackupCodec.decode(ReaderToolsBackupCodec.encode(settings)))
        assertEquals(settings.pairingRestarts, ReaderToolsBackupCodec.decodePairing(ReaderToolsBackupCodec.encodePairing(settings.pairingRestarts)))
    }
    @Test fun malformedModesAndRestartValuesAreNormalized() {
        val settings = ReaderToolsSettings(pageLayout = "unknown", pairingRestarts = mapOf("a" to listOf(4, -1, 2, 4)))
        assertEquals("SINGLE", settings.normalized().pageLayout)
        assertEquals(listOf(2, 4), settings.normalized().pairingRestarts["a"])
    }
}
