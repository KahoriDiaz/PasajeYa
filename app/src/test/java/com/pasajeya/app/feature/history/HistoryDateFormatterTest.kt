package com.pasajeya.app.feature.history

import com.pasajeya.app.feature.history.domain.HistoryDateFormatter
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryDateFormatterTest {

    @Test
    fun `formats in spanish with lima time`() {
        val millis = Instant.parse("2025-10-20T13:15:00Z").toEpochMilli()

        assertEquals("Lun 20 oct · 08:15", HistoryDateFormatter.format(millis))
    }

    @Test
    fun `uses the lima day even when utc already changed`() {
        val millis = Instant.parse("2026-10-20T02:00:00Z").toEpochMilli()

        assertEquals("Lun 19 oct · 21:00", HistoryDateFormatter.format(millis))
    }
}
