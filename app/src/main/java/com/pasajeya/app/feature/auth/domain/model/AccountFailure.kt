package com.pasajeya.app.feature.auth.domain.model

sealed interface AccountFailure {
    data object NotAuthenticated : AccountFailure
    data object SessionInactive : AccountFailure
    data object DniAlreadyRegistered : AccountFailure
    data object PlateAlreadyRegistered : AccountFailure
    data object RouteUnavailable : AccountFailure
    data object EmailNotVerified : AccountFailure
    data object ProfileAlreadyComplete : AccountFailure
    data object InvalidData : AccountFailure
    data object Network : AccountFailure
    data object Unknown : AccountFailure
}

class AccountException(
    val failure: AccountFailure
) : Exception()
