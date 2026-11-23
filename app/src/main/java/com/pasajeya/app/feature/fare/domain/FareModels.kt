package com.pasajeya.app.feature.fare.domain

import com.pasajeya.app.feature.trip.domain.FareVersionInput
import kotlinx.coroutines.flow.Flow

data class RouteStop(
    val name: String = "",
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val id: String = "",
    val order: Int = 0
)

data class RouteGeo(
    val id: String = "",
    val name: String = "",
    val stops: List<RouteStop> = emptyList(),
    val officialFareCents: Int = 500,
    val officialUniversityFareCents: Int = 250
)

// Alias/clase de compatibilidad por si alguna vista usa Route
typealias Route = RouteGeo

data class FareBenefit(
    val type: String = "UNIVERSITY",
    val percent: Int = 50,
    val discountCents: Int = 0
)

data class FareVersion(
    val id: String = "v1",
    val baseFarePerKmCents: Int = 8,
    val minFareCents: Int = 130,
    val maxFareCents: Int = 200,
    val fuelIndex: Double = 1.13,
    val roundingStepCents: Int = 10
)

/** Ruta con su geometria y la version tarifaria vigente, lista para calcular. */
data class RouteFareContext(
    val route: RouteGeo,
    val version: FareVersionInput
)

interface FareRepository {
    /** Lee la ruta y la version tarifaria vigente desde Firestore. */
    suspend fun loadRouteFareContext(routeId: String): RouteFareContext

    /**
     * Version tarifaria vigente del dia, la que publica el script de combustible.
     * Es la misma que usa el cobro del viaje, para que estimacion y cobro coincidan.
     */
    suspend fun loadFareVersion(): FareVersionInput

    /** Emite en vivo (snapshot listener) si el usuario tiene el beneficio universitario vigente. */
    fun observeUniversityBenefit(uid: String): Flow<Boolean>
}
