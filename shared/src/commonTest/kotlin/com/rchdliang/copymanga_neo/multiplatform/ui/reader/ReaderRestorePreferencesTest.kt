package com.rchdliang.copymanga_neo.multiplatform.ui.reader

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import com.rchdliang.copymanga_neo.domain.model.ReaderToolsSettings
import com.rchdliang.copymanga_neo.data.local.portable.PortableBackupCodec
import kotlinx.serialization.json.Json
import kotlin.test.*

class ReaderRestorePreferencesTest {
    @Test fun defaultsAndIndependentLevelsSurviveSaveAndBackup() {
        val defaults = ReaderToolsSettings()
        assertEquals(ReaderRestoreLevel.M, defaults.restoreSelection().level)
        var value = defaults.withRestoreMode(ReaderRestoreMode.RESTORE).withRestoreLevel(ReaderRestoreLevel.UL)
        value = value.withRestoreMode(ReaderRestoreMode.SOFT)
        assertEquals(ReaderRestoreLevel.M, value.restoreSelection().level)
        value = value.withRestoreLevel(ReaderRestoreLevel.S).withRestoreMode(ReaderRestoreMode.OFF)
        assertEquals("UL", value.restoreLevel)
        assertEquals("S", value.restoreSoftLevel)
        assertEquals(ReaderRestoreLevel.S, value.restoreSelection().level)
        val json = Json { encodeDefaults = true }
        assertEquals(value, json.decodeFromString<ReaderToolsSettings>(json.encodeToString(value)))
        assertEquals(value, PortableBackupCodec.tools(PortableBackupCodec.tools(value)))
        assertEquals(ReaderRestoreLevel.UL, value.withRestoreMode(ReaderRestoreMode.RESTORE).restoreSelection().level)
        assertEquals(ReaderRestoreLevel.M, json.decodeFromString<ReaderToolsSettings>("{}").restoreSelection().level)
    }

    @Test fun adjustmentMaximizesOnlyOnEntryAndPreservesManualView() {
        val zoom = ReaderZoomState()
        zoom.updateViewport(IntSize(1000, 800))
        zoom.transform(2f, zoom.viewportCenter(), Offset(20f, -40f), READER_MAX_SCALE)
        val state = ReaderQualityAdjustmentState()
        state.begin("page", zoom)
        assertEquals(READER_MAX_SCALE, zoom.scale)
        zoom.transform(.4f, zoom.viewportCenter(), Offset.Zero, READER_MAX_SCALE)
        zoom.pan(Offset(50f, 20f))
        val observing = zoom.snapshot()
        state.begin("page", zoom)
        assertEquals(observing, zoom.snapshot())
        state.finish()
        assertEquals(observing, zoom.snapshot())
        assertFalse(state.active)
    }

    @Test fun changingPageAndClosingDoNotResetUserView() {
        val first = ReaderZoomState().apply { updateViewport(IntSize(1000, 800)) }
        val second = ReaderZoomState().apply { updateViewport(IntSize(1000, 800)) }
        val state = ReaderQualityAdjustmentState()
        state.begin("first", first, Offset(250f, 400f))
        assertEquals(1250f, first.offset.x)
        state.begin("second", second)
        assertEquals(5f, first.scale)
        assertEquals(5f, second.scale)
        state.finish()
        assertEquals(5f, second.scale)
    }

    @Test fun continuousMaximumAppliesOnceEvenWhenViewportArrivesLater() {
        val state = ReaderQualityAdjustmentState()
        state.begin("page", null, maximum = READER_CONTINUOUS_MAX_SCALE)
        val zoom = ReaderZoomState().apply { updateViewport(IntSize(1000, 800)) }
        state.attach(zoom)
        assertEquals(3f, zoom.scale)
        zoom.transform(.5f, zoom.viewportCenter(), Offset(40f, 20f), 3f)
        val observing = zoom.snapshot()
        state.begin("page", zoom, maximum = 3f)
        assertEquals(observing, zoom.snapshot())
        val replacement = ReaderZoomState().apply { updateViewport(IntSize(1000, 800)) }
        state.attach(replacement)
        assertEquals(observing, replacement.snapshot())
        state.finish()
        assertEquals(observing, replacement.snapshot())
        state.begin("page", replacement, maximum = 3f)
        assertEquals(3f, replacement.scale)
    }
}
