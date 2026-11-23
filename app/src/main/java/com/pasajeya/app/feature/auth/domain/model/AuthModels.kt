package com.pasajeya.app.feature.auth.domain.model

data class AuthUser(
    val uid: String,
    val email: String,
    val emailVerified: Boolean
)

/**
 * Roles de PasajeYa!.
 * - [PASSENGER]: quien viaja.
 * - [DRIVER]: el conductor de la unidad. Se guarda como "operator" para seguir siendo
 *   compatible con las cuentas y las Cloud Functions que ya existen.
 * - [COMPANY]: el operador (empresa o consorcio concesionario) que paga la suscripción
 *   por unidad y administra a sus conductores.
 */
enum class UserRole(val wireValue: String) {
    PASSENGER("passenger"),
    DRIVER("operator"),
    COMPANY("company");

    companion object {
        fun fromWire(value: Any?): UserRole? = when (value) {
            "passenger" -> PASSENGER
            "operator", "driver" -> DRIVER
            "company" -> COMPANY
            else -> null
        }
    }
}

data class DeviceSession(
    val uid: String,
    val sessionId: String,
    val profileComplete: Boolean,
    val emailVerified: Boolean,
    val role: UserRole?,
    val operatorComplete: Boolean
)

data class UserProfile(
    val role: UserRole,
    val firstName: String,
    val lastName: String,
    val privacyConsentVersion: String
)

data class RouteOption(
    val id: String,
    val name: String,
    val stopsCount: Int
)

data class RemoteSession(
    val sessionId: String?,
    val fromCache: Boolean
)

data class OperatorRegistration(
    val plate: String,
    val routeIds: List<String>,
    val status: String
)
