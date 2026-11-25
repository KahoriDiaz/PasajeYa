package com.pasajeya.app.feature.trip.domain

import com.pasajeya.app.feature.fare.domain.FareEstimator
import com.pasajeya.app.feature.fare.domain.RouteStop

/**
 * Cuanto recorrio la unidad y cuanto le corresponde a cada viaje, en metros.
 *
 * Es la unica forma de medir que usan los tres lados (el mapa del pasajero, la lista del
 * conductor y el cobro del Tap Out), para que los tres muestren el mismo numero. Toda la
 * medicion sale de datos que no dependen del orden en que lleguen los avisos, asi que un
 * aviso repetido o atrasado no vuelve a sumar el tramo.
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
     * Metros del viaje con el modo demostracion encendido: lo que avanzo la unidad desde
     * que la persona subio. Nunca devuelve un valor negativo.
     */
    fun simulatedTripMeters(
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

    /**
     * Metros del viaje en marcha real. Manda el odometro de la unidad, que lo lleva el
     * telefono del conductor con el GPS; si todavia no avanzo (recien subio, o el conductor
     * no dio permiso de ubicacion) se mide entre el paradero de subida y el de ahora.
     *
     * El telefono del pasajero nunca mide el viaje: cada equipo mediria distinto y el
     * pasajero veria un numero y pagaria otro.
     */
    fun realTripMeters(
        stops: List<RouteStop>,
        boardingOdometerMeters: Double?,
        unitOdometerMeters: Double?,
        boardingStopIndex: Int,
        stopIndex: Int
    ): Double {
        if (stops.size < 2) return 0.0
        val oneWay = routeMeters(stops)
        val maxMeters = if (oneWay > 0.0) oneWay * 2 else Double.MAX_VALUE
        if (boardingOdometerMeters != null &&
            unitOdometerMeters != null &&
            unitOdometerMeters > boardingOdometerMeters
        ) {
            return (unitOdometerMeters - boardingOdometerMeters).coerceAtMost(maxMeters)
        }
        return FareEstimator.routeDistanceMeters(
            stops,
            boardingStopIndex.coerceIn(stops.indices),
            stopIndex.coerceIn(stops.indices)
        )
    }

    /**
     * La medicion del viaje, con demostracion o sin ella. Los tres lados llaman aqui.
     *
     * @param stopIndex paradero en el que va la unidad ahora (o donde bajo la persona).
     */
    fun tripMeters(
        stops: List<RouteStop>,
        simulationEnabled: Boolean,
        boardingStopIndex: Int,
        stopIndex: Int,
        boardingLaps: Int = 0,
        laps: Int = 0,
        boardingOdometerMeters: Double? = null,
        unitOdometerMeters: Double? = null
    ): Double = if (simulationEnabled) {
        simulatedTripMeters(
            stops = stops,
            boardingLaps = boardingLaps,
            boardingStopIndex = boardingStopIndex,
            laps = laps,
            stopIndex = stopIndex
        )
    } else {
        realTripMeters(
            stops = stops,
            boardingOdometerMeters = boardingOdometerMeters,
            unitOdometerMeters = unitOdometerMeters,
            boardingStopIndex = boardingStopIndex,
            stopIndex = stopIndex
        )
    }
}
