package com.pasajeya.app.feature.company.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pasajeya.app.R
import com.pasajeya.app.core.theme.ErrorRed
import com.pasajeya.app.core.theme.PrimaryBlue
import com.pasajeya.app.core.theme.ReviewAmber
import com.pasajeya.app.core.theme.SuccessGreen
import com.pasajeya.app.core.theme.TextGray
import com.pasajeya.app.core.theme.TextPrimary
import com.pasajeya.app.core.ui.AuthScreen
import com.pasajeya.app.core.ui.FormCard
import com.pasajeya.app.core.ui.InlineMessage
import com.pasajeya.app.core.ui.MessageTone
import com.pasajeya.app.core.ui.PrimaryButton
import com.pasajeya.app.core.ui.ScreenHeading
import com.pasajeya.app.core.util.formatCents
import com.pasajeya.app.feature.claims.domain.ClaimCategory
import com.pasajeya.app.feature.auth.domain.model.AuthDestination
import com.pasajeya.app.feature.auth.ui.common.SessionActionsViewModel

/** Panel del Operador (empresa): flota, viajes del día, total cobrado y suscripción. */
@Composable
fun CompanyHomeScreen(
    onNavigate: (AuthDestination) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CompanyViewModel = hiltViewModel(),
    sessionViewModel: SessionActionsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(sessionViewModel) {
        sessionViewModel.events.collect(onNavigate)
    }

    AuthScreen(
        title = stringResource(R.string.company_title),
        modifier = modifier.testTag("company_home_screen"),
        actions = {
            TextButton(onClick = sessionViewModel::signOut) {
                Text(
                    text = stringResource(R.string.action_logout),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    ) {
        val dashboard = state.dashboard
        ScreenHeading(
            title = dashboard?.businessName?.takeIf { it.isNotBlank() }
                ?: stringResource(R.string.company_title),
            subtitle = dashboard?.ruc?.takeIf { it.isNotBlank() }
                ?.let { stringResource(R.string.company_ruc_label, it) }
        )

        if (state.isLoading) {
            Text(text = stringResource(R.string.company_loading), color = TextGray)
        }
        if (state.hasError) {
            InlineMessage(
                text = stringResource(R.string.company_load_error),
                tone = MessageTone.ERROR
            )
            PrimaryButton(
                text = stringResource(R.string.action_retry),
                onClick = viewModel::refresh
            )
        }

        if (dashboard != null) {
            FormCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Metric(
                        label = stringResource(R.string.company_units),
                        value = dashboard.units.size.toString(),
                        modifier = Modifier.weight(1f)
                    )
                    Metric(
                        label = stringResource(R.string.company_trips_today),
                        value = dashboard.tripsToday.toString(),
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Metric(
                        label = stringResource(R.string.company_charged_today),
                        value = formatCents(dashboard.chargedTodayCents),
                        modifier = Modifier.weight(1f)
                    )
                    Metric(
                        label = stringResource(R.string.company_subscription),
                        value = formatCents(dashboard.monthlySubscriptionCents),
                        modifier = Modifier.weight(1f)
                    )
                }
                Text(
                    text = stringResource(
                        R.string.company_subscription_note,
                        formatCents(dashboard.dailyPerUnitCents.toLong())
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextGray
                )
                if (dashboard.discountPercent > 0) {
                    Text(
                        text = stringResource(
                            R.string.company_formal_discount,
                            dashboard.discountPercent,
                            formatCents(dashboard.monthlySavingCents)
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = PrimaryBlue
                    )
                }
            }

            FormCard {
                Text(
                    text = stringResource(R.string.company_fleet_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                if (dashboard.units.isEmpty()) {
                    Text(
                        text = stringResource(R.string.company_fleet_empty, dashboard.ruc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextGray
                    )
                }
                dashboard.units.forEach { unit ->
                    val plateLabel = unit.plate.takeIf { it.isNotBlank() }
                        ?: stringResource(R.string.company_no_plate)
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = plateLabel,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "${unit.driverName} · ${unit.routeId}",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextGray
                        )
                        Text(
                            text = when {
                                unit.licenseRejected ->
                                    stringResource(R.string.company_driver_license_rejected)
                                unit.verified ->
                                    stringResource(R.string.company_driver_verified)
                                else -> stringResource(R.string.company_driver_pending)
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = when {
                                unit.licenseRejected -> ErrorRed
                                unit.verified -> SuccessGreen
                                else -> ReviewAmber
                            }
                        )
                    }
                }
            }

            FormCard {
                Text(
                    text = stringResource(R.string.company_add_driver_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Text(
                    text = stringResource(R.string.company_add_driver_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextGray
                )
                OutlinedTextField(
                    value = state.driverName,
                    onValueChange = viewModel::onDriverNameChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(stringResource(R.string.company_driver_name)) }
                )
                OutlinedTextField(
                    value = state.driverDni,
                    onValueChange = viewModel::onDriverDniChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(stringResource(R.string.company_driver_dni)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                OutlinedTextField(
                    value = state.driverPlate,
                    onValueChange = viewModel::onDriverPlateChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(stringResource(R.string.company_driver_plate)) }
                )
                if (state.driverError) {
                    InlineMessage(
                        text = stringResource(R.string.company_driver_error),
                        tone = MessageTone.ERROR
                    )
                }
                if (state.driverRegistered) {
                    InlineMessage(
                        text = stringResource(R.string.company_driver_registered),
                        tone = MessageTone.SUCCESS
                    )
                }
                PrimaryButton(
                    text = stringResource(R.string.company_add_driver_action),
                    onClick = viewModel::registerDriver,
                    loading = state.isRegisteringDriver,
                    enabled = !state.isRegisteringDriver
                )

                if (dashboard.pendingDrivers.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.company_pending_drivers),
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimary
                    )
                    dashboard.pendingDrivers.forEach { pending ->
                        Text(
                            text = stringResource(
                                R.string.company_pending_driver_line,
                                pending.fullName,
                                pending.plate.ifBlank {
                                    stringResource(R.string.company_no_plate)
                                }
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = TextGray
                        )
                    }
                }
            }

            FormCard {
                Text(
                    text = stringResource(R.string.company_claims_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                if (dashboard.claims.isEmpty()) {
                    Text(
                        text = stringResource(R.string.company_claims_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextGray
                    )
                }
                dashboard.claims.forEach { claim ->
                    Text(
                        text = stringResource(
                            R.string.company_claim_line,
                            claimLabel(claim.category),
                            claim.plate.ifBlank { stringResource(R.string.company_no_plate) }
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary
                    )
                }
            }

            PrimaryButton(
                text = stringResource(R.string.company_refresh),
                onClick = viewModel::refresh
            )
        }
    }
}

@Composable
private fun claimLabel(category: String): String = stringResource(
    when (ClaimCategory.fromWire(category)) {
        ClaimCategory.OVERCHARGE -> R.string.claim_cat_overcharge
        ClaimCategory.BENEFIT -> R.string.claim_cat_benefit
        ClaimCategory.REFUSED -> R.string.claim_cat_refused
        ClaimCategory.TREATMENT -> R.string.claim_cat_treatment
        ClaimCategory.SAFETY -> R.string.claim_cat_safety
        ClaimCategory.VEHICLE -> R.string.claim_cat_vehicle
        ClaimCategory.OTHER -> R.string.claim_cat_other
    }
)

@Composable
private fun Metric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = PrimaryBlue
        )
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = TextGray)
    }
}
