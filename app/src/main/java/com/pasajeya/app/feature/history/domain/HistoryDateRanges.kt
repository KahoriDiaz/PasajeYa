package com.pasajeya.app.feature.history.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

data class HistoryDateRange(
    val startInclusiveMillis: Long,
    val endExclusiveMillis: Long
)

object HistoryDateRanges {
    val LIMA_ZONE: ZoneId = ZoneId.of("America/Lima")

    fun range(
        filter: HistoryFilter,
        now: Instant = Instant.now(),
        zone: ZoneId = LIMA_ZONE
    ): HistoryDateRange? = when (filter) {
        HistoryFilter.WEEK -> week(now, zone)
        HistoryFilter.MONTH -> month(now, zone)
        HistoryFilter.ALL -> null
    }

    fun week(
        now: Instant = Instant.now(),
        zone: ZoneId = LIMA_ZONE
    ): HistoryDateRange {
        val today = now.atZone(zone).toLocalDate()
        val monday = today.with(
            TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)
        )
        return rangeStartingOn(monday, zone, days = 7)
    }

    fun month(
        now: Instant = Instant.now(),
        zone: ZoneId = LIMA_ZONE
    ): HistoryDateRange {
        val today = now.atZone(zone).toLocalDate()
        val firstDay = today.withDayOfMonth(1)
        val start = firstDay.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = firstDay.plusMonths(1)
            .atStartOfDay(zone)
            .toInstant()
            .toEpochMilli()
        return HistoryDateRange(start, end)
    }

    private fun rangeStartingOn(
        date: LocalDate,
        zone: ZoneId,
        days: Long
    ): HistoryDateRange {
        val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = date.plusDays(days)
            .atStartOfDay(zone)
            .toInstant()
            .toEpochMilli()
        return HistoryDateRange(start, end)
    }
}
