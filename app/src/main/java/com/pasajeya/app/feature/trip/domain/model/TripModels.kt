package com.pasajeya.app.feature.trip.domain.model

data class QrToken(
    val token: String,
    val expiresAtMillis: Long
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
    val passengerAge: Int?
)

data class Shift(
    val id: String,
    val operatorUid: String,
    val plate: String,
    val routeId: String,
    val startedAtMillis: Long?,
    val endedAtMillis: Long?,
    val status: String
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
    val passengerAge: Int?
)

data class TapOutResult(
    val tripId: String,
    val status: String,
    val distanceMeters: Double,
    val requiresReview: Boolean
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
