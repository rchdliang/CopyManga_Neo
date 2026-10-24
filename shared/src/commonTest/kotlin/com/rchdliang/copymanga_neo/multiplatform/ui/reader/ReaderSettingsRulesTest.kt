package com.rchdliang.copymanga_neo.multiplatform.ui.reader
import kotlin.test.Test
import kotlin.test.*
class ReaderSettingsRulesTest {
    @Test fun rtlCounterUsesOriginalAscendingRangeAndPairingRejectsBadBoundaries() {
        assertEquals("2–3 / 24", readerPageRangeLabel(listOf(3,2),24))
        assertEquals("1 / 24", readerPageRangeLabel(listOf(1),24))
        assertNull(validPairingInput("x",24));assertNull(validPairingInput("0",24));assertNull(validPairingInput("24",24))
        assertEquals(22,validPairingInput("23",24));assertNull(validPairingInput("1",1))
        assertEquals("连续阅读使用单页",readerLayoutStatus("DOUBLE",true,true))
        assertEquals("当前使用单页 · 窗口较宽时显示双页",readerLayoutStatus("AUTO",false,false))
    }
}
