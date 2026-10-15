package com.rchdliang.copymanga_neo.multiplatform.ui.comments

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/** An instant and display zone, so both runtime and fixed previews use the same date policy. */
internal data class CommentMoment(val instant: Instant, val zone: TimeZone) {
    companion object {
        fun now(): CommentMoment = CommentMoment(Clock.System.now(), TimeZone.currentSystemDefault())
    }
}

/** Original policy: relative under an hour, time today, yesterday, month-day, then full date. */
internal object CommentTime {
    fun format(raw: String, now: CommentMoment = CommentMoment.now()): String {
        val instant = parse(raw, now.zone) ?: return "时间未知"
        val created = instant.toLocalDateTime(now.zone)
        val date = created.date
        val today = now.instant.toLocalDateTime(now.zone).date
        val elapsed = now.instant - instant
        val time = "${created.hour.twoDigits()}:${created.minute.twoDigits()}"
        return when {
            elapsed >= Duration.ZERO && elapsed < 1.hours ->
                if (elapsed.inWholeMinutes == 0L) "刚刚" else "${elapsed.inWholeMinutes}分钟前"
            date == today -> time
            date == today.minus(DatePeriod(days = 1)) -> "昨天 $time"
            date.year == today.year -> "${date.month.ordinal.plus(1).twoDigits()}-${date.day.twoDigits()}"
            else -> date.toString()
        }
    }

    private fun Int.twoDigits() = toString().padStart(2, '0')

    private fun parse(raw: String, zone: TimeZone): Instant? {
        val value = raw.trim().replace(' ', 'T')
        runCatching { Instant.parse(value) }.getOrNull()?.let { return it }
        runCatching { LocalDateTime.parse(value).toInstant(zone) }.getOrNull()?.let { return it }
        runCatching { LocalDate.parse(value).atStartOfDayIn(zone) }.getOrNull()?.let { return it }
        val timestamp = value.toLongOrNull() ?: return null
        return runCatching {
            if (value.length <= 10) Instant.fromEpochSeconds(timestamp)
            else Instant.fromEpochMilliseconds(timestamp)
        }.getOrNull()
    }
}
