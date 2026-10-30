package com.pasajeya.app.feature.auth.ui.register

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
import com.pasajeya.app.core.ui.PasswordRequirements
import com.pasajeya.app.core.ui.PrimaryButton
import com.pasajeya.app.core.ui.ScreenHeading
import com.pasajeya.app.core.ui.SecondaryTextButton
import com.pasajeya.app.feature.auth.domain.model.AuthDestination
import com.pasajeya.app.feature.auth.ui.accountFailureMessage
import com.pasajeya.app.feature.auth.ui.authFailureMessage

@Composable
fun RegisterScreen(
    onBack: () -> Unit,
    onLogin: () -> Unit,
    onNavigate: (AuthDestination) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RegisterViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.events.collect(onNavigate)
    }

    val errorText = when {
        state.accountFailure != null -> accountFailureMessage(state.accountFailure!!)
        state.authFailure != null -> authFailureMessage(state.authFailure!!)
        state.passwordsUnavailable -> stringResource(R.string.register_passwords_unavailable)
        else -> null
    }

    AuthScreen(
        title = stringResource(R.string.register_title),
        modifier = modifier.testTag("register_screen"),
        onBack = onBack
    ) {
        ScreenHeading(
            title = stringResource(R.string.register_title),
            subtitle = stringResource(R.string.register_subtitle)
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
                    stringResource(R.string.register_weak_password)
                } else {
                    null
                }
            )
            PasswordField(
                value = state.confirmPassword,
                onValueChange = viewModel::onConfirmPasswordChange,
                visible = state.confirmPasswordVisible,
                onToggleVisibility = viewModel::toggleConfirmPasswordVisibility,
                label = stringResource(R.string.confirm_password_label),
                enabled = !state.isSubmitting,
                isError = state.confirmPasswordError,
                errorText = if (state.confirmPasswordError) {
                    stringResource(R.string.register_password_mismatch)
                } else {
                    null
                },
                imeAction = ImeAction.Done
            )
            PasswordRequirements(validation = state.validation)
            if (state.passwordsUnavailable) {
                SecondaryTextButton(
                    text = stringResource(R.string.action_retry),
                    onClick = viewModel::retryCommonPasswords
                )
            }
            if (errorText != null) {
                InlineMessage(text = errorText, tone = MessageTone.ERROR)
            }
            PrimaryButton(
                text = stringResource(R.string.register_submit),
                onClick = viewModel::submit,
                enabled = !state.isLoadingPasswords,
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
                text = stringResource(R.string.register_have_account),
                style = MaterialTheme.typography.bodyMedium
            )
            TextButton(
                onClick = onLogin,
                enabled = !state.isSubmitting
            ) {
                Text(stringResource(R.string.register_go_login))
            }
        }
    }
}
