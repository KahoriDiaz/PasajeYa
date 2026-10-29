package com.pasajeya.app.feature.claims.domain

data class Claim(
    val claimId: String? = null,
    val tripId: String,
    val passengerId: String,
    val operatorId: String? = null,
    val reason: String,
    val proposedDistanceMeters: Double? = null,
    val status: String = "submitted", // submitted, in_review, approved, rejected, partial_refund
    val refundAmountCents: Int = 0,
    val resolutionNotes: String? = null,
    val createdAt: Long? = null,
    val resolvedAt: Long? = null
)