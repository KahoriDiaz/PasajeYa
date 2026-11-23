package com.pasajeya.app.feature.auth.domain.model

enum class AuthDestination {
    WELCOME,
    LOGIN,
    VERIFY_EMAIL,
    CHOOSE_ROLE,
    PASSENGER_HOME,
    DRIVER_PROFILE,
    DRIVER_HOME,
    COMPANY_HOME
}

/** El destino sale del rol guardado en `users/{uid}`; no depende de sesiones remotas. */
fun resolveAuthDestination(session: DeviceSession): AuthDestination {
    if (!session.profileComplete) return AuthDestination.CHOOSE_ROLE

    return when (session.role) {
        UserRole.PASSENGER -> AuthDestination.PASSENGER_HOME
        UserRole.DRIVER ->
            if (session.operatorComplete) AuthDestination.DRIVER_HOME
            else AuthDestination.DRIVER_PROFILE
        UserRole.COMPANY ->
            if (session.operatorComplete) AuthDestination.COMPANY_HOME
            else AuthDestination.CHOOSE_ROLE
        null -> AuthDestination.CHOOSE_ROLE
    }
}
