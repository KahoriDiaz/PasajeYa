package com.pasajeya.app.feature.trip.domain.model

data class QrToken(
    val token: String,
    val expiresAtMillis: Long
)

data class FareBenefit(
    val kind: String,
    val percent: Int,
    val officialUniversityFareCents: Int,
    val discountCents: Int,
    val isStudent: Boolean = false
)

data class FareBreakdown(
    val distanceMeters: Double,
    val baseFarePerKmCents: Int,
    val fuelIndex: Double,
    val calculatedCents: Int,
    val officialFareCents: Int,
    val regularCents: Int,
    val benefit: FareBenefit?,
    val totalCents: Int,
    val capped: Boolean,
    val minApplied: Boolean,
    val fareVersionId: String
)

data class Trip(
    val id: String,
    val passengerUid: String,
    val operatorUid: String,
    val shiftId: String,
    val routeId: String,
    val status: String,
    val reviewReason: String?,
    val startedAtMillis: Long?,
    val endedAtMillis: Long?,
    val distanceMeters: Double,
    val passengerFirstName: String,
    val passengerAge: Int?,
    val fareType: String = "regular",
    val boardingStopIndex: Int? = null,
    val boardingStopName: String? = null,
    val alightingStopIndex: Int? = null,
    val alightingStopName: String? = null,
    /** Lectura del odometro del turno cuando el pasajero subio. */
    val boardingOdometerMeters: Double? = null,
    val fareBreakdown: FareBreakdown? = null,
    val heldAmountCents: Int = 0
)

data class Shift(
    val id: String,
    val operatorUid: String,
    val plate: String,
    val routeId: String,
    val startedAtMillis: Long?,
    val endedAtMillis: Long?,
    val status: String,
    val simulationEnabled: Boolean = false,
    val simulatedStopIndex: Int = 0,
    val simulatedLatitude: Double? = null,
    val simulatedLongitude: Double? = null,
    val simulatedStopName: String? = null,
    val simulatedUpdatedAtMillis: Long? = null,
    /**
     * Metros recorridos por la unidad desde que arranco el turno. Solo crece, asi que
     * sirve igual si la combi da la vuelta completa y vuelve a pasar por el mismo paradero.
     */
    val odometerMeters: Double = 0.0
) {
    val isActive: Boolean get() = status == "active"
}

data class OperatorContext(
    val plate: String,
    val routeIds: List<String>,
    val status: String
)

data class TapInResult(
    val tripId: String,
    val passengerFirstName: String,
    val passengerAge: Int?,
    val fareType: String = "regular"
)

data class TapOutResult(
    val tripId: String,
    val status: String,
    val distanceMeters: Double,
    val requiresReview: Boolean,
    val fareBreakdown: FareBreakdown? = null
)

data class EndShiftResult(
    val shiftId: String,
    val endedAtMillis: Long?,
    val reviewCount: Int
)

class TripOperationException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)
