package com.pasajeya.app.feature.auth.domain.model

data class AuthUser(
    val uid: String,
    val email: String,
    val emailVerified: Boolean
)

enum class UserRole(val wireValue: String) {
    PASSENGER("passenger"),
    OPERATOR("operator");

    companion object {
        fun fromWire(value: Any?): UserRole? =
            entries.firstOrNull { it.wireValue == value }
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
