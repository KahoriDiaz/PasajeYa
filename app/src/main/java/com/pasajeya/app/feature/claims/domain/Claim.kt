package com.pasajeya.app.feature.claims.domain

data class Claim(
    val claimId: String? = null,
    val tripId: String,
    val passengerId: String,
    val passengerName: String? = null,
    /** Conductor contra el que va el reclamo. */
    val operatorId: String? = null,
    /** Empresa que responde por ese conductor, si la tiene. */
    val companyId: String? = null,
    val plate: String? = null,
    val routeId: String? = null,
    val category: String = ClaimCategory.OTHER.wire,
    val reason: String,
    val proposedDistanceMeters: Double? = null,
    // submitted, in_review, approved, rejected, partial_refund
    val status: String = "submitted",
    val refundAmountCents: Int = 0,
    val resolutionNotes: String? = null,
    val createdAt: Long? = null,
    val resolvedAt: Long? = null
)
