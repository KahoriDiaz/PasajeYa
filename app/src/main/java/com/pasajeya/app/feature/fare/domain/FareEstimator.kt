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

    /** Techo de seguridad utilizado por la billetera para retenciones previas al viaje (S/ 5.00) */
    const val SAFETY_CEILING_CENTS = 500

    /** Tope máximo aceptado para el cálculo tarifario de una ruta (S/ 3.30) */
    const val MAX_FARE_CEILING_CENTS = 330

    fun ceilingCents(route: RouteGeo): Int {
        val official = route.officialFareCents
            .takeIf { it > 0 }
            ?: MAX_FARE_CEILING_CENTS
        return min(official, MAX_FARE_CEILING_CENTS)
    }

    fun routeDistanceMeters(stops: List<RouteStop>, from: Int, to: Int): Double {
        if (stops.isEmpty()) return 0.0
        if (from !in stops.indices) throw IllegalArgumentException("Índice de subida fuera de rango.")
        if (to !in stops.indices) throw IllegalArgumentException("Índice de bajada fuera de rango.")
        if (from == to) return 0.0
        val start = min(from, to)
        val end = max(from, to)

        var total = 0.0
        for (i in start until end) {
            val a = stops[i]
            val b = stops[i + 1]
            total += Haversine.distanceMeters(a.lat, a.lng, b.lat, b.lng)
        }
        return total
    }

    fun fareForDistance(
        context: RouteFareContext,
        distanceMeters: Double,
        hasUniversityBenefit: Boolean
    ): FareResult = FareCalculator.computeFare(
        distanceMeters = distanceMeters,
        version = context.version,
        hasUniversityBenefit = hasUniversityBenefit,
        officialFareCents = ceilingCents(context.route)
    )

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
