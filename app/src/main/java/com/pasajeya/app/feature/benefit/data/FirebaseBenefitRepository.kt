package com.pasajeya.app.feature.benefit.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.storage.FirebaseStorage
import com.pasajeya.app.feature.benefit.domain.BenefitRepository
import com.pasajeya.app.feature.benefit.domain.model.BenefitOperationResult
import com.pasajeya.app.feature.benefit.domain.model.BenefitRequestState
import com.pasajeya.app.feature.benefit.domain.model.BenefitStatus
import com.pasajeya.app.feature.benefit.domain.model.UserBenefit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

@Singleton
class FirebaseBenefitRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val functions: FirebaseFunctions,
    private val storage: FirebaseStorage,
    private val auth: FirebaseAuth
) : BenefitRepository {

    override fun observeUserBenefit(uid: String): Flow<UserBenefit?> = callbackFlow {
        val registration = firestore.collection("users").document(uid)
            .addSnapshotListener(MetadataChanges.EXCLUDE) { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val benefitData = snapshot?.get("benefit") as? Map<*, *>
                val benefit = benefitData?.let {
                    UserBenefit(
                        status = try {
                            BenefitStatus.valueOf((it["status"] as? String)?.uppercase() ?: "NONE")
                        } catch (e: Exception) {
                            BenefitStatus.NONE
                        },
                        kind = it["kind"] as? String,
                        validUntilMillis = (it["validUntil"] as? com.google.firebase.Timestamp)?.toDate()?.time,
                        rejectionReason = it["rejectionReason"] as? String
                    )
                }
                trySend(benefit)
            }
        awaitClose { registration.remove() }
    }

    override fun observeBenefitRequest(uid: String): Flow<BenefitRequestState?> = callbackFlow {
        val registration = firestore.collection("benefitRequests").document(uid)
            .addSnapshotListener(MetadataChanges.EXCLUDE) { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot == null || !snapshot.exists()) {
                    trySend(null)
                    return@addSnapshotListener
                }
                trySend(
                    BenefitRequestState(
                        stage = snapshot.getString("stage") ?: "",
                        expiresAtMillis = snapshot.getTimestamp("expiresAt")?.toDate()?.time,
                        attempts = snapshot.getLong("attempts")?.toInt() ?: 0
                    )
                )
            }
        awaitClose { registration.remove() }
    }

    override suspend fun submitCarne(
        sessionId: String,
        requestId: String,
        imageBytes: ByteArray
    ): BenefitOperationResult {
        return try {
            val currentUid = auth.currentUser?.uid
                ?: return BenefitOperationResult.Error("No hay sesión activa.")

            // 1. Subir a Storage (las reglas exigen que la ruta lleve el uid del dueño)
            storage.reference.child("benefitUploads/$currentUid/$requestId.jpg")
                .putBytes(imageBytes)
                .await()

            // 2. Llamar a la Function
            functions.getHttpsCallable("submitCarne")
                .call(mapOf("sessionId" to sessionId, "requestId" to requestId))
                .await()

            BenefitOperationResult.Success
        } catch (e: Exception) {
            BenefitOperationResult.Error(e.message ?: "Error al subir el carné.")
        }
    }

    override suspend fun requestBenefitCode(
        sessionId: String,
        institutionalEmail: String
    ): BenefitOperationResult {
        return try {
            functions.getHttpsCallable("requestBenefitCode")
                .call(mapOf("sessionId" to sessionId, "institutionalEmail" to institutionalEmail))
                .await()
            BenefitOperationResult.Success
        } catch (e: Exception) {
            BenefitOperationResult.Error(e.message ?: "Error al solicitar el código.")
        }
    }

    override suspend fun confirmBenefitCode(
        sessionId: String,
        code: String
    ): BenefitOperationResult {
        return try {
            functions.getHttpsCallable("confirmBenefitCode")
                .call(mapOf("sessionId" to sessionId, "code" to code))
                .await()
            BenefitOperationResult.Success
        } catch (e: Exception) {
            BenefitOperationResult.Error(e.message ?: "Error al confirmar el código.")
        }
    }
}
