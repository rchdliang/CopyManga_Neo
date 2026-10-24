package com.rchdliang.copymanga_neo.multiplatform.ui.reader
import com.rchdliang.copymanga_neo.domain.model.ReaderPreferences
import kotlin.test.Test
import kotlin.test.*
class ReaderComicLayoutTest {
    @Test fun defaultsAndInvalidValuesUseSinglePage() {
        assertEquals("SINGLE",ReaderPreferences().normalized().pageLayout)
        assertEquals("SINGLE",ReaderPreferences(pageLayout="SINGLE").normalized().pageLayout)
        assertEquals("SINGLE",ReaderPreferences(pageLayout="broken").normalized().pageLayout)
    }
}
