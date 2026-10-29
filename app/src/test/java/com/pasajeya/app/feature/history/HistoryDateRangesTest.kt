package com.pasajeya.app.feature.history

import com.pasajeya.app.feature.history.domain.HistoryDateRanges
import com.pasajeya.app.feature.history.domain.HistoryFilter
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HistoryDateRangesTest {

    @Test
    fun `week starts on monday at lima midnight`() {
        val sunday = Instant.parse("2026-10-26T04:59:00Z")
        val monday = Instant.parse("2026-10-26T05:00:00Z")

        val sundayRange = HistoryDateRanges.week(sunday)
        val mondayRange = HistoryDateRanges.week(monday)

        assertEquals(
            Instant.parse("2026-10-19T05:00:00Z").toEpochMilli(),
            sundayRange.startInclusiveMillis
        )
        assertEquals(
            Instant.parse("2026-10-26T05:00:00Z").toEpochMilli(),
            sundayRange.endExclusiveMillis
        )
        assertEquals(
            Instant.parse("2026-10-26T05:00:00Z").toEpochMilli(),
            mondayRange.startInclusiveMillis
        )
    }

    @Test
    fun `week crosses the month change`() {
        val range = HistoryDateRanges.week(
            Instant.parse("2026-12-30T17:00:00Z")
        )

        assertEquals(
            Instant.parse("2026-12-28T05:00:00Z").toEpochMilli(),
            range.startInclusiveMillis
        )
        assertEquals(
            Instant.parse("2027-01-04T05:00:00Z").toEpochMilli(),
            range.endExclusiveMillis
        )
    }

    @Test
    fun `week crosses new year`() {
        val range = HistoryDateRanges.week(
            Instant.parse("2027-01-01T15:00:00Z")
        )

        assertEquals(
            Instant.parse("2026-12-28T05:00:00Z").toEpochMilli(),
            range.startInclusiveMillis
        )
        assertEquals(
            Instant.parse("2027-01-04T05:00:00Z").toEpochMilli(),
            range.endExclusiveMillis
        )
    }

    @Test
    fun `month changes at lima midnight`() {
        val october = HistoryDateRanges.month(
            Instant.parse("2026-11-01T04:59:59Z")
        )
        val november = HistoryDateRanges.month(
            Instant.parse("2026-11-01T05:00:00Z")
        )

        assertEquals(
            Instant.parse("2026-10-01T05:00:00Z").toEpochMilli(),
            october.startInclusiveMillis
        )
        assertEquals(
            Instant.parse("2026-11-01T05:00:00Z").toEpochMilli(),
            october.endExclusiveMillis
        )
        assertEquals(
            Instant.parse("2026-11-01T05:00:00Z").toEpochMilli(),
            november.startInclusiveMillis
        )
        assertEquals(
            Instant.parse("2026-12-01T05:00:00Z").toEpochMilli(),
            november.endExclusiveMillis
        )
    }

    @Test
    fun `all time has no date range`() {
        assertNull(
            HistoryDateRanges.range(
                HistoryFilter.ALL,
                Instant.parse("2026-10-29T12:00:00Z")
            )
        )
    }
}
