package com.pasajeya.app.feature.profile.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pasajeya.app.R
import com.pasajeya.app.core.theme.BorderLight
import com.pasajeya.app.core.theme.ErrorRed
import com.pasajeya.app.core.theme.PrimaryBlue
import com.pasajeya.app.core.theme.ReviewAmber
import com.pasajeya.app.core.theme.SuccessGreen
import com.pasajeya.app.core.theme.TextGray
import com.pasajeya.app.core.theme.TextPrimary
import com.pasajeya.app.core.ui.AuthScreen
import com.pasajeya.app.core.ui.FormCard
import com.pasajeya.app.core.ui.SecondaryTextButton
import com.pasajeya.app.core.util.formatCents
import kotlinx.coroutines.launch

/** Perfil completo: todos los datos de la cuenta en una sola pantalla. */
@Composable
fun ProfileDetailScreen(
    onBack: (() -> Unit)? = null,
    onLogout: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    /** La barra de abajo depende del rol de la cuenta que esta en sesion. */
    bottomBar: @Composable (role: String) -> Unit = {},
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val notAvailable = stringResource(R.string.profile_field_not_available)

    AuthScreen(
        title = stringResource(R.string.profile_title),
        onBack = onBack,
        bottomBar = { bottomBar(state.roleWire) },
        modifier = modifier.testTag("profile_screen")
    ) {
        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
            }
        }

        // Cabecera con la inicial del nombre.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(64.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = state.fullName.trim().take(1).uppercase().ifBlank { "·" },
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = state.fullName.ifBlank { notAvailable },
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary
                )
                Text(
                    text = state.email.ifBlank { notAvailable },
                    style = MaterialTheme.typography.bodySmall,
                    color = TextGray
                )
                Text(
                    text = state.role.ifBlank { notAvailable },
                    style = MaterialTheme.typography.labelMedium,
                    color = PrimaryBlue
                )
            }
        }

        EmailStatusCard(
            state = state,
            onResend = { scope.launch { viewModel.resendVerification() } }
        )

        FormCard {
            Text(
                text = stringResource(R.string.profile_section_personal),
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary
            )
            ProfileRow(stringResource(R.string.profile_field_dni), state.dni.ifBlank { notAvailable })
            ProfileRow(
                stringResource(R.string.profile_field_birth_date),
                state.birthDate.ifBlank { notAvailable }
            )
            ProfileRow(
                stringResource(R.string.profile_field_age),
                if (state.age > 0) {
                    "${state.age} ${stringResource(R.string.profile_field_age_years)}"
                } else {
                    notAvailable
                }
            )
        }

        FormCard {
            Text(
                text = stringResource(R.string.profile_section_travel),
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary
            )
            ProfileRow(stringResource(R.string.profile_field_benefit), state.benefitType)
            ProfileRow(
                stringResource(R.string.profile_field_balance),
                formatCents(state.balanceCents.toLong())
            )
            Text(
                text = stringResource(R.string.profile_balance_note),
                style = MaterialTheme.typography.bodySmall,
                color = TextGray
            )
        }

        if (onLogout != null) {
            SecondaryTextButton(
                text = stringResource(R.string.action_logout),
                onClick = onLogout
            )
        }
    }
}

@Composable
private fun EmailStatusCard(state: ProfileUiState, onResend: () -> Unit) {
    if (state.email.isBlank()) return
    val verified = state.emailVerified
    val tone = if (verified) SuccessGreen else ReviewAmber

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = tone.copy(alpha = 0.10f)),
        border = BorderStroke(1.dp, tone.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = if (verified) {
                    stringResource(R.string.profile_email_verified_title)
                } else {
                    stringResource(R.string.profile_email_unverified_title)
                },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = tone
            )
            Text(
                text = if (verified) {
                    stringResource(R.string.profile_email_verified_body)
                } else {
                    stringResource(R.string.profile_email_unverified_body)
                },
                style = MaterialTheme.typography.bodySmall,
                color = TextGray
            )
            if (!verified) {
                if (state.resendInProgress) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = ReviewAmber
                    )
                } else {
                    TextButton(onClick = onResend) {
                        Text(
                            text = stringResource(R.string.profile_email_resend),
                            color = PrimaryBlue,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                state.resendError?.let { error ->
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = ErrorRed,
                        textAlign = TextAlign.Center
                    )
                }
                if (state.resendSuccess) {
                    Text(
                        text = stringResource(R.string.profile_email_resend_success),
                        style = MaterialTheme.typography.bodySmall,
                        color = SuccessGreen
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = TextGray,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
        }
        HorizontalDivider(color = BorderLight)
    }
}

/** Acceso al perfil desde la barra superior. */
@Composable
fun ProfileUserIcon(onProfileClick: () -> Unit) {
    IconButton(onClick = onProfileClick, modifier = Modifier.testTag("open_profile")) {
        Icon(
            imageVector = Icons.Filled.AccountCircle,
            contentDescription = stringResource(R.string.profile_open),
            tint = MaterialTheme.colorScheme.onPrimary
        )
    }
}
