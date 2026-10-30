package com.pasajeya.app.feature.trip

import com.pasajeya.app.feature.trip.domain.GpsFix
import com.pasajeya.app.feature.trip.domain.TripDistanceTracker
import org.junit.Assert.assertEquals
import org.junit.Test

class TripDistanceTrackerTest {

    private fun fix(lat: Double, seconds: Long, accuracy: Double? = 5.0) =
        GpsFix(lat = lat, lng = -71.5, accuracyMeters = accuracy, timeMillis = seconds * 1000)

    @Test
    fun `la primera lectura solo fija el punto de partida`() {
        val tracker = TripDistanceTracker()
        assertEquals(0.0, tracker.onFix(fix(-16.400, 0)), 0.0)
        assertEquals(0.0, tracker.distanceMeters, 0.0)
    }

    @Test
    fun `acumula tramos haversine entre lecturas`() {
        val tracker = TripDistanceTracker()
        tracker.onFix(fix(0.0000, 0))
        tracker.onFix(fix(0.0010, 10)) // ~111 m
        tracker.onFix(fix(0.0020, 20)) // ~111 m
        assertEquals(222.4, tracker.distanceMeters, 1.0)
    }

    @Test
    fun `descarta lecturas imprecisas`() {
        val tracker = TripDistanceTracker()
        tracker.onFix(fix(0.0000, 0))
        tracker.onFix(fix(0.0010, 10, accuracy = 120.0))
        assertEquals(0.0, tracker.distanceMeters, 0.0)
    }

    @Test
    fun `ignora el ruido del gps cuando el vehiculo esta quieto`() {
        val tracker = TripDistanceTracker()
        tracker.onFix(fix(0.00000, 0))
        tracker.onFix(fix(0.00001, 5)) // ~1 m
        tracker.onFix(fix(0.00002, 10)) // ~2 m desde el ancla
        assertEquals(0.0, tracker.distanceMeters, 0.0)
    }

    @Test
    fun `descarta saltos imposibles de velocidad`() {
        val tracker = TripDistanceTracker()
        tracker.onFix(fix(0.0, 0))
        tracker.onFix(fix(0.1, 1)) // 11 km en 1 s
        assertEquals(0.0, tracker.distanceMeters, 0.0)
    }

    @Test
    fun `reset vuelve a cero`() {
        val tracker = TripDistanceTracker()
        tracker.onFix(fix(0.0, 0))
        tracker.onFix(fix(0.001, 10))
        tracker.reset()
        assertEquals(0.0, tracker.distanceMeters, 0.0)
    }
}
