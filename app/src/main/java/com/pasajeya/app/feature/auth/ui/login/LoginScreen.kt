package com.pasajeya.app.feature.auth.ui.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pasajeya.app.R
import com.pasajeya.app.core.ui.AuthScreen
import com.pasajeya.app.core.ui.EmailField
import com.pasajeya.app.core.ui.FormCard
import com.pasajeya.app.core.ui.InlineMessage
import com.pasajeya.app.core.ui.MessageTone
import com.pasajeya.app.core.ui.PasswordField
import com.pasajeya.app.core.ui.PrimaryButton
import com.pasajeya.app.core.ui.ScreenHeading
import com.pasajeya.app.feature.auth.domain.model.AuthDestination
import com.pasajeya.app.feature.auth.ui.accountFailureMessage
import com.pasajeya.app.feature.auth.ui.authFailureMessage

@Composable
fun LoginScreen(
    onBack: () -> Unit,
    onForgotPassword: () -> Unit,
    onCreateAccount: () -> Unit,
    onNavigate: (AuthDestination) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LoginViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.events.collect(onNavigate)
    }

    val errorText = when {
        state.lockedSeconds > 0 -> stringResource(
            R.string.login_locked,
            state.lockedSeconds
        )
        state.accountFailure != null -> accountFailureMessage(state.accountFailure!!)
        state.authFailure != null -> authFailureMessage(state.authFailure!!)
        else -> null
    }

    AuthScreen(
        title = stringResource(R.string.login_title),
        modifier = modifier.testTag("login_screen"),
        onBack = onBack
    ) {
        ScreenHeading(
            title = stringResource(R.string.login_title),
            subtitle = stringResource(R.string.login_subtitle)
        )

        FormCard {
            EmailField(
                value = state.email,
                onValueChange = viewModel::onEmailChange,
                enabled = !state.isSubmitting,
                isError = state.emailError,
                errorText = if (state.emailError) {
                    stringResource(R.string.login_email_invalid)
                } else {
                    null
                }
            )
            PasswordField(
                value = state.password,
                onValueChange = viewModel::onPasswordChange,
                visible = state.passwordVisible,
                onToggleVisibility = viewModel::togglePasswordVisibility,
                enabled = !state.isSubmitting,
                isError = state.passwordError,
                errorText = if (state.passwordError) {
                    stringResource(R.string.login_password_required)
                } else {
                    null
                },
                imeAction = ImeAction.Done
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = onForgotPassword,
                    enabled = !state.isSubmitting
                ) {
                    Text(stringResource(R.string.login_forgot_password))
                }
            }
            if (errorText != null) {
                InlineMessage(text = errorText, tone = MessageTone.ERROR)
            }
            PrimaryButton(
                text = stringResource(R.string.login_submit),
                onClick = viewModel::submit,
                enabled = state.lockedSeconds == 0,
                loading = state.isSubmitting
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.login_no_account),
                style = MaterialTheme.typography.bodyMedium
            )
            TextButton(
                onClick = onCreateAccount,
                enabled = !state.isSubmitting
            ) {
                Text(stringResource(R.string.login_create_account))
            }
        }
    }
}
