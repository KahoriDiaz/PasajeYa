package com.pasajeya.app.feature.company.domain

/** Una unidad de la flota: el conductor vinculado a la empresa y su vehículo. */
data class CompanyUnit(
    val driverUid: String,
    val driverName: String,
    val plate: String,
    val routeId: String,
    /** El conductor avalado por la empresa cuenta como verificado. */
    val verified: Boolean = false,
    /** La consulta al MTC devolvio que el brevete no habilita. */
    val licenseRejected: Boolean = false
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
    val monthlySavingCents: Long,
    /** Reclamos abiertos contra las unidades de la empresa. */
    val claims: List<CompanyClaim> = emptyList(),
    /** Conductores dados de alta que todavia no se registran en la app. */
    val pendingDrivers: List<PendingDriver> = emptyList()
)

/** Un reclamo de pasajero visto desde la empresa. */
data class CompanyClaim(
    val claimId: String,
    val category: String,
    val plate: String,
    val createdAtMillis: Long
)

/** Conductor dado de alta por la empresa que aun no se registra en la app. */
data class PendingDriver(
    val dniHash: String,
    val fullName: String,
    val plate: String,
    val routeId: String
)

interface CompanyRepository {
    suspend fun loadDashboard(companyUid: String): CompanyDashboard

    /**
     * La empresa registra a su conductor por DNI y placa. Cuando esa persona se registre
     * en la app con ese DNI, queda vinculada y verificada sin que nadie haga nada mas.
     */
    suspend fun registerDriver(
        companyUid: String,
        fullName: String,
        dni: String,
        plate: String,
        routeId: String
    )
}
