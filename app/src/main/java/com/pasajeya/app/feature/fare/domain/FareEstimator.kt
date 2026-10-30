package com.pasajeya.app.feature.fare.domain

import com.pasajeya.app.core.util.Haversine
import com.pasajeya.app.feature.trip.domain.FareCalculator
import com.pasajeya.app.feature.trip.domain.FareResult
import kotlin.math.max
import kotlin.math.min

data class FareEstimate(
    val boardingStopIndex: Int,
    val alightingStopIndex: Int,
    val fare: FareResult
)

object FareEstimator {

    /** Techo de seguridad global del pasaje, en céntimos. */
    const val SAFETY_CEILING_CENTS = 330

    /**
     * Distancia a lo largo de la ruta entre dos paraderos: suma de tramos consecutivos
     * calculados con Haversine. El orden de los índices no importa.
     */
    fun routeDistanceMeters(stops: List<RouteStop>, from: Int, to: Int): Double {
        require(stops.isNotEmpty()) { "La ruta no tiene paraderos con coordenadas." }
        val start = min(from, to)
        val end = max(from, to)
        require(start >= 0 && end < stops.size) { "Los índices de paradero no son válidos." }

        var total = 0.0
        for (i in start until end) {
            val a = stops[i]
            val b = stops[i + 1]
            total += Haversine.distanceMeters(a.lat, a.lng, b.lat, b.lng)
        }
        return total
    }

    /** Tope del cálculo: pasaje oficial de la ruta, sin pasar del techo de seguridad. */
    fun ceilingCents(route: RouteGeo): Int =
        if (route.officialFareCents > 0) {
            min(route.officialFareCents, SAFETY_CEILING_CENTS)
        } else {
            SAFETY_CEILING_CENTS
        }

    /** Tarifa para una distancia dada: distancia_km × base × índice de combustible. */
    fun fareForDistance(
        context: RouteFareContext,
        distanceMeters: Double,
        hasUniversityBenefit: Boolean
    ): FareResult = FareCalculator.computeFare(
        distanceMeters = distanceMeters,
        version = context.version.copy(maxFareCents = ceilingCents(context.route)),
        hasUniversityBenefit = hasUniversityBenefit
    )

    /** Estimación según paradero de origen y destino usando la distancia real entre coordenadas. */
    fun estimateFare(
        context: RouteFareContext,
        boardingStopIndex: Int,
        alightingStopIndex: Int,
        hasUniversityBenefit: Boolean
    ): FareEstimate {
        val distance = routeDistanceMeters(
            context.route.stops,
            boardingStopIndex,
            alightingStopIndex
        )
        return FareEstimate(
            boardingStopIndex = boardingStopIndex,
            alightingStopIndex = alightingStopIndex,
            fare = fareForDistance(context, distance, hasUniversityBenefit)
        )
    }
}
