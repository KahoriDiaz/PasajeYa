package com.pasajeya.app.feature.auth.ui.verification

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pasajeya.app.R
import com.pasajeya.app.core.theme.BorderLight
import com.pasajeya.app.core.theme.PrimaryBlue
import com.pasajeya.app.core.ui.AuthScreen
import com.pasajeya.app.core.ui.InlineMessage
import com.pasajeya.app.core.ui.MessageTone
import com.pasajeya.app.core.ui.PrimaryButton
import com.pasajeya.app.core.ui.ScreenHeading
import com.pasajeya.app.core.ui.SecondaryTextButton
import com.pasajeya.app.feature.auth.domain.model.AuthDestination
import com.pasajeya.app.feature.auth.ui.accountFailureMessage

@Composable
fun VerificationScreen(
    onNavigate: (AuthDestination) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: VerificationViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.events.collect(onNavigate)
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(3000)
            val currentState = viewModel.uiState.value
            if (currentState.hasUser && !currentState.isLoadingUser) {
                viewModel.checkVerification()
            }
        }
    }

    val errorText = when {
        !state.hasUser -> stringResource(R.string.verify_missing_user)
        state.accountFailure != null -> accountFailureMessage(state.accountFailure!!)
        state.error == VerificationError.SEND -> stringResource(R.string.verify_send_error)
        state.error == VerificationError.NETWORK -> stringResource(R.string.error_network)
        state.error == VerificationError.TOO_MANY ->
            stringResource(R.string.error_too_many_requests)
        state.error == VerificationError.SESSION -> stringResource(R.string.error_session)
        state.error == VerificationError.GENERIC -> stringResource(R.string.error_generic)
        else -> null
    }

    AuthScreen(
        title = stringResource(R.string.verify_title),
        modifier = modifier.testTag("verification_screen"),
        actions = {
            TextButton(onClick = viewModel::logout) {
                Text(
                    text = stringResource(R.string.action_logout),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    ) {
        ScreenHeading(
            title = stringResource(R.string.verify_title),
            subtitle = stringResource(R.string.verify_subtitle)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            border = BorderStroke(1.dp, BorderLight)
        ) {
            Text(
                text = state.email,
                modifier = Modifier.padding(18.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = PrimaryBlue
            )
        }

        Text(
            text = stringResource(R.string.verify_instructions),
            style = MaterialTheme.typography.bodyLarge
        )

        if (state.info == VerificationInfo.SENT) {
            InlineMessage(
                text = stringResource(R.string.verify_sent),
                tone = MessageTone.SUCCESS
            )
        } else if (state.info == VerificationInfo.NOT_VERIFIED) {
            InlineMessage(
                text = stringResource(R.string.verify_not_yet),
                tone = MessageTone.INFO
            )
        }

        if (errorText != null) {
            InlineMessage(text = errorText, tone = MessageTone.ERROR)
        }

        if (state.cooldownSeconds > 0) {
            Text(
                text = stringResource(
                    R.string.verify_resend_cooldown,
                    state.cooldownSeconds
                ),
                style = MaterialTheme.typography.bodyMedium
            )
        }
        SecondaryTextButton(
            text = stringResource(R.string.verify_resend),
            onClick = viewModel::resend,
            enabled = state.hasUser &&
                state.cooldownSeconds == 0 &&
                !state.isSending &&
                !state.isChecking
        )
        PrimaryButton(
            text = stringResource(R.string.verify_check),
            onClick = viewModel::checkVerification,
            enabled = state.hasUser && !state.isSending,
            loading = state.isChecking || state.isLoadingUser
        )
    }
}
