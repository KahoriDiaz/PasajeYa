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

/** El destino sale del rol guardado en `users/{uid}`; no depende de sesiones remotas. */
fun resolveAuthDestination(session: DeviceSession): AuthDestination {
    if (!session.profileComplete) return AuthDestination.CHOOSE_ROLE

    return when (session.role) {
        UserRole.PASSENGER -> AuthDestination.PASSENGER_HOME
        UserRole.OPERATOR ->
            if (session.operatorComplete) AuthDestination.OPERATOR_HOME
            else AuthDestination.OPERATOR_PROFILE
        null -> AuthDestination.CHOOSE_ROLE
    }
}
