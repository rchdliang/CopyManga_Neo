package com.rchdliang.copymanga_neo.multiplatform.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import com.rchdliang.copymanga_neo.multiplatform.ui.reader.ReaderBatteryStatus

class DesktopReaderBatteryTest {
    @Test fun noBatteryAndUnknownValuesProduceUnknownState() {
        assertEquals(ReaderBatteryStatus(), windowsBatteryStatus(128, 100))
        assertEquals(ReaderBatteryStatus(), windowsBatteryStatus(255, 100))
        assertEquals(ReaderBatteryStatus(), windowsBatteryStatus(0, 255))
        assertEquals(ReaderBatteryStatus(), windowsBatteryStatus(136, 100))
    }

    @Test fun validLevelsIncludeEmptyFullAndCharging() {
        assertEquals(ReaderBatteryStatus(0), windowsBatteryStatus(4, 0))
        assertEquals(ReaderBatteryStatus(100), windowsBatteryStatus(1, 100))
        assertEquals(ReaderBatteryStatus(52, charging = true), windowsBatteryStatus(8, 52))
        assertEquals(ReaderBatteryStatus(charging = true), windowsBatteryStatus(8, 255))
    }
}
