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
    /** Suscripción mensual = unidades × S/ 30 (S/ 1 al día por unidad). */
    val monthlySubscriptionCents: Long
)

interface CompanyRepository {
    suspend fun loadDashboard(companyUid: String): CompanyDashboard
}
