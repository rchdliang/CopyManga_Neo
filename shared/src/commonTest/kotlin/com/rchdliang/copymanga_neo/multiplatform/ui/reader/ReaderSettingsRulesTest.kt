package com.rchdliang.copymanga_neo.multiplatform.ui.reader
import kotlin.test.Test
import kotlin.test.*
class ReaderSettingsRulesTest {
    @Test fun backLeavesChildFirstAndHomeCanStillClose() {
        for (page in listOf("本章配页", "阅读操作", "画质增强", "显示设置")) {
            var child: String? = page
            var dismisses = 0
            val back = { readerSettingsBack(child != null, { child = null }, { dismisses++ }) }
            back()
            assertNull(child)
            assertEquals(0, dismisses)
            back()
            assertEquals(1, dismisses)
        }
    }
    @Test fun rtlCounterUsesOriginalAscendingRangeAndPairingRejectsBadBoundaries() {
        assertEquals("2–3 / 24", readerPageRangeLabel(listOf(3,2),24))
        assertEquals("1 / 24", readerPageRangeLabel(listOf(1),24))
        assertNull(validPairingInput("x",24));assertNull(validPairingInput("0",24));assertNull(validPairingInput("24",24))
        assertEquals(22,validPairingInput("23",24));assertNull(validPairingInput("1",1))
        assertEquals("连续阅读使用单页",readerLayoutStatus("DOUBLE",true,true))
        assertEquals("当前使用单页 · 窗口较宽时显示双页",readerLayoutStatus("AUTO",false,false))
    }
}
