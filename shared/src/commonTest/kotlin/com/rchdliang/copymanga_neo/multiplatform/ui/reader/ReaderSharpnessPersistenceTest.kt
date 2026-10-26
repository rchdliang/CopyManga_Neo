package com.rchdliang.copymanga_neo.multiplatform.ui.reader
import com.rchdliang.copymanga_neo.domain.model.ReaderToolsSettings
import kotlinx.serialization.json.Json
import kotlin.test.*
class ReaderSharpnessPersistenceTest {
    @Test fun oldSettingsDefaultOffAndSelectionRoundTrips() {
        val json=Json { encodeDefaults=true }
        assertEquals(0f,json.decodeFromString<ReaderToolsSettings>("{}").sharpness)
        val settings=ReaderToolsSettings(sharpness=0.2f)
        assertEquals(settings,json.decodeFromString<ReaderToolsSettings>(json.encodeToString(settings)))
        assertEquals(0f,ReaderToolsSettings(sharpness=Float.NaN).normalized().sharpness)
        assertEquals(1f,ReaderToolsSettings(sharpness=10f).normalized().sharpness)
        val strong = ReaderToolsSettings(sharpness=0.8f, sharpnessRestore=0.8f).normalized()
        assertEquals(strong,json.decodeFromString<ReaderToolsSettings>(json.encodeToString(strong)).normalized())
        assertEquals(0.8f,strong.sharpness)
    }
}
