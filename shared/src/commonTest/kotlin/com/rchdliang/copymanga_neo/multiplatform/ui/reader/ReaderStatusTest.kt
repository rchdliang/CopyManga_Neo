package com.rchdliang.copymanga_neo.multiplatform.ui.reader

import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class ReaderStatusTest {
    @Test fun batteryLevelsCoverEmptyThroughFullWithoutShowingFullEarly() {
        val ranges = listOf(
            0..14 to ReaderBatteryGlyph.EMPTY, 15..28 to ReaderBatteryGlyph.BAR_1,
            29..42 to ReaderBatteryGlyph.BAR_2, 43..57 to ReaderBatteryGlyph.BAR_3,
            58..71 to ReaderBatteryGlyph.BAR_4, 72..85 to ReaderBatteryGlyph.BAR_5,
            86..99 to ReaderBatteryGlyph.BAR_6, 100..100 to ReaderBatteryGlyph.FULL
        )
        for ((range, glyph) in ranges) for (level in range) {
            assertEquals(glyph, ReaderBatteryStatus(level).glyph(), "电量 $level")
        }
    }

    @Test fun chargingTakesPriorityAndInvalidLevelsStayUnknown() {
        for (level in listOf(null, -1, 101)) {
            assertEquals(ReaderBatteryGlyph.UNKNOWN, ReaderBatteryStatus(level).glyph())
            assertEquals(null, ReaderBatteryStatus(level).validPercentage)
        }
        for (level in listOf(null, 0, 52, 100)) {
            assertEquals(ReaderBatteryGlyph.CHARGING, ReaderBatteryStatus(level, charging = true).glyph())
        }
    }

    @Test fun clockUsesLocalTimeAcrossMidnightAndPadsDigits() {
        val instant = Instant.parse("2026-10-29T16:05:00Z")
        assertEquals("00:05", readerClockLabel(instant, TimeZone.of("Asia/Shanghai")))
        assertEquals("16:05", readerClockLabel(instant, TimeZone.UTC))
    }

    @Test fun singleAndSpreadPageNumbersHaveSpacesAroundSlash() {
        assertEquals("7 / 24", readerRestingPageLabel("7", 24))
        assertEquals("7–8 / 24", readerRestingPageLabel("7–8", 24))
    }
}
