package com.pasajeya.app.feature.fare.domain

import java.util.Date

data class FuelRecord(
    val price: Double = 0.0,
    val fuelIndex: Double = 1.0,
    val changePercent: Double = 0.0,
    val source: String = "",
    val recordedAt: Date? = null
)
