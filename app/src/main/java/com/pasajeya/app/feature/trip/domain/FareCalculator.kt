package com.pasajeya.app.feature.trip.domain

import com.pasajeya.app.feature.fare.domain.FareBenefit
import java.time.LocalDateTime
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

data class FareResult(
    val distanceMeters: Double,
    val totalCents: Int,
    val regularCents: Int,
    val benefit: FareBenefit? = null,
    val capped: Boolean = false,
    val minApplied: Boolean = false,
    val rawCalculatedCents: Double = 0.0,
    /** Desglose para mostrar en pantalla: banderazo + lo que cobro la distancia. */
    val boardingCents: Int = 0,
    val distanceCents: Int = 0,
    /** Tiene el beneficio, pero fuera del horario en que vale. */
    val benefitOutsideSchedule: Boolean = false,
    /** Costo por km ya ajustado por el indice de combustible. */
    val perKmCents: Double = 0.0
)

object FareCalculator {

    /**
     * Tarifa por tramo: banderazo + distancia (km) x costo por km. Los tres parametros
     * (banderazo, costo por km y minimo) ya vienen indexados al combustible del dia, asi
     * que aqui no se vuelve a multiplicar por el indice. Luego se redondea al paso
     * configurado, se aplica la tarifa minima y el tope mas bajo entre el de la ruta y el
     * de la version vigente. El beneficio universitario es el 50 % del pasaje regular.
     */
    fun computeFare(
        distanceMeters: Double,
        version: FareVersionInput,
        hasUniversityBenefit: Boolean = false,
        officialFareCents: Int? = null,
        now: LocalDateTime = LocalDateTime.now()
    ): FareResult {
        val km = distanceMeters.coerceAtLeast(0.0) / 1000.0
        val perKmCents = version.baseFarePerKmCents.coerceAtLeast(0)
        val distanceCents = km * perKmCents
        val boardingCents = version.boardingFareCents.coerceAtLeast(0)
        val rawCalculatedCents = boardingCents + distanceCents
        val step = version.roundingStepCents.takeIf { it > 0 } ?: 5
        val rounded = roundToStep(rawCalculatedCents, step)

        val minCents = version.minFareCents.coerceAtLeast(0)
        val afterMinimum = rounded.coerceAtLeast(minCents)
        val minApplied = afterMinimum > rounded

        // Manda el tope mas bajo: el de la ruta o el de la version vigente.
        val capCents = listOfNotNull(
            officialFareCents?.takeIf { it > 0 },
            version.maxFareCents.takeIf { it > 0 }
        ).minOrNull()
        val regularCents = capCents?.let { afterMinimum.coerceAtMost(it) } ?: afterMinimum
        val capped = capCents != null && afterMinimum > capCents

        var finalCents = regularCents
        var benefit: FareBenefit? = null
        val benefitActive = hasUniversityBenefit && UniversityBenefitSchedule.isActiveAt(now)
        if (benefitActive) {
            // Se redondea a favor del estudiante: nunca paga mas de la mitad.
            finalCents = floorToStep(regularCents / 2.0, step).coerceAtMost(regularCents)
            // Piso universitario: la mitad de la tarifa minima del dia.
            val universityMinCents = floorToStep(version.minFareCents / 2.0, step)
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
            benefitOutsideSchedule = hasUniversityBenefit && !benefitActive,
            rawCalculatedCents = rawCalculatedCents,
            boardingCents = boardingCents,
            distanceCents = distanceCents.roundToInt(),
            perKmCents = perKmCents.toDouble()
        )
    }

    private fun floorToStep(value: Double, step: Int): Int {
        if (value <= 0.0) return 0
        val steps = floor(value / step).toInt()
        return max(step, steps * step)
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
