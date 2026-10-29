package com.pasajeya.app.feature.claims.data

import com.google.firebase.firestore.FirebaseFirestore
import com.pasajeya.app.feature.claims.domain.Claim
import com.pasajeya.app.feature.claims.domain.ClaimRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseClaimRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) : ClaimRepository {

    override suspend fun submitClaim(claim: Claim): Result<Unit> = runCatching {
        val claimData = hashMapOf(
            "tripId" to claim.tripId,
            "passengerId" to claim.passengerId,
            "operatorId" to claim.operatorId,
            "reason" to claim.reason,
            "proposedDistanceMeters" to claim.proposedDistanceMeters,
            "status" to claim.status,
            "refundAmountCents" to claim.refundAmountCents,
            "resolutionNotes" to claim.resolutionNotes,
            "createdAt" to (claim.createdAt ?: System.currentTimeMillis()),
            "resolvedAt" to claim.resolvedAt
        )
        firestore.collection("claims").add(claimData).await()
    }

    override fun observeClaimByTripId(tripId: String): Flow<Claim?> = callbackFlow {
        val listener = firestore.collection("claims")
            .whereEqualTo("tripId", tripId)
            .limit(1)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(null)
                    return@addSnapshotListener
                }
                val doc = snapshot?.documents?.firstOrNull()
                val claim = doc?.toObject(Claim::class.java)?.copy(claimId = doc.id)
                trySend(claim)
            }
        awaitClose { listener.remove() }
    }

    override fun observePassengerClaims(passengerId: String): Flow<List<Claim>> = callbackFlow {
        val listener = firestore.collection("claims")
            .whereEqualTo("passengerId", passengerId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val claims = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Claim::class.java)?.copy(claimId = doc.id)
                } ?: emptyList()
                trySend(claims)
            }
        awaitClose { listener.remove() }
    }
}