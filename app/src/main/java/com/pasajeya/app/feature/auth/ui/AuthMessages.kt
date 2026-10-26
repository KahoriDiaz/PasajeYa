package com.pasajeya.app.feature.auth.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.pasajeya.app.R
import com.pasajeya.app.feature.auth.domain.model.AccountFailure
import com.pasajeya.app.feature.auth.domain.model.AuthFailure

@Composable
fun authFailureMessage(failure: AuthFailure): String = when (failure) {
    AuthFailure.InvalidCredentials -> stringResource(R.string.login_invalid)
    AuthFailure.EmailAlreadyInUse -> stringResource(R.string.register_email_in_use)
    AuthFailure.WeakPassword -> stringResource(R.string.register_weak_password)
    AuthFailure.Network -> stringResource(R.string.error_network)
    AuthFailure.TooManyRequests -> stringResource(R.string.error_too_many_requests)
    AuthFailure.NotSignedIn -> stringResource(R.string.error_session)
    AuthFailure.RequiresRecentLogin,
    AuthFailure.Unknown -> stringResource(R.string.error_generic)
}

@Composable
fun accountFailureMessage(failure: AccountFailure): String = when (failure) {
    AccountFailure.NotAuthenticated,
    AccountFailure.SessionInactive -> stringResource(R.string.error_session)
    AccountFailure.DniAlreadyRegistered -> stringResource(R.string.profile_dni_taken)
    AccountFailure.PlateAlreadyRegistered -> stringResource(R.string.operator_plate_taken)
    AccountFailure.RouteUnavailable -> stringResource(R.string.operator_route_unavailable)
    AccountFailure.EmailNotVerified -> stringResource(R.string.profile_email_verified_required)
    AccountFailure.ProfileAlreadyComplete -> stringResource(R.string.profile_already_complete)
    AccountFailure.InvalidData -> stringResource(R.string.profile_invalid_data)
    AccountFailure.Network -> stringResource(R.string.error_network)
    AccountFailure.Unknown -> stringResource(R.string.error_generic)
}
