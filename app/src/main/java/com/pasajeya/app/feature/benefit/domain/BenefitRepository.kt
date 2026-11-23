package com.pasajeya.app.feature.benefit.domain

import com.pasajeya.app.feature.benefit.domain.model.BenefitOperationResult
import com.pasajeya.app.feature.benefit.domain.model.BenefitRequestState
import com.pasajeya.app.feature.benefit.domain.model.UserBenefit
import kotlinx.coroutines.flow.Flow

interface BenefitRepository {
    /**
     * Observa el estado del beneficio del usuario actual.
     */
    fun observeUserBenefit(uid: String): Flow<UserBenefit?>

    /**
     * Observa el estado de la solicitud de beneficio en curso.
     */
    fun observeBenefitRequest(uid: String): Flow<BenefitRequestState?>

    /**
     * Sube la imagen del carné a Storage y registra la solicitud.
     * @param requestId ID único para la solicitud (ej: timestamp)
     * @param imageBytes Contenido de la imagen JPEG
     */
    suspend fun submitCarne(
        sessionId: String,
        requestId: String,
        imageBytes: ByteArray
    ): BenefitOperationResult

    /**
     * Pide a Firebase Auth que envie el enlace de verificacion al correo institucional.
     * El correo pasa a ser el de acceso de la cuenta solo cuando la persona abre el enlace,
     * y Firebase rechaza un correo que ya pertenezca a otra cuenta.
     */
    suspend fun sendInstitutionalEmailLink(
        institutionalEmail: String
    ): BenefitOperationResult

    /**
     * Revisa si el enlace ya fue abierto y, en ese caso, aprueba el beneficio.
     */
    suspend fun confirmInstitutionalEmail(): BenefitOperationResult
}
