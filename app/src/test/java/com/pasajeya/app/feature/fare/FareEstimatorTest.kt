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

    private fun context(official: Int = 330, base: Int = 120, fuel: Double = 1.0) =
        RouteFareContext(
            route = RouteGeo("r1", "Ruta 1", stops, official, official / 2),
            version = FareVersionInput(
                baseFarePerKmCents = base,
                fuelIndex = fuel,
                minFareCents = 50,
                roundingStepCents = 5
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
    fun `aplica distancia por tarifa base por indice de combustible y redondea al paso`() {
        // 1.112 km * 120 * 1.0 = 133.4 -> 133 -> 135
        val estimate = FareEstimator.estimateFare(context(), 0, 1, false)
        assertEquals(135, estimate.fare.totalCents)
        assertNull(estimate.fare.benefit)

        // 1.112 km * 120 * 1.5 = 200.1 -> 200
        val withFuel = FareEstimator.estimateFare(context(fuel = 1.5), 0, 1, false)
        assertEquals(200, withFuel.fare.totalCents)
    }

    @Test
    fun `respeta la tarifa minima`() {
        val estimate = FareEstimator.estimateFare(context(base = 10), 0, 1, false)
        assertEquals(50, estimate.fare.totalCents)
        assertTrue(estimate.fare.minApplied)
    }

    @Test
    fun `respeta el techo oficial de la ruta`() {
        // ~11.1 km * 120 = ~1334 -> tope oficial 250
        val estimate = FareEstimator.estimateFare(context(official = 250), 0, 3, false)
        assertEquals(250, estimate.fare.totalCents)
        assertTrue(estimate.fare.capped)
    }

    @Test
    fun `el techo de seguridad es 330 aunque la ruta pida mas o no tenga pasaje oficial`() {
        assertEquals(330, FareEstimator.ceilingCents(context(official = 500).route))
        assertEquals(330, FareEstimator.ceilingCents(context(official = 0).route))
        assertEquals(250, FareEstimator.ceilingCents(context(official = 250).route))
        val estimate = FareEstimator.estimateFare(context(official = 500), 0, 3, false)
        assertEquals(330, estimate.fare.totalCents)
    }

    @Test
    fun `el universitario paga el 50 por ciento`() {
        val regular = FareEstimator.estimateFare(context(), 0, 2, false).fare
        val student = FareEstimator.estimateFare(context(), 0, 2, true).fare
        assertNotNull(student.benefit)
        assertEquals(50, student.benefit!!.percent)
        assertEquals(regular.totalCents, student.regularCents)
        assertTrue(student.totalCents < regular.totalCents)
        assertEquals(
            Math.round(regular.totalCents / 2.0 / 5).toInt() * 5,
            student.totalCents
        )
    }

    @Test
    fun `el descuento universitario nunca supera al pasaje regular`() {
        val student = FareEstimator.fareForDistance(context(official = 250), 50_000.0, true)
        assertFalse(student.totalCents > student.regularCents)
        assertEquals(125, student.totalCents)
    }

    @Test
    fun `distancia cero cobra la tarifa minima`() {
        val fare = FareEstimator.fareForDistance(context(), 0.0, false)
        assertEquals(50, fare.totalCents)
    }
}
