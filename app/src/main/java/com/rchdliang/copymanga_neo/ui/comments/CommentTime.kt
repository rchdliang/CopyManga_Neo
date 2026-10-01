package com.rchdliang.copymanga_neo.ui.comments

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** 一小时内显示相对时间；更早的评论按日、年缩短日期。 */
internal object CommentTime {
    private val time = DateTimeFormatter.ofPattern("HH:mm")
    private val monthDay = DateTimeFormatter.ofPattern("MM-dd")
    private val fullDate = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    fun format(raw: String, now: ZonedDateTime = ZonedDateTime.now()): String {
        val created = parse(raw, now.zone) ?: return "时间未知"
        val date = created.toLocalDate()
        val today = now.toLocalDate()
        val elapsed = Duration.between(created, now)
        return when {
            !elapsed.isNegative && elapsed < Duration.ofHours(1) ->
                if (elapsed.toMinutes() == 0L) "刚刚" else "${elapsed.toMinutes()}分钟前"

            date == today -> created.format(time)
            date == today.minusDays(1) -> "昨天 ${created.format(time)}"
            date.year == today.year -> created.format(monthDay)
            else -> created.format(fullDate)
        }
    }

    private fun parse(raw: String, zone: ZoneId): ZonedDateTime? {
        val value = raw.trim().replace(' ', 'T')
        return try {
            OffsetDateTime.parse(value).atZoneSameInstant(zone)
        } catch (_: java.time.format.DateTimeParseException) {
            try {
                LocalDateTime.parse(value).atZone(zone)
            } catch (_: java.time.format.DateTimeParseException) {
                try {
                    LocalDate.parse(value).atStartOfDay(zone)
                } catch (_: java.time.format.DateTimeParseException) {
                    val timestamp = value.toLongOrNull() ?: return null
                    try {
                        (if (value.length <= 10) Instant.ofEpochSecond(timestamp) else Instant.ofEpochMilli(
                            timestamp
                        )).atZone(zone)
                    } catch (_: java.time.DateTimeException) {
                        null
                    }
                }
            }
        }
    }
}
