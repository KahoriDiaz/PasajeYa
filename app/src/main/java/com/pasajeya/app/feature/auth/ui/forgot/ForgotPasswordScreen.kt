package com.pasajeya.app.feature.auth.ui.forgot

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pasajeya.app.R
import com.pasajeya.app.core.ui.AuthScreen
import com.pasajeya.app.core.ui.EmailField
import com.pasajeya.app.core.ui.FormCard
import com.pasajeya.app.core.ui.InlineMessage
import com.pasajeya.app.core.ui.MessageTone
import com.pasajeya.app.core.ui.PrimaryButton
import com.pasajeya.app.core.ui.ScreenHeading
import com.pasajeya.app.feature.auth.ui.authFailureMessage

@Composable
fun ForgotPasswordScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ForgotPasswordViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    AuthScreen(
        title = stringResource(R.string.forgot_title),
        modifier = modifier.testTag("forgot_password_screen"),
        onBack = onBack
    ) {
        if (state.sent) {
            ScreenHeading(title = stringResource(R.string.forgot_success_title))
            InlineMessage(
                text = stringResource(R.string.forgot_success),
                tone = MessageTone.SUCCESS
            )
            PrimaryButton(
                text = stringResource(R.string.forgot_back),
                onClick = onBack
            )
        } else {
            ScreenHeading(
                title = stringResource(R.string.forgot_title),
                subtitle = stringResource(R.string.forgot_subtitle)
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
                state.failure?.let { failure ->
                    InlineMessage(
                        text = authFailureMessage(failure),
                        tone = MessageTone.ERROR
                    )
                }
                PrimaryButton(
                    text = stringResource(R.string.forgot_submit),
                    onClick = viewModel::submit,
                    loading = state.isSubmitting
                )
            }
        }
    }
}
