package com.pasajeya.app.feature.fare

import com.pasajeya.app.feature.fare.domain.FareEstimator
import com.pasajeya.app.feature.fare.domain.RouteFareContext
import com.pasajeya.app.feature.fare.domain.RouteGeo
import com.pasajeya.app.feature.fare.domain.RouteStop
import com.pasajeya.app.feature.trip.domain.FareVersionInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FareEstimatorTest {

    // Paraderos sobre un meridiano: cada 0.01° de latitud son ~1112 m.
    private val stops = listOf(
        RouteStop("A", 0.00, -71.5),
        RouteStop("B", 0.01, -71.5),
        RouteStop("C", 0.02, -71.5),
        RouteStop("D", 0.10, -71.5)
    )

    /** Tarifa del día con índice de combustible 1.13: la que escribe el script diario. */
    private fun context(
        official: Int = 330,
        boarding: Int = 68,
        perKm: Int = 10,
        min: Int = 100,
        max: Int = 150
    ) = RouteFareContext(
        route = RouteGeo("r1", "Ruta 1", stops, official, official / 2),
        version = FareVersionInput(
            boardingFareCents = boarding,
            baseFarePerKmCents = perKm,
            minFareCents = min,
            maxFareCents = max,
            fuelIndex = 1.13,
            roundingStepCents = 10
        )
    )

    @Test
    fun `la distancia entre paraderos suma los tramos haversine`() {
        val meters = FareEstimator.routeDistanceMeters(stops, 0, 2)
        assertEquals(2_223.9, meters, 2.0)
    }

    @Test
    fun `el orden de los paraderos no cambia la distancia`() {
        assertEquals(
            FareEstimator.routeDistanceMeters(stops, 0, 2),
            FareEstimator.routeDistanceMeters(stops, 2, 0),
            0.0001
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `indice fuera de rango falla`() {
        FareEstimator.routeDistanceMeters(stops, 0, 9)
    }

    @Test
    fun `cobra banderazo mas distancia redondeado al paso`() {
        // 5 km: 68 + 5 x 10 = 118 -> 120
        val fare = FareEstimator.fareForDistance(context(), 5_000.0, false)
        assertEquals(120, fare.totalCents)
        assertEquals(68, fare.boardingCents)
        assertEquals(50, fare.distanceCents)
        assertNull(fare.benefit)
    }

    @Test
    fun `el pasaje sube de diez en diez centimos por kilometro`() {
        val cents = listOf(4, 5, 6, 7).map {
            FareEstimator.fareForDistance(context(), it * 1_000.0, false).totalCents
        }
        assertEquals(listOf(110, 120, 130, 140), cents)
    }

    @Test
    fun `los tramos cortos pagan la tarifa minima`() {
        val fare = FareEstimator.fareForDistance(context(), 1_000.0, false)
        assertEquals(100, fare.totalCents)
        assertTrue(fare.minApplied)
    }

    @Test
    fun `distancia cero cobra la tarifa minima`() {
        assertEquals(100, FareEstimator.fareForDistance(context(), 0.0, false).totalCents)
    }

    @Test
    fun `el tope del dia corta el pasaje aunque la ruta permita mas`() {
        // 20 km darian 268, pero el tope de la version vigente es 150.
        val fare = FareEstimator.fareForDistance(context(official = 330), 20_000.0, false)
        assertEquals(150, fare.totalCents)
        assertTrue(fare.capped)
    }

    @Test
    fun `manda el tope mas bajo entre la ruta y la version`() {
        val routeCheaper = FareEstimator.fareForDistance(context(official = 120), 20_000.0, false)
        assertEquals(120, routeCheaper.totalCents)
    }

    @Test
    fun `el tope sube cuando sube el combustible`() {
        // Con diesel mas caro el script publica banderazo, km, minimo y tope mas altos.
        val expensive = context(boarding = 76, perKm = 11, min = 110, max = 170)
        assertEquals(170, FareEstimator.fareForDistance(expensive, 20_000.0, false).totalCents)
        assertEquals(110, FareEstimator.fareForDistance(expensive, 1_000.0, false).totalCents)
    }

    @Test
    fun `el universitario nunca paga mas de la mitad`() {
        listOf(1, 4, 5, 6, 7, 20).forEach { km ->
            val regular = FareEstimator.fareForDistance(context(), km * 1_000.0, false)
            val student = FareEstimator.fareForDistance(context(), km * 1_000.0, true)
            assertNotNull(student.benefit)
            assertEquals(50, student.benefit!!.percent)
            assertEquals(regular.totalCents, student.regularCents)
            assertTrue(student.totalCents * 2 <= regular.totalCents)
        }
    }

    @Test
    fun `el universitario paga al menos la mitad del minimo`() {
        val student = FareEstimator.fareForDistance(context(), 0.0, true)
        assertEquals(50, student.totalCents)
        assertFalse(student.totalCents > student.regularCents)
    }

    @Test
    fun `el techo de cordura aplica cuando la ruta no declara pasaje oficial`() {
        assertEquals(330, FareEstimator.ceilingCents(context(official = 500).route))
        assertEquals(330, FareEstimator.ceilingCents(context(official = 0).route))
        assertEquals(250, FareEstimator.ceilingCents(context(official = 250).route))
    }
}
