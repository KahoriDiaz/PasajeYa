package com.pasajeya.app.feature.trip.domain

import com.pasajeya.app.core.util.Haversine

/** Lectura real del GPS del teléfono. */
data class GpsFix(
    val lat: Double,
    val lng: Double,
    val accuracyMeters: Double?,
    val timeMillis: Long
)

/**
 * Acumula la distancia recorrida (en metros) a partir de lecturas GPS continuas, sumando tramos
 * Haversine. Descarta lecturas imprecisas, saltos imposibles y el ruido de GPS parado.
 */
class TripDistanceTracker(
    private val maxAccuracyMeters: Double = 50.0,
    private val minStepMeters: Double = 3.0,
    private val maxSpeedMetersPerSecond: Double = 40.0
) {
    var distanceMeters: Double = 0.0
        private set

    var lastFix: GpsFix? = null
        private set

    /** Procesa una lectura y devuelve los metros que sumó (0.0 si se descartó). */
    fun onFix(fix: GpsFix): Double {
        val accuracy = fix.accuracyMeters
        if (accuracy != null && accuracy > maxAccuracyMeters) return 0.0

        val anchor = lastFix
        if (anchor == null) {
            lastFix = fix
            return 0.0
        }

        val step = Haversine.distanceMeters(anchor.lat, anchor.lng, fix.lat, fix.lng)
        if (step < minStepMeters) return 0.0

        val seconds = (fix.timeMillis - anchor.timeMillis) / 1000.0
        if (seconds > 0 && step / seconds > maxSpeedMetersPerSecond) return 0.0

        distanceMeters += step
        lastFix = fix
        return step
    }

    fun reset() {
        distanceMeters = 0.0
        lastFix = null
    }
}
