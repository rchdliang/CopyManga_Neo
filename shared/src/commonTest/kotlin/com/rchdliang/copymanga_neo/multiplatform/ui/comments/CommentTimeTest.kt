package com.rchdliang.copymanga_neo.multiplatform.ui.comments

import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class CommentTimeTest {
    private val now = CommentMoment(
        Instant.parse("2026-10-01T16:15:00+08:00"), TimeZone.of("Asia/Shanghai")
    )

    @Test fun relativeThenCalendarFormatting() {
        assertEquals("刚刚", CommentTime.format("2026-10-01 16:14:59", now))
        assertEquals("10分钟前", CommentTime.format("2026-10-01 16:05:00", now))
        assertEquals("15:15", CommentTime.format("2026-10-01 15:15:00", now))
        assertEquals("昨天 09:00", CommentTime.format("2026-09-30 09:00:00", now))
        assertEquals("09-28", CommentTime.format("2026-09-28 13:08:00", now))
        assertEquals("2024-03-20", CommentTime.format("2024-03-20 13:08:00", now))
    }

    @Test fun normalizesOffsetAndEpochFormats() {
        val time = Instant.parse("2026-10-01T08:05:00Z")
        assertEquals("10分钟前", CommentTime.format(time.toString(), now))
        assertEquals("10分钟前", CommentTime.format(time.epochSeconds.toString(), now))
        assertEquals("10分钟前", CommentTime.format(time.toEpochMilliseconds().toString(), now))
        assertEquals("00:00", CommentTime.format("2026-10-01", now))
    }

    @Test fun futureAndInvalidTimesAreNotMistakenForJustNow() {
        assertEquals("17:00", CommentTime.format("2026-10-01 17:00:00", now))
        assertEquals("时间未知", CommentTime.format("not a timestamp", now))
        assertEquals("时间未知", CommentTime.format("2026-99-99", now))
    }

    @Test fun previousYearAndDayBoundaryRespectDisplayTimezone() {
        val newYear = CommentMoment(
            Instant.parse("2027-01-01T00:30:00+08:00"), TimeZone.of("Asia/Shanghai")
        )
        assertEquals("昨天 21:00", CommentTime.format("2026-12-31T13:00:00Z", newYear))
        assertEquals("2026-12-30", CommentTime.format("2026-12-30 23:00:00", newYear))
    }
}
