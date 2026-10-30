package com.pasajeya.app.feature.benefit.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pasajeya.app.R
import com.pasajeya.app.core.theme.SuccessGreen
import com.pasajeya.app.core.theme.TextGray
import com.pasajeya.app.core.theme.TextPrimary
import com.pasajeya.app.core.ui.AuthScreen
import com.pasajeya.app.core.ui.InlineMessage
import com.pasajeya.app.core.ui.MessageTone
import com.pasajeya.app.core.ui.ScreenHeading
import com.pasajeya.app.feature.benefit.domain.model.BenefitStatus
import com.pasajeya.app.feature.benefit.domain.model.UserBenefit
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BenefitScreen(
    onBack: () -> Unit,
    onVerifyUniversity: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BenefitViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    AuthScreen(
        title = stringResource(R.string.benefit_title),
        onBack = onBack,
        modifier = modifier
    ) {
        ScreenHeading(
            title = stringResource(R.string.benefit_title),
            subtitle = stringResource(R.string.benefit_university_desc)
        )

        state.error?.let {
            InlineMessage(text = it, tone = MessageTone.ERROR)
        }

        // El servidor guarda el avance en benefitRequests.stage; users.benefit sigue en
        // "none" hasta aprobar. Por eso el flujo pendiente se decide por la solicitud.
        val stage = state.request?.stage
        val requestInProgress = stage == "carne_ok" || stage == "code_sent"
        when {
            state.benefit?.status == BenefitStatus.APPROVED -> {
                ApprovedBenefitCard(benefit = state.benefit!!)
            }
            requestInProgress ||
                state.benefit?.status == BenefitStatus.PENDING_CARNE ||
                state.benefit?.status == BenefitStatus.PENDING_CODE -> {
                PendingBenefitCard(
                    status = if (stage == "code_sent") {
                        BenefitStatus.PENDING_CODE
                    } else {
                        state.benefit?.status ?: BenefitStatus.PENDING_CODE
                    },
                    stage = stage,
                    email = state.institutionalEmail,
                    code = state.verificationCode,
                    timer = state.codeTimerSeconds,
                    canResend = state.canResendCode,
                    onEmailChange = viewModel::onEmailChange,
                    onCodeChange = viewModel::onCodeChange,
                    onSendCode = viewModel::requestEmailCode,
                    onConfirmCode = viewModel::confirmCode
                )
            }
            else -> {
                InitialBenefitCard(onVerify = onVerifyUniversity)
            }
        }
    }
}

@Composable
private fun InitialBenefitCard(onVerify: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.School,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
                Text(
                    text = "Beneficio Universitario",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(start = 12.dp)
                )
            }
            Text(
                text = "Si eres estudiante de la UCSM, puedes pagar el 50% de la tarifa regular.",
                color = TextGray
            )
            Button(
                onClick = onVerify,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.benefit_verify_university))
            }
        }
    }
}

@Composable
private fun ApprovedBenefitCard(benefit: UserBenefit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SuccessGreen.copy(alpha = 0.1f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.benefit_status_approved),
                style = MaterialTheme.typography.titleLarge,
                color = SuccessGreen,
                fontWeight = FontWeight.Bold
            )
            benefit.validUntilMillis?.let {
                Text(
                    text = stringResource(R.string.benefit_valid_until, formatDate(it)),
                    color = TextGray
                )
            }
            Text(
                text = "Tu descuento del 50% se aplicará automáticamente en cada viaje.",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun PendingBenefitCard(
    status: BenefitStatus,
    stage: String?,
    email: String,
    code: String,
    timer: Int,
    canResend: Boolean,
    onEmailChange: (String) -> Unit,
    onCodeChange: (String) -> Unit,
    onSendCode: () -> Unit,
    onConfirmCode: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = if (status == BenefitStatus.PENDING_CARNE) 
                    stringResource(R.string.benefit_status_pending_carne) 
                else stringResource(R.string.benefit_status_pending_code),
                style = MaterialTheme.typography.titleMedium
            )

            if (stage == "carne_ok" || stage == "code_sent") {
                Text(text = stringResource(R.string.benefit_step_email_instructions))
                OutlinedTextField(
                    value = email,
                    onValueChange = onEmailChange,
                    label = { Text(stringResource(R.string.benefit_field_email)) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = stage == "carne_ok",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                )
                if (stage == "carne_ok") {
                    Button(onClick = onSendCode, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.benefit_email_button))
                    }
                }
            }

            if (stage == "code_sent") {
                Text(text = stringResource(R.string.benefit_step_code_instructions))
                OutlinedTextField(
                    value = code,
                    onValueChange = onCodeChange,
                    label = { Text(stringResource(R.string.benefit_field_code)) },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                Button(
                    onClick = onConfirmCode,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = code.length == 6
                ) {
                    Text(stringResource(R.string.benefit_code_button))
                }
                
                if (canResend) {
                    TextButton(onClick = onSendCode, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                        Text(stringResource(R.string.benefit_code_resend))
                    }
                } else {
                    Text(
                        text = stringResource(R.string.benefit_code_timer, timer),
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextGray
                    )
                }
            }
        }
    }
}

private fun formatDate(millis: Long): String =
    SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(millis))
