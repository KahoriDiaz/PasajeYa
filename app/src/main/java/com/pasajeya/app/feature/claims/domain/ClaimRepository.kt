package com.pasajeya.app.feature.claims.domain

import com.pasajeya.app.feature.claims.domain.Claim
import io.reactivex.rxjava3.core.Flowable
import kotlinx.coroutines.SuspendCancellation

interface ClaimRepository {
    suspend fun submitClaim(claim: Claim): Unit

    suspend fun observeClaimByTripId(tripId: String): Flowable<Claim>

    suspend fun observePassengerClaims(passengerId: String): Flowable<List<Claim>>
}