package com.pasajeya.app.feature.company.domain

/**
 * Precio de la suscripcion por unidad. El formal paga menos que el informal: es el
 * incentivo economico a formalizarse, no solo una insignia.
 */
object SubscriptionPricing {

    /** Conductor independiente, sin empresa que lo avale: S/ 1.30 al dia. */
    const val INDEPENDENT_DAILY_CENTS = 130

    /** Empresa formal con RUC: S/ 1.00 al dia por unidad. */
    const val COMPANY_DAILY_CENTS = 100

    /** Empresa formal grande: S/ 0.80 al dia por unidad. */
    const val LARGE_FLEET_DAILY_CENTS = 80

    /** Unidades desde las que aplica el precio de flota grande. */
    const val LARGE_FLEET_UNITS = 50

    const val DAYS_PER_MONTH = 30

    fun dailyCentsFor(units: Int, isFormalCompany: Boolean): Int = when {
        !isFormalCompany -> INDEPENDENT_DAILY_CENTS
        units >= LARGE_FLEET_UNITS -> LARGE_FLEET_DAILY_CENTS
        else -> COMPANY_DAILY_CENTS
    }

    fun monthlyCents(units: Int, isFormalCompany: Boolean): Long =
        units.toLong() * dailyCentsFor(units, isFormalCompany) * DAYS_PER_MONTH

    /** Cuanto se ahorra al mes una empresa formal frente al precio de informal. */
    fun monthlySavingCents(units: Int): Long =
        monthlyCents(units, isFormalCompany = false) - monthlyCents(units, isFormalCompany = true)

    /** Descuento en porcentaje frente al precio de informal. */
    fun discountPercent(units: Int): Int {
        val independent = INDEPENDENT_DAILY_CENTS
        val formal = dailyCentsFor(units, isFormalCompany = true)
        return ((independent - formal) * 100) / independent
    }
}
