package com.pasajeya.app.feature.benefit.data

import com.google.firebase.firestore.FirebaseFirestore
import com.pasajeya.app.feature.benefit.domain.Benefit
import com.pasajeya.app.feature.benefit.domain.BenefitRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseBenefitRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) : BenefitRepository {

    override suspend fun requestBenefit(uid: String, universityName: String, studentCode: String): Result<Unit> = runCatching {
        val data = hashMapOf(
            "uid" to uid,
            "stage" to "pending",
            "universityName" to universityName,
            "studentCode" to studentCode,
            "updatedAt" to System.currentTimeMillis()
        )
        firestore.collection("benefitRequests").document(uid).set(data).await()
    }

    override fun observeBenefitStatus(uid: String): Flow<Benefit?> = callbackFlow {
        val listener = firestore.collection("benefitRequests").document(uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(null)
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    val approved = snapshot.getBoolean("approved") ?: false
                    val benefit = Benefit(
                        kind = "university",
                        percent = if (approved) 50 else 0,
                        officialUniversityFareCents = 0,
                        discountCents = 0,
                        validUntil = snapshot.getLong("validUntil")
                    )
                    trySend(benefit)
                } else {
                    trySend(null)
                }
            }
        awaitClose { listener.remove() }
    }
}