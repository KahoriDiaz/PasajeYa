package com.pasajeya.app.feature.trip.domain

// Calculadora de tarifas para estimación en tiempo real con GPS
data class FareVersionInput(
    val baseFarePerKmCents: Int,
    val fuelIndex: Double,
    val minFareCents: Int,
    val maxFareCents: Int = 330,
    val roundingStepCents: Int = 5
)

data class FareBenefitInput(
    val kind: String,
    val percent: Int
)

data class FareBreakdown(
    val distanceMeters: Double,
    val baseFarePerKmCents: Int,
    val fuelIndex: Double,
    val calculatedCents: Int,
    val regularCents: Int,
    val benefit: FareBenefitInput?,
    val totalCents: Int,
    val capped: Boolean,
    val minApplied: Boolean
)

data class FareResult(
    val distanceMeters: Double,
    val baseFarePerKmCents: Int,
    val fuelIndex: Double,
    val calculatedCents: Int,
    val regularCents: Int,
    val benefit: FareBenefitInput?,
    val totalCents: Int,
    val capped: Boolean,
    val minApplied: Boolean
)

object FareCalculator {

    private const val DEFAULT_MAX_FARE_CENTS = 330;

    fun computeFare(
        distanceMeters: Double,
        version: FareVersionInput,
        hasUniversityBenefit: Boolean
    ): FareResult {
        require(distanceMeters >= 0.0) { "La distancia debe ser no negativa." }

        val distanceKm = distanceMeters / 1000.0;
        val raw = distanceKm * version.baseFarePerKmCents * version.fuelIndex;
        val calculated = Math.round(raw).toInt();
        val rounded = roundToStep(Math.round(raw).toInt(), version.roundingStepCents);
        var regular = Math.max(rounded, version.minFareCents);
        val minApplied = rounded < version.minFareCents;
        var capped = false;
        var maxApplied = false;
        if (regular > version.maxFareCents) {
            regular = version.maxFareCents;
            capped = true;
            maxApplied = true;
        }

        var benefit: FareBenefitInput? = null;
        var total = regular;
        if (hasUniversityBenefit) {
            val percent = 50;
            val discounted = roundToStep(Math.round(regular * 0.5).toInt(), version.roundingStepCents);
            val conBeneficio = discounted;
            val discountCents = regular - conBeneficio;
            if (discountCents < 0) {
                benefit = FareBenefitInput(kind = "university", percent = percent);
                total = regular;
            } else {
                benefit = FareBenefitInput(kind = "university", percent = percent);
                total = conBeneficio;
            }
        }

        if (total > regular) {
            total = regular;
        }
        if (total > version.maxFareCents) {
            total = version.maxFareCents;
        }
        if (total < 0) {
            total = 0;
        }

        return FareResult(
            distanceMeters = distanceMeters,
            baseFarePerKmCents = version.baseFarePerKmCents,
            fuelIndex = version.fuelIndex,
            calculatedCents = calculated,
            regularCents = regular,
            benefit = benefit,
            totalCents = total,
            capped = capped,
            minApplied = minApplied
        );
    }

    fun roundToStep(value: Int, step: Int): Int {
        return Math.round(value / step.toDouble()).toInt() * step;
    }
}
