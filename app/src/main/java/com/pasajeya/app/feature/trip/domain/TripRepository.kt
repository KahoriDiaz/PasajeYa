package com.pasajeya.app.feature.trip.domain

import com.pasajeya.app.feature.trip.domain.model.EndShiftResult
import com.pasajeya.app.feature.trip.domain.model.OperatorContext
import com.pasajeya.app.feature.trip.domain.model.QrToken
import com.pasajeya.app.feature.trip.domain.model.Shift
import com.pasajeya.app.feature.trip.domain.model.TapInResult
import com.pasajeya.app.feature.trip.domain.model.TapOutResult
import com.pasajeya.app.feature.trip.domain.model.Trip
import kotlinx.coroutines.flow.Flow

data class TripPosition(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Double?
)

interface TripRepository {
    suspend fun issueQrToken(sessionId: String): QrToken

    fun observeActiveTrip(passengerUid: String): Flow<Trip?>

    suspend fun loadRouteName(routeId: String): String?

    suspend fun loadOperatorContext(sessionId: String): OperatorContext

    fun observeActiveShift(operatorUid: String): Flow<Shift?>

    fun observeOpenTrips(shiftId: String): Flow<List<Trip>>

    suspend fun startShift(sessionId: String, routeId: String): Shift

    suspend fun endShift(sessionId: String, shiftId: String): EndShiftResult

    suspend fun tapIn(
        sessionId: String,
        shiftId: String,
        qrToken: String,
        position: TripPosition
    ): TapInResult

    suspend fun tapOut(
        sessionId: String,
        tripId: String,
        position: TripPosition
    ): TapOutResult
}
