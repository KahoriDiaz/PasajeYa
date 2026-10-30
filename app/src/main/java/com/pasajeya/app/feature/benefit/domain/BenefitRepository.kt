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
     * Solicita el envío de un código de verificación al correo institucional.
     */
    suspend fun requestBenefitCode(
        sessionId: String,
        institutionalEmail: String
    ): BenefitOperationResult

    /**
     * Confirma el código de verificación recibido por correo.
     */
    suspend fun confirmBenefitCode(
        sessionId: String,
        code: String
    ): BenefitOperationResult
}
