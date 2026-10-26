package com.pasajeya.app.feature.auth.domain.model

enum class AuthDestination {
    WELCOME,
    LOGIN,
    VERIFY_EMAIL,
    CHOOSE_ROLE,
    PASSENGER_HOME,
    OPERATOR_PROFILE,
    OPERATOR_HOME
}

fun resolveAuthDestination(
    emailVerified: Boolean,
    session: DeviceSession
): AuthDestination {
    if (!emailVerified) return AuthDestination.VERIFY_EMAIL
    if (!session.profileComplete) return AuthDestination.CHOOSE_ROLE

    return when (session.role) {
        UserRole.PASSENGER -> AuthDestination.PASSENGER_HOME
        UserRole.OPERATOR ->
            if (session.operatorComplete) AuthDestination.OPERATOR_HOME
            else AuthDestination.OPERATOR_PROFILE
        null -> AuthDestination.CHOOSE_ROLE
    }
}
