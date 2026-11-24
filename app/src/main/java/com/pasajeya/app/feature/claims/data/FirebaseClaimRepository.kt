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
        // El reclamo guarda contra quien va: conductor, placa y empresa que responde.
        val operatorDoc = claim.operatorId?.let { uid ->
            runCatching { firestore.collection("operators").document(uid).get().await() }
                .getOrNull()
        }
        val claimData = hashMapOf(
            "tripId" to claim.tripId,
            "passengerId" to claim.passengerId,
            "passengerName" to claim.passengerName,
            "operatorId" to claim.operatorId,
            "companyId" to (claim.companyId ?: operatorDoc?.getString("companyId")),
            "plate" to (claim.plate ?: operatorDoc?.getString("plate")),
            "routeId" to claim.routeId,
            "category" to claim.category,
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

    override fun observeCompanyClaims(companyId: String): Flow<List<Claim>> =
        claimsWhere("companyId", companyId)

    override fun observePassengerClaims(passengerId: String): Flow<List<Claim>> =
        claimsWhere("passengerId", passengerId)

    private fun claimsWhere(field: String, value: String): Flow<List<Claim>> = callbackFlow {
        val listener = firestore.collection("claims")
            .whereEqualTo(field, value)
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