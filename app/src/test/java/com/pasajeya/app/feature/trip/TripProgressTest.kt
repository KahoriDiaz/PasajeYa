package com.pasajeya.app.feature.trip

import com.pasajeya.app.feature.fare.domain.RouteStop
import com.pasajeya.app.feature.trip.domain.TripProgress
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * La medicion tiene que dar lo mismo en los tres lados (mapa del pasajero, lista del
 * conductor y cobro del Tap Out), con el modo demostracion encendido y apagado.
 */
class TripProgressTest {

    // Cuatro paraderos sobre un meridiano: cada 0.01 de latitud son ~1112 m.
    private val stops = listOf(
        RouteStop("P1", 0.00, -71.56),
        RouteStop("P2", 0.01, -71.56),
        RouteStop("P3", 0.02, -71.56),
        RouteStop("P4", 0.03, -71.56)
    )

    private val stopMeters = 1112.0
    private val routeMeters = stopMeters * 3

    @Test
    fun `el largo de la ruta es la suma de sus tramos`() {
        assertEquals(routeMeters, TripProgress.routeMeters(stops), 5.0)
    }

    @Test
    fun `en demostracion los tres lados miden lo mismo`() {
        // La persona subio en el segundo paradero y la unidad va por el cuarto.
        val medicion = { stopIndex: Int ->
            TripProgress.tripMeters(
                stops = stops,
                simulationEnabled = true,
                boardingStopIndex = 1,
                stopIndex = stopIndex,
                boardingLaps = 0,
                laps = 0,
                boardingOdometerMeters = 0.0,
                unitOdometerMeters = 0.0
            )
        }
        assertEquals(0.0, medicion(1), 0.0)
        assertEquals(stopMeters, medicion(2), 3.0)
        assertEquals(stopMeters * 2, medicion(3), 3.0)
    }

    @Test
    fun `en demostracion la vuelta completa se suma una sola vez`() {
        val meters = TripProgress.tripMeters(
            stops = stops,
            simulationEnabled = true,
            boardingStopIndex = 1,
            stopIndex = 1,
            boardingLaps = 0,
            laps = 1
        )
        assertEquals(routeMeters, meters, 5.0)
    }

    @Test
    fun `en marcha real manda el odometro de la unidad`() {
        val meters = TripProgress.tripMeters(
            stops = stops,
            simulationEnabled = false,
            boardingStopIndex = 1,
            stopIndex = 3,
            boardingOdometerMeters = 500.0,
            unitOdometerMeters = 500.0 + 2_000.0
        )
        assertEquals(2_000.0, meters, 0.5)
    }

    @Test
    fun `en marcha real sin odometro se mide entre paraderos`() {
        val meters = TripProgress.tripMeters(
            stops = stops,
            simulationEnabled = false,
            boardingStopIndex = 1,
            stopIndex = 3,
            boardingOdometerMeters = 500.0,
            unitOdometerMeters = 500.0
        )
        assertEquals(stopMeters * 2, meters, 3.0)
    }

    @Test
    fun `recien subida la medicion es cero`() {
        val simulada = TripProgress.tripMeters(
            stops = stops,
            simulationEnabled = true,
            boardingStopIndex = 2,
            stopIndex = 2,
            boardingLaps = 0,
            laps = 0
        )
        val real = TripProgress.tripMeters(
            stops = stops,
            simulationEnabled = false,
            boardingStopIndex = 2,
            stopIndex = 2,
            boardingOdometerMeters = 1_000.0,
            unitOdometerMeters = 1_000.0
        )
        assertEquals(0.0, simulada, 0.0)
        assertEquals(0.0, real, 0.0)
    }

    @Test
    fun `ningun viaje mide mas que la ruta de ida y vuelta`() {
        val meters = TripProgress.tripMeters(
            stops = stops,
            simulationEnabled = false,
            boardingStopIndex = 0,
            stopIndex = 1,
            boardingOdometerMeters = 0.0,
            unitOdometerMeters = 100_000.0
        )
        assertEquals(routeMeters * 2, meters, 5.0)
    }
}
