package com.pasajeya.app.feature.trip.domain

data class FareVersionInput(
    val baseFarePerKmCents: Int = 8,
    val minFareCents: Int = 130,
    val maxFareCents: Int = 200,
    val fuelIndex: Double = 1.13,
    val roundingStepCents: Int = 10
)
