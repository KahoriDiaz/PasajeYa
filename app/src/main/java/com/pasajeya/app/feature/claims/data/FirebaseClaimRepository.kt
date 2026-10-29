package com.pasajeya.app.feature.claims.data

import com.google.firebase.firestore.FirebaseFirestore
import com.pasajeya.app.feature.claims.domain.Claim
import kotlinx.coroutines.flow.Flowable
import kotlinx.coroutines.flow.flowable
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class FirebaseClaimRepository @Inject constructor(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) : ClaimRepository {

    override suspend fun submitClaim(claim: Claim) {
        val claimData = mapOf(
            "tripId" to claim.tripId,
            "passengerId" to claim.passengerId,
            "operatorId" to claim.operatorId,
            "reason" to claim.reason,
            "proposedDistanceMeters" to claim.proposedDistanceMeters,
            "status" to claim.status,
            "refundAmountCents" to claim.refundAmountCents,
            "resolutionNotes" to claim.resolutionNotes,
            "createdAt" to claim.createdAt,
            "resolvedAt" to claim.resolvedAt
        )
        firestore.collection("claims").add(claimData)
    }

    override suspend fun observeClaimByTripId(tripId: String): Flowable<Claim> {
        // TODO: Implement Firestore snapshot listener returning Flowable<Claim>
        return flowable { emitClaim(tripId) }
    }

    override suspend fun observePassengerClaims(passengerId: String): Flowable<List<Claim>> {
        // TODO: Implement Firestore query returning Flowable<List<Claim>>
        return flowable { emitPassengerClaims(passengerId) }
    }

    private fun emitClaim(tripId: String) {
        // Placeholder: emit a Claim object from Firestore snapshot
        // In a real implementation, attach a snapshot listener and emit the parsed Claim.
        if (isActive) {
            // Emit nothing for now; real impl would emit parsed Claim from snapshot
        }
    }

    private fun emitPassengerClaims(passengerId: String) {
        // Placeholder
    }
}