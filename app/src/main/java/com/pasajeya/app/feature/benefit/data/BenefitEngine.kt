package com.pasajeya.app.feature.benefit.data

import com.pasajeya.app.feature.benefit.domain.Benefit
import com.pasajeya.app.feature.trip.domain.model.FareBreakdown

/**
 * Motor de cálculo de beneficios universitarios.
 * Aplica un descuento del 50 % si el beneficio tiene kind == "university" y percent == 50.
 */
object BenefitEngine {

    /**
     * Aplica el descuento universitario al desglose de tarifa.
     * Si el beneficio es universitario con 50% de descuento, el total se reduce a la mitad.
     * En caso contrario, se devuelve el total original.
     */
    fun applyUniversityBenefit(breakdown: FareBreakdown): Int {
        val benefit = breakdown.benefit
        if (benefit != null && benefit.kind == "university" && benefit.percent == 50) {
            // Descuento del 50 % sobre el total
            return (breakdown.totalCents / 2)
        }
        return breakdown.totalCents
    }
}