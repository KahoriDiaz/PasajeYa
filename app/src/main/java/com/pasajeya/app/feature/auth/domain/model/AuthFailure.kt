package com.pasajeya.app.feature.auth.domain.model

sealed interface AuthFailure {
    data object InvalidCredentials : AuthFailure
    data object EmailAlreadyInUse : AuthFailure
    data object WeakPassword : AuthFailure
    data object Network : AuthFailure
    data object TooManyRequests : AuthFailure
    data object RequiresRecentLogin : AuthFailure
    data object NotSignedIn : AuthFailure
    data object Unknown : AuthFailure
}

class AuthException(
    val failure: AuthFailure
) : Exception()
