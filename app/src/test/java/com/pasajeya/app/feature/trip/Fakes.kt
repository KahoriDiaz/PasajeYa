package com.pasajeya.app.feature.trip

import com.pasajeya.app.feature.auth.domain.AccountRepository
import com.pasajeya.app.feature.auth.domain.AuthRepository
import com.pasajeya.app.feature.auth.domain.model.AuthUser
import com.pasajeya.app.feature.auth.domain.model.DeviceSession
import com.pasajeya.app.feature.auth.domain.model.OperatorRegistration
import com.pasajeya.app.feature.auth.domain.model.RemoteSession
import com.pasajeya.app.feature.auth.domain.model.RouteOption
import com.pasajeya.app.feature.auth.domain.model.UserProfile
import com.pasajeya.app.feature.auth.domain.model.UserRole
import com.pasajeya.app.feature.trip.domain.TripPosition
import com.pasajeya.app.feature.trip.domain.TripRepository
import com.pasajeya.app.feature.trip.domain.model.EndShiftResult
import com.pasajeya.app.feature.trip.domain.model.FareBreakdown
import com.pasajeya.app.feature.trip.domain.model.OperatorContext
import com.pasajeya.app.feature.trip.domain.model.QrToken
import com.pasajeya.app.feature.trip.domain.model.Shift
import com.pasajeya.app.feature.trip.domain.model.TapInResult
import com.pasajeya.app.feature.trip.domain.model.TapOutResult
import com.pasajeya.app.feature.trip.domain.model.Trip
import com.pasajeya.app.feature.trip.domain.model.TripOperationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class FakeAuthRepository(
    private val user: AuthUser?
) : AuthRepository {
    override val authState: Flow<AuthUser?> = flowOf(user)

    override fun currentUser(): AuthUser? = user

    override suspend fun signIn(email: String, password: String): AuthUser =
        error("Not used")

    override suspend fun register(email: String, password: String): AuthUser =
        error("Not used")

    override suspend fun signOut() = Unit

    override suspend fun sendEmailVerification() = Unit

    override suspend fun reloadUser(): AuthUser = error("Not used")

    override suspend fun sendPasswordReset(email: String) = Unit
}

class FakeAccountRepository(
    uid: String,
    role: UserRole,
    private val routes: List<RouteOption> = listOf(
        RouteOption(id = "route-1", name = "Ruta 1", stopsCount = 5)
    )
) : AccountRepository {
    override val deviceSession = MutableStateFlow<DeviceSession?>(
        DeviceSession(
            uid = uid,
            sessionId = "session-1",
            profileComplete = true,
            emailVerified = true,
            role = role,
            operatorComplete = true
        )
    )

    override suspend fun ensureSession(uid: String): DeviceSession =
        deviceSession.value ?: error("No session")

    override fun clearSession() {
        deviceSession.value = null
    }

    override fun observeUserProfile(uid: String): Flow<UserProfile?> = flowOf(null)

    override fun observeRemoteSession(uid: String): Flow<RemoteSession> =
        flowOf(RemoteSession(null, false))

    override suspend fun loadActiveRoutes(): List<RouteOption> = routes

    override suspend fun completeProfile(
        role: UserRole,
        firstName: String,
        lastName: String,
        dni: String,
        birthDate: String,
        consentVersion: String
    ) = Unit

    override suspend fun registerOperator(
        licenseNumber: String,
        licenseClass: String,
        plate: String,
        routeIds: List<String>
    ): OperatorRegistration = error("Not used")
}

class FakeTripRepository : TripRepository {
    val activeTrip = MutableStateFlow<Trip?>(null)
    val trips = MutableStateFlow<Map<String, Trip>>(emptyMap())
    val activeShift = MutableStateFlow<Shift?>(null)
    val openTrips = MutableStateFlow<List<Trip>>(emptyList())

    var issueFailure: Throwable? = null
    var tokenCalls = 0
    var tapInCalls = 0
    var tapOutCalls = 0
    var startShiftCalls = 0
    var endShiftCalls = 0
    var tapInFailure: Throwable? = null
    var endShiftResult = EndShiftResult(
        shiftId = "shift-1",
        endedAtMillis = 1_000L,
        reviewCount = 0
    )
    var operatorContext = OperatorContext(
        plate = "ABC123",
        routeIds = listOf("route-1"),
        status = "verified"
    )

    override suspend fun issueQrToken(sessionId: String): QrToken {
        issueFailure?.let { throw it }
        tokenCalls += 1
        return QrToken(
            token = "token-$tokenCalls",
            expiresAtMillis = System.currentTimeMillis() + 60_000L
        )
    }

    override fun observeActiveTrip(passengerUid: String): Flow<Trip?> = activeTrip

    override fun observeTrip(tripId: String): Flow<Trip?> = trips.map { it[tripId] }

    override suspend fun loadRouteName(routeId: String): String = "Ruta 1"

    override suspend fun loadOperatorContext(sessionId: String): OperatorContext =
        operatorContext

    override fun observeActiveShift(operatorUid: String): Flow<Shift?> = activeShift

    override fun observeOpenTrips(shiftId: String): Flow<List<Trip>> = openTrips

    override suspend fun startShift(sessionId: String, routeId: String): Shift {
        startShiftCalls += 1
        val shift = Shift(
            id = "shift-1",
            operatorUid = "operator-1",
            plate = operatorContext.plate,
            routeId = routeId,
            startedAtMillis = 1_000L,
            endedAtMillis = null,
            status = "active"
        )
        activeShift.value = shift
        return shift
    }

    override suspend fun endShift(
        sessionId: String,
        shiftId: String
    ): EndShiftResult {
        endShiftCalls += 1
        activeShift.value = null
        return endShiftResult
    }

    override suspend fun tapIn(
        sessionId: String,
        shiftId: String,
        qrToken: String,
        position: TripPosition
    ): TapInResult {
        tapInCalls += 1
        tapInFailure?.let { throw it }
        return TapInResult(
            tripId = "trip-$tapInCalls",
            passengerFirstName = "Ana",
            passengerAge = 22
        )
    }

    override suspend fun tapOut(
        sessionId: String,
        tripId: String,
        position: TripPosition
    ): TapOutResult {
        tapOutCalls += 1
        return TapOutResult(
            tripId = tripId,
            status = "closed",
            distanceMeters = 1_200.0,
            requiresReview = false
        )
    }

    override suspend fun estimateFare(
        sessionId: String,
        routeId: String,
        boardingStopIndex: Int,
        alightingStopIndex: Int
    ): FareBreakdown {
        return FareBreakdown(
            distanceMeters = 5000.0,
            baseFarePerKmCents = 12,
            fuelIndex = 1.0,
            calculatedCents = 60,
            officialFareCents = 130,
            regularCents = 60,
            benefit = null,
            totalCents = 60,
            capped = false,
            minApplied = false,
            fareVersionId = "v1"
        )
    }
}

fun sampleTrip(id: String = "trip-1"): Trip = Trip(
    id = id,
    passengerUid = "passenger-1",
    operatorUid = "operator-1",
    shiftId = "shift-1",
    routeId = "route-1",
    status = "open",
    reviewReason = null,
    startedAtMillis = 1_000L,
    endedAtMillis = null,
    distanceMeters = 0.0,
    passengerFirstName = "Ana",
    passengerAge = 22
)

fun offlineError(): TripOperationException =
    TripOperationException("Sin conexión.")
