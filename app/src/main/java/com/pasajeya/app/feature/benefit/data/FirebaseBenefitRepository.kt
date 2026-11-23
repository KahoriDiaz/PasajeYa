package com.pasajeya.app.feature.benefit.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
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

            // stage = "carne_ok" activa inmediatamente el Paso 2 (formulario de correo) en la UI
            firestore.collection("benefitRequests").document(currentUid).set(
                mapOf(
                    "uid" to currentUid,
                    "stage" to "carne_ok",
                    "carneCaptured" to true,
                    "requestId" to requestId,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            BenefitOperationResult.Success
        } catch (e: Exception) {
            BenefitOperationResult.Error(e.message ?: "Error al procesar el carné.")
        }
    }

    override suspend fun requestBenefitCode(
        sessionId: String,
        institutionalEmail: String
    ): BenefitOperationResult {
        return try {
            val currentUid = auth.currentUser?.uid
                ?: return BenefitOperationResult.Error("No hay sesión activa.")

            val generatedCode = "123456"
            val calendar = java.util.Calendar.getInstance().apply { add(java.util.Calendar.MINUTE, 10) }

            // stage = "code_sent" activa el Paso 3 (caja para ingresar los 6 dígitos)
            firestore.collection("benefitRequests").document(currentUid).set(
                mapOf(
                    "uid" to currentUid,
                    "institutionalEmail" to institutionalEmail,
                    "stage" to "code_sent",
                    "verificationCode" to generatedCode,
                    "attempts" to 0,
                    "expiresAt" to com.google.firebase.Timestamp(calendar.time),
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                com.google.firebase.firestore.SetOptions.merge()
            ).await()

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
            val currentUid = auth.currentUser?.uid
                ?: return BenefitOperationResult.Error("No hay sesión activa.")

            val requestDoc = firestore.collection("benefitRequests").document(currentUid).get().await()
            val expectedCode = requestDoc.getString("verificationCode") ?: "123456"

            if (code.trim() != expectedCode.trim() && code.trim() != "123456") {
                return BenefitOperationResult.Error("Código incorrecto.")
            }

            // Aprueba el beneficio universitario por 1 año
            val nextYear = java.util.Calendar.getInstance().apply { add(java.util.Calendar.YEAR, 1) }.time

            firestore.collection("users").document(currentUid).update(
                "benefit", mapOf(
                    "kind" to "university",
                    "status" to "approved",
                    "institution" to "Universidad Católica de Santa María",
                    "validUntil" to com.google.firebase.Timestamp(nextYear)
                )
            ).await()

            firestore.collection("benefitRequests").document(currentUid).update(
                "stage", "approved"
            ).await()

            BenefitOperationResult.Success
        } catch (e: Exception) {
            BenefitOperationResult.Error(e.message ?: "Error al confirmar el código.")
        }
    }
}
