package com.pasajeya.app.feature.benefit.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import com.pasajeya.app.feature.benefit.domain.BenefitRepository
import com.pasajeya.app.feature.benefit.domain.InstitutionalEmail
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
                        attempts = snapshot.getLong("attempts")?.toInt() ?: 0,
                        institutionalEmail = snapshot.getString("institutionalEmail").orEmpty()
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
                    "stage" to STAGE_CARNE_OK,
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

    override suspend fun sendInstitutionalEmailLink(
        institutionalEmail: String
    ): BenefitOperationResult {
        val user = auth.currentUser
            ?: return BenefitOperationResult.Error("No hay sesión activa.")
        val email = InstitutionalEmail.normalize(institutionalEmail)
        if (!InstitutionalEmail.isInstitutional(email)) {
            return BenefitOperationResult.Error(
                "Usa tu correo de la universidad, el que termina en .edu.pe."
            )
        }

        return try {
            if (user.email?.lowercase() == email && user.isEmailVerified) {
                // Ya entra con su correo institucional verificado: no hay nada que enviar.
                approveUniversityBenefit(user.uid, email)
                return BenefitOperationResult.Success
            }

            // Firebase envía el enlace a ese correo y recién entonces lo toma como propio.
            user.verifyBeforeUpdateEmail(email).await()
            firestore.collection("benefitRequests").document(user.uid).set(
                mapOf(
                    "uid" to user.uid,
                    "institutionalEmail" to email,
                    "stage" to STAGE_LINK_SENT,
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            ).await()
            BenefitOperationResult.Success
        } catch (error: FirebaseAuthUserCollisionException) {
            BenefitOperationResult.Error(
                "Ese correo institucional ya está registrado en otra cuenta."
            )
        } catch (error: FirebaseAuthRecentLoginRequiredException) {
            BenefitOperationResult.Error(
                "Por seguridad, vuelve a iniciar sesión y reinténtalo."
            )
        } catch (error: Exception) {
            BenefitOperationResult.Error(
                error.message ?: "No pudimos enviar el enlace de verificación."
            )
        }
    }

    override suspend fun confirmInstitutionalEmail(): BenefitOperationResult {
        val user = auth.currentUser
            ?: return BenefitOperationResult.Error("No hay sesión activa.")

        return try {
            user.reload().await()
            val refreshed = auth.currentUser
                ?: return BenefitOperationResult.Error("No hay sesión activa.")
            val email = refreshed.email?.lowercase().orEmpty()

            if (!InstitutionalEmail.isInstitutional(email) || !refreshed.isEmailVerified) {
                return BenefitOperationResult.Error(
                    "Todavía no vemos el correo verificado. Abre el enlace que te llegó " +
                        "y vuelve a intentarlo."
                )
            }

            approveUniversityBenefit(refreshed.uid, email)
            BenefitOperationResult.Success
        } catch (error: Exception) {
            BenefitOperationResult.Error(
                error.message ?: "No pudimos confirmar tu correo institucional."
            )
        }
    }

    /** Aprueba el beneficio por un año desde que se verificó el correo. */
    private suspend fun approveUniversityBenefit(uid: String, email: String) {
        val nextYear = java.util.Calendar.getInstance()
            .apply { add(java.util.Calendar.YEAR, 1) }
            .time

        firestore.collection("users").document(uid).set(
            mapOf(
                "benefitType" to "university",
                "institutionalEmail" to email,
                "benefit" to mapOf(
                    "kind" to "university",
                    "status" to "approved",
                    "institution" to InstitutionalEmail.institutionFromEmail(email),
                    "validUntil" to com.google.firebase.Timestamp(nextYear)
                )
            ),
            SetOptions.merge()
        ).await()

        firestore.collection("benefitRequests").document(uid).set(
            mapOf(
                "uid" to uid,
                "institutionalEmail" to email,
                "stage" to STAGE_APPROVED,
                "updatedAt" to FieldValue.serverTimestamp()
            ),
            SetOptions.merge()
        ).await()
    }

    private companion object {
        const val STAGE_CARNE_OK = "carne_ok"
        const val STAGE_LINK_SENT = "link_sent"
        const val STAGE_APPROVED = "approved"
    }
}
