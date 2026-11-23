package com.pasajeya.app.feature.trip.domain

/**
 * Tarifa vigente. El script diario (scripts/update_fuel_index.cjs) toma el precio del
 * diesel en Arequipa, calcula el indice de combustible y escribe en Firestore el
 * banderazo, el costo por km, el minimo y el tope YA indexados. La app no vuelve a
 * multiplicar por el indice: solo lo muestra para que el pasajero vea de donde sale.
 *
 * Los valores por defecto son los del indice 1.13 (diesel a S/ 17.50).
 */
data class FareVersionInput(
    /** Banderazo: lo que cuesta subir, cubre el costo fijo del viaje. */
    val boardingFareCents: Int = 68,
    val baseFarePerKmCents: Int = 10,
    val minFareCents: Int = 100,
    val maxFareCents: Int = 150,
    /** Indice del dia (precio actual / precio base). Informativo: ya viene aplicado. */
    val fuelIndex: Double = 1.13,
    val roundingStepCents: Int = 10
)
