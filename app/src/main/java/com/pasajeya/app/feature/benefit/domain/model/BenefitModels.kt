package com.pasajeya.app.feature.benefit.domain.model

enum class BenefitStatus {
    NONE,
    PENDING_CARNE,
    PENDING_CODE,
    APPROVED,
    REJECTED,
    EXPIRED
}

data class UserBenefit(
    val status: BenefitStatus,
    val kind: String? = null,
    val validUntilMillis: Long? = null,
    val rejectionReason: String? = null
)

data class BenefitRequestState(
    val stage: String,
    val expiresAtMillis: Long?,
    val attempts: Int,
    /** Correo institucional al que se envio el enlace de verificacion. */
    val institutionalEmail: String = ""
)

sealed class BenefitOperationResult {
    object Success : BenefitOperationResult()
    data class Error(val message: String) : BenefitOperationResult()
}
