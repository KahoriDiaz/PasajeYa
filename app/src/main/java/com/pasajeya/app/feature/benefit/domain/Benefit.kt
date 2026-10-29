package com.pasajeya.app.feature.benefit.domain

data class Benefit(
    val kind: String, // "university" | other
    val percent: Int, // ej. 50 para 50%
    val officialUniversityFareCents: Int,
    val discountCents: Int,
    val validUntil: Long? // opcional
)