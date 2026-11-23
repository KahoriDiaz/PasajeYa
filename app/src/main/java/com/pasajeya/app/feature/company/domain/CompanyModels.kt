package com.pasajeya.app.feature.company.domain

/** Una unidad de la flota: el conductor vinculado a la empresa y su vehículo. */
data class CompanyUnit(
    val driverUid: String,
    val driverName: String,
    val plate: String,
    val routeId: String
)

data class CompanyDashboard(
    val businessName: String,
    val ruc: String,
    val units: List<CompanyUnit>,
    val tripsToday: Int,
    val chargedTodayCents: Long,
    /** Suscripción mensual = unidades × precio del día × 30. */
    val monthlySubscriptionCents: Long,
    /** Precio por unidad al día que le toca a esta empresa. */
    val dailyPerUnitCents: Int,
    /** Descuento frente a lo que pagaría un conductor independiente. */
    val discountPercent: Int,
    /** Ahorro mensual frente al precio de informal. */
    val monthlySavingCents: Long
)

interface CompanyRepository {
    suspend fun loadDashboard(companyUid: String): CompanyDashboard
}
