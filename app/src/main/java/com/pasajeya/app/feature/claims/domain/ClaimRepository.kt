package com.pasajeya.app.feature.claims.domain

import kotlinx.coroutines.flow.Flow

interface ClaimRepository {
    suspend fun submitClaim(claim: Claim): Result<Unit>
    fun observeClaimByTripId(tripId: String): Flow<Claim?>
    fun observePassengerClaims(passengerId: String): Flow<List<Claim>>
}