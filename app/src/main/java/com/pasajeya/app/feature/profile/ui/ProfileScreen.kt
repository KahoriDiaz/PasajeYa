package com.pasajeya.app.feature.profile.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
import com.pasajeya.app.core.util.formatCents
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen(
    visible: Boolean,
    onDismiss: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    if (!visible) return

    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, BorderLight)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (state.emailVerified && state.email.isNotBlank()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = SuccessGreen.copy(alpha = 0.10f)
                        ),
                        border = BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.35f))
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.profile_email_verified_title),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = SuccessGreen
                            )
                            Text(
                                text = stringResource(R.string.profile_email_verified_body),
                                style = MaterialTheme.typography.bodySmall,
                                color = TextGray,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                } else if (state.email.isNotBlank()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = ReviewAmber.copy(alpha = 0.10f)
                        ),
                        border = BorderStroke(1.dp, ReviewAmber.copy(alpha = 0.35f))
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.profile_email_unverified_title),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = ReviewAmber
                            )
                            Text(
                                text = stringResource(R.string.profile_email_unverified_body),
                                style = MaterialTheme.typography.bodySmall,
                                color = TextGray,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            if (state.resendInProgress) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = ReviewAmber
                                )
                            }
                            if (state.resendError != null) {
                                val errorMsg = state.resendError ?: ""
                                Text(
                                    text = errorMsg,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ErrorRed,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                            if (state.resendSuccess) {
                                Text(
                                    text = stringResource(R.string.profile_email_resend_success),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SuccessGreen,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                            if (!state.resendInProgress) {
                                TextButton(
                                    onClick = { scope.launch { viewModel.resendVerification() } },
                                    enabled = state.resendError == null || !state.resendInProgress
                                ) {
                                    Text(
                                        text = stringResource(R.string.profile_email_resend),
                                        color = PrimaryBlue,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Text(
                text = stringResource(R.string.profile_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            ProfileRow(
                label = stringResource(R.string.profile_field_name),
                value = state.fullName.takeIf { it.isNotBlank() } ?: stringResource(R.string.profile_field_not_available)
            )
            ProfileRow(
                label = stringResource(R.string.profile_field_email),
                value = state.email.takeIf { it.isNotBlank() } ?: stringResource(R.string.profile_field_not_available)
            )
                ProfileRow(
                    label = stringResource(R.string.profile_field_dni),
                    value = state.dni.takeIf { it.isNotBlank() } ?: stringResource(R.string.profile_field_not_available)
                )
                ProfileRow(
                    label = stringResource(R.string.profile_field_birth_date),
                    value = state.birthDate.takeIf { it.isNotBlank() } ?: stringResource(R.string.profile_field_not_available)
                )
                ProfileRow(
                    label = stringResource(R.string.profile_field_age),
                    value = if (state.age > 0) "${state.age} ${stringResource(R.string.profile_field_age_years)}" else stringResource(R.string.profile_field_not_available)
                )
            ProfileRow(
                label = stringResource(R.string.profile_field_role),
                value = state.role.takeIf { it.isNotBlank() } ?: stringResource(R.string.profile_field_not_available)
            )
            ProfileRow(
                label = stringResource(R.string.profile_field_benefit),
                value = state.benefitType
            )
            ProfileRow(
                label = stringResource(R.string.profile_field_balance),
                value = formatCents(state.balanceCents.toLong())
            )
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_close))
            }
        }
    }
}

@Composable
private fun ProfileRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = TextGray,
            modifier = Modifier.weight(0.5f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary,
            modifier = Modifier.weight(0.5f)
        )
    }
}

@Composable
fun ProfileUserIcon(
    onProfileClick: () -> Unit
) {
    TextButton(onClick = onProfileClick) {
        Text(text = stringResource(R.string.profile_open))
    }
}
