package com.pasajeya.app.feature.fare.domain

import com.pasajeya.app.feature.trip.domain.FareVersionInput
import kotlinx.coroutines.flow.Flow

data class RouteStop(
    val name: String,
    val lat: Double,
    val lng: Double
)

data class RouteGeo(
    val id: String,
    val name: String,
    val stops: List<RouteStop>,
    val officialFareCents: Int,
    val officialUniversityFareCents: Int
)

/** Ruta con su geometría y la versión tarifaria vigente, lista para calcular. */
data class RouteFareContext(
    val route: RouteGeo,
    val version: FareVersionInput
)

interface FareRepository {
    /** Lee la ruta y la versión tarifaria vigente desde Firestore. */
    suspend fun loadRouteFareContext(routeId: String): RouteFareContext

    /** Emite en vivo (snapshot listener) si el usuario tiene el beneficio universitario vigente. */
    fun observeUniversityBenefit(uid: String): Flow<Boolean>
}
