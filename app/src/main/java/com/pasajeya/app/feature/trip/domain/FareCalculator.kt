package com.pasajeya.app.feature.trip.domain

import com.pasajeya.app.feature.fare.domain.FareBenefit
import kotlin.math.max
import kotlin.math.roundToInt

data class FareResult(
    val distanceMeters: Double,
    val totalCents: Int,
    val regularCents: Int,
    val benefit: FareBenefit? = null,
    val capped: Boolean = false,
    val minApplied: Boolean = false,
    val rawCalculatedCents: Double = 0.0
)

object FareCalculator {

    /**
     * Tarifa por tramo: distancia (km) × tarifa base por km × índice de combustible.
     * El resultado se redondea al paso configurado, se aplica la tarifa mínima y el tope
     * oficial de la ruta (limitado al techo de seguridad). El beneficio universitario es el
     * 50 % del pasaje regular, redondeado al mismo paso.
     */
    fun computeFare(
        distanceMeters: Double,
        version: FareVersionInput,
        hasUniversityBenefit: Boolean = false,
        officialFareCents: Int? = null
    ): FareResult {
        val km = distanceMeters.coerceAtLeast(0.0) / 1000.0
        val rawCalculatedCents = km * version.baseFarePerKmCents * version.fuelIndex
        val step = version.roundingStepCents.takeIf { it > 0 } ?: 5
        val rounded = roundToStep(rawCalculatedCents, step)

        val minCents = version.minFareCents.coerceAtLeast(0)
        val afterMinimum = rounded.coerceAtLeast(minCents)
        val minApplied = afterMinimum > rounded

        val capCents = officialFareCents
            ?.takeIf { it > 0 }
            ?: version.maxFareCents.takeIf { it > 0 }
        val regularCents = capCents?.let { afterMinimum.coerceAtMost(it) } ?: afterMinimum
        val capped = capCents != null && afterMinimum > capCents

        var finalCents = regularCents
        var benefit: FareBenefit? = null
        if (hasUniversityBenefit) {
            finalCents = roundToStep(regularCents / 2.0, step).coerceAtMost(regularCents)
            // Piso universitario: la mitad de la tarifa mínima (65 céntimos / S/ 0.65)
            val universityMinCents = (version.minFareCents / 2.0).roundToInt()
            finalCents = finalCents.coerceAtLeast(universityMinCents)
            benefit = FareBenefit(
                type = "UNIVERSITY",
                percent = 50,
                discountCents = regularCents - finalCents
            )
        }

        return FareResult(
            distanceMeters = distanceMeters,
            totalCents = finalCents,
            regularCents = regularCents,
            benefit = benefit,
            capped = capped,
            minApplied = minApplied && !capped,
            rawCalculatedCents = rawCalculatedCents
        )
    }

    private fun roundToStep(value: Double, step: Int): Int {
        if (value <= 0.0) return 0
        val steps = (value / step).roundToInt()
        return max(step, steps * step)
    }

    /**
     * Modelo comparativo secundario: esquema por escalones/rangos tradicionales.
     */
    fun computeSteppedFareCents(distanceMeters: Double): Int {
        val km = distanceMeters / 1000.0
        return when {
            km <= 3.0 -> 100
            km <= 8.0 -> 130
            else -> 150
        }
    }
}
