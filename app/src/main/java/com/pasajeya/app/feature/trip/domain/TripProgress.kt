package com.pasajeya.app.feature.trip.domain

import com.pasajeya.app.feature.fare.domain.FareEstimator
import com.pasajeya.app.feature.fare.domain.RouteStop

/**
 * Avance de la unidad sobre su ruta, en metros.
 *
 * Se calcula siempre a partir de dos datos que no dependen del orden en que lleguen los
 * avisos: cuantas veces la unidad ya recorrio la ruta completa y en que paradero va. Asi
 * el pasajero, el conductor y el cobro miden exactamente lo mismo, y un aviso repetido o
 * atrasado no vuelve a sumar el tramo.
 */
object TripProgress {

    /** Largo de la ruta de punta a punta. */
    fun routeMeters(stops: List<RouteStop>): Double {
        if (stops.size < 2) return 0.0
        return FareEstimator.routeDistanceMeters(stops, 0, stops.lastIndex)
    }

    /** Metros que lleva la unidad: vueltas completas mas lo andado desde el primer paradero. */
    fun unitMeters(stops: List<RouteStop>, laps: Int, stopIndex: Int): Double {
        if (stops.size < 2) return 0.0
        val index = stopIndex.coerceIn(stops.indices)
        val completed = laps.coerceAtLeast(0) * routeMeters(stops)
        return completed + FareEstimator.routeDistanceMeters(stops, 0, index)
    }

    /**
     * Metros del viaje de una persona: lo que avanzo la unidad desde que subio. Nunca
     * devuelve un valor negativo ni mayor a lo que la unidad pudo recorrer.
     */
    fun tripMeters(
        stops: List<RouteStop>,
        boardingLaps: Int,
        boardingStopIndex: Int,
        laps: Int,
        stopIndex: Int
    ): Double {
        if (stops.size < 2) return 0.0
        val boarded = unitMeters(stops, boardingLaps, boardingStopIndex)
        val now = unitMeters(stops, laps, stopIndex)
        return (now - boarded).coerceAtLeast(0.0)
    }
}
