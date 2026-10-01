package com.rchdliang.copymanga_neo.ui.comments

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class CommentTimeTest {
    private val now = ZonedDateTime.of(2026, 10, 1, 13, 30, 0, 0, ZoneId.of("Asia/Shanghai"))

    @Test
    fun usesRequestedTimeFormats() {
        assertEquals("10分钟前", CommentTime.format("2026-10-01 13:20:00", now))
        assertEquals("13:08", CommentTime.format("2026-10-01 13:08:00", now.plusHours(1)))
        assertEquals("昨天 09:00", CommentTime.format("2026-09-30 09:00:00", now))
        assertEquals("09-30", CommentTime.format("2026-09-30 09:00:00", now.plusDays(1)))
        assertEquals("2024-03-20", CommentTime.format("2024-03-20 18:30:00", now))
    }

    @Test
    fun handlesMinuteAndHourBoundaries() {
        assertEquals("刚刚", CommentTime.format("2026-10-01 13:29:30", now))
        assertEquals("59分钟前", CommentTime.format("2026-10-01 12:30:01", now))
        assertEquals("12:30", CommentTime.format("2026-10-01 12:30:00", now))
        assertEquals("13:31", CommentTime.format("2026-10-01 13:31:00", now))
    }

    @Test
    fun relativeTimeCrossesMidnightAndYesterdayCrossesYear() {
        val midnight = now.withHour(0).withMinute(5)
        assertEquals("10分钟前", CommentTime.format("2026-09-30 23:55:00", midnight))
        val newYear = now.withMonth(1).withDayOfMonth(1)
        assertEquals("昨天 09:00", CommentTime.format("2025-12-31 09:00:00", newYear))
    }

    @Test
    fun normalizesOffsetsAndEpochs() {
        val date = now.minusMinutes(10)
        assertEquals("10分钟前", CommentTime.format("2026-10-01T05:20:00Z", now))
        assertEquals("10分钟前", CommentTime.format(date.toEpochSecond().toString(), now))
        assertEquals(
            "10分钟前",
            CommentTime.format(date.toInstant().toEpochMilli().toString(), now)
        )
        assertEquals("10分钟前", CommentTime.format("2026-10-01 13:20:00.000", now))
    }

    @Test
    fun malformedTimeDoesNotLeakRawData() {
        assertEquals("时间未知", CommentTime.format("invalid time", now))
        assertEquals("时间未知", CommentTime.format("", now))
    }
}
