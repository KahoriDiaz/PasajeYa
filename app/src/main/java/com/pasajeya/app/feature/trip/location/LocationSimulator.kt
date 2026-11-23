package com.pasajeya.app.feature.trip.location

import com.pasajeya.app.core.util.Haversine
import com.pasajeya.app.feature.trip.domain.RoutePath
import com.pasajeya.app.feature.trip.domain.TripPosition

/**
 * Simula el avance del vehículo paradero por paradero sobre una ruta real.
 * La posición se persiste en el turno para que el pasajero pueda leer el mismo tramo.
 */
class LocationSimulator(private val route: RoutePath) {

    val stopCount: Int get() = route.stops.size

    fun positionAt(index: Int): TripPosition {
        val safeIndex = index.coerceIn(0, (stopCount - 1).coerceAtLeast(0))
        val stop = route.stops[safeIndex]
        return TripPosition(stop.lat, stop.lng, accuracy = 5.0)
    }

    fun stopNameAt(index: Int): String? =
        route.stops.getOrNull(index.coerceIn(0, (stopCount - 1).coerceAtLeast(0)))?.name

    fun nextIndex(index: Int): Int {
        if (stopCount <= 1) return 0
        return (index + 1) % stopCount
    }

    fun nearestStopIndex(position: TripPosition): Int {
        if (route.stops.isEmpty()) return 0
        return route.stops.indices.minByOrNull { index ->
            val stop = route.stops[index]
            Haversine.distanceMeters(
                position.latitude,
                position.longitude,
                stop.lat,
                stop.lng
            )
        } ?: 0
    }
}
