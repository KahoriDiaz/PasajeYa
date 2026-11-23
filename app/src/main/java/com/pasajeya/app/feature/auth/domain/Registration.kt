package com.pasajeya.app.feature.auth.domain

import java.time.LocalDate

enum class BenefitType(val wireValue: String) {
    REGULAR("regular"),
    UNIVERSITY("university")
}

/**
 * Ruta asignable a un conductor. [id] es el codigo corto que se guarda en
 * `assignedRouteId`; [routeIds] son los trayectos reales (ida/vuelta) del catalogo.
 */
data class OperatorRouteChoice(
    val id: String,
    val name: String,
    val routeIds: List<String>
)

object RegistrationConfig {
    const val INITIAL_WALLET_CENTS = 2_000L
    const val STATUS_ACTIVE = "active"
    const val LICENSE_STATUS_ENABLED = "HABILITADO"

    /** Unica categoria no profesional: no autoriza transporte publico urbano. */
    const val BLOCKED_LICENSE_CATEGORY = "A-I"

    val licenseCategories = listOf("A-I", "A-IIa", "A-IIb", "A-IIIa")

    val routes = listOf(
        OperatorRouteChoice(
            id = "R-CAY-01",
            name = "Alto Cayma - Cercado - UNSA",
            routeIds = listOf("R-CAY-01-IDA", "R-CAY-01-VUELTA")
        ),
        OperatorRouteChoice(
            id = "R-GRAU-C",
            name = "Miguel Grau - Avelino - Cerro Colorado",
            routeIds = listOf("R-GRAU-C-IDA", "R-GRAU-C-VUELTA")
        )
    )

    fun isLicenseCategoryAllowed(category: String): Boolean =
        category in licenseCategories && category != BLOCKED_LICENSE_CATEGORY

    fun routeById(id: String): OperatorRouteChoice? = routes.firstOrNull { it.id == id }
}

/** Guarda el perfil del usuario directamente en Firestore (sin Cloud Functions). */
interface RegistrationRepository {
    suspend fun savePassenger(
        uid: String,
        email: String,
        fullName: String,
        dni: String,
        birthDate: String,
        age: Int,
        benefitType: BenefitType,
        deviceId: String,
        universityCardCode: String? = null,
        universityCardExpiry: LocalDate? = null
    )

    suspend fun saveOperator(
        uid: String,
        email: String,
        fullName: String,
        dni: String,
        birthDate: String,
        age: Int,
        plate: String,
        route: OperatorRouteChoice,
        licenseNumber: String,
        licenseCategory: String,
        deviceId: String,
        companyId: String? = null,
        companyRuc: String? = null
    )

    /** Registra al operador (empresa): su cuenta de representante y el documento `companies/{uid}`. */
    suspend fun saveCompany(
        uid: String,
        email: String,
        fullName: String,
        dni: String,
        birthDate: String,
        age: Int,
        businessName: String,
        ruc: String,
        deviceId: String
    )

    /** Devuelve el uid de la empresa que tiene ese RUC, o null si no existe. */
    suspend fun findCompanyIdByRuc(ruc: String): String?
}

/** Lee el texto de una foto en el telefono (ML Kit, sin red). */
interface DocumentTextRecognizer {
    suspend fun recognize(jpeg: ByteArray, rotationDegrees: Int): String
}
