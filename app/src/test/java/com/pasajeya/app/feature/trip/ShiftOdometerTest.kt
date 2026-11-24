package com.pasajeya.app.feature.trip

import com.pasajeya.app.feature.fare.domain.RouteStop
import com.pasajeya.app.feature.trip.data.odometerMetersAt
import com.pasajeya.app.feature.trip.data.travelledMeters
import com.pasajeya.app.feature.trip.domain.RoutePath
import com.pasajeya.app.feature.trip.domain.model.Shift
import com.pasajeya.app.feature.trip.domain.model.Trip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShiftOdometerTest {

    // Cuatro paraderos sobre un meridiano: cada 0.01° de latitud son ~1112 m.
    private val route = RoutePath(
        id = "r1",
        name = "Ruta 1",
        stops = listOf(
            RouteStop("A", 0.00, -71.5),
            RouteStop("B", 0.01, -71.5),
            RouteStop("C", 0.02, -71.5),
            RouteStop("D", 0.03, -71.5)
        )
    )

    private val routeLength = 3 * 1112.0

    private fun trip(boardingOdometer: Double?, boardingIndex: Int) = Trip(
        id = "t1",
        passengerUid = "p1",
        operatorUid = "o1",
        shiftId = "s1",
        routeId = "r1",
        status = "open",
        reviewReason = null,
        startedAtMillis = 0L,
        endedAtMillis = null,
        distanceMeters = 0.0,
        passengerFirstName = "Ana",
        passengerAge = 20,
        boardingStopIndex = boardingIndex,
        boardingOdometerMeters = boardingOdometer
    )

    private fun shift(odometer: Double) = Shift(
        id = "s1",
        operatorUid = "o1",
        plate = "V7D123",
        routeId = "r1",
        startedAtMillis = 0L,
        endedAtMillis = null,
        status = "active",
        odometerMeters = odometer
    )

    @Test
    fun `el odometro es lo andado desde el primer paradero`() {
        assertEquals(0.0, odometerMetersAt(route, 0, 0.0), 0.0)
        assertEquals(1112.0, odometerMetersAt(route, 1, 0.0), 3.0)
        assertEquals(routeLength, odometerMetersAt(route, 3, 0.0), 5.0)
    }

    @Test
    fun `el mismo aviso repetido no suma dos veces`() {
        val once = odometerMetersAt(route, 2, 0.0)
        val twice = odometerMetersAt(route, 2, 0.0)
        assertEquals(once, twice, 0.0)
        assertEquals(2224.0, once, 3.0)
    }

    @Test
    fun `al volver a empezar el odometro sigue desde donde iba`() {
        // La unidad termino el recorrido (routeLength) y arranca de nuevo.
        assertEquals(routeLength, odometerMetersAt(route, 0, routeLength), 5.0)
        assertEquals(routeLength + 1112.0, odometerMetersAt(route, 1, routeLength), 5.0)
    }

    @Test
    fun `un viaje nunca mide mas que la ruta de ida y vuelta`() {
        val meters = travelledMeters(
            trip = trip(boardingOdometer = 0.0, boardingIndex = 0),
            shift = shift(odometer = 100_000.0),
            route = route,
            boardingIndex = 0,
            alightingIndex = 1
        )
        assertEquals(routeLength * 2, meters, 5.0)
    }

    @Test
    fun `el viaje se mide con la diferencia del odometro`() {
        val meters = travelledMeters(
            trip = trip(boardingOdometer = 1112.0, boardingIndex = 1),
            shift = shift(odometer = 1112.0 + routeLength),
            route = route,
            boardingIndex = 1,
            alightingIndex = 1
        )
        // Subio y bajo en el mismo paradero, pero la combi dio la vuelta completa.
        assertEquals(routeLength, meters, 5.0)
    }

    @Test
    fun `sin odometro se mide entre paraderos`() {
        val meters = travelledMeters(
            trip = trip(boardingOdometer = null, boardingIndex = 0),
            shift = shift(odometer = 0.0),
            route = route,
            boardingIndex = 0,
            alightingIndex = 2
        )
        assertEquals(2224.0, meters, 3.0)
        assertTrue(meters > 0.0)
    }
}
