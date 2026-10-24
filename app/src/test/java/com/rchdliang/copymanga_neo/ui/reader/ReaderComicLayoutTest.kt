package com.rchdliang.copymanga_neo.ui.reader
import com.rchdliang.copymanga_neo.domain.model.ReaderPreferences
import org.junit.Test
import org.junit.Assert.*
class ReaderComicLayoutTest {
    @Test fun defaultsAndInvalidValuesUseSinglePage() {
        assertEquals("SINGLE",ReaderPreferences().normalized().pageLayout)
        assertEquals("SINGLE",ReaderPreferences(pageLayout="SINGLE").normalized().pageLayout)
        assertEquals("SINGLE",ReaderPreferences(pageLayout="broken").normalized().pageLayout)
    }
}
