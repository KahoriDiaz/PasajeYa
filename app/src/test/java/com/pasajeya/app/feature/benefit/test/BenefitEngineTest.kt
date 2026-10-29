package com.pasajeya.app.feature.benefit.test

import com.pasajeya.app.feature.benefit.data.BenefitEngine
import com.pasajeya.app.feature.trip.domain.model.FareBreakdown
import com.pasajeya.app.feature.trip.domain.model.FareBenefit
import org.junit.Assert.assertEquals
import org.junit.Test

class BenefitEngineTest {

    @Test
    fun `applyUniversityBenefit divide entre dos cuando beneficio es universitario al 50`() {
        val breakdown = FareBreakdown(
            distanceMeters = 1000.0,
            baseFarePerKmCents = 120,
            fuelIndex = 1.0,
            calculatedCents = 300,
            officialFareCents = 500,
            regularCents = 500,
            benefit = FareBenefit(
                kind = "university",
                percent = 50,
                officialUniversityFareCents = 250,
                discountCents = 250
            ),
            totalCents = 500,
            capped = false,
            minApplied = false,
            fareVersionId = "v1"
        )

        val totalFinal = BenefitEngine.applyUniversityBenefit(breakdown)
        assertEquals(250, totalFinal)
    }

    @Test
    fun `applyUniversityBenefit mantiene total cuando no hay beneficio`() {
        val breakdown = FareBreakdown(
            distanceMeters = 1000.0,
            baseFarePerKmCents = 120,
            fuelIndex = 1.0,
            calculatedCents = 300,
            officialFareCents = 500,
            regularCents = 500,
            benefit = null,
            totalCents = 500,
            capped = false,
            minApplied = false,
            fareVersionId = "v1"
        )

        val totalFinal = BenefitEngine.applyUniversityBenefit(breakdown)
        assertEquals(500, totalFinal)
    }
}