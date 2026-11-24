package com.pasajeya.app.feature.claims.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pasajeya.app.R
import com.pasajeya.app.core.theme.TextGray
import com.pasajeya.app.core.ui.AuthScreen
import com.pasajeya.app.core.ui.FormCard
import com.pasajeya.app.core.ui.InlineMessage
import com.pasajeya.app.core.ui.MessageTone
import com.pasajeya.app.core.ui.PrimaryButton
import com.pasajeya.app.core.ui.ScreenHeading
import com.pasajeya.app.feature.claims.domain.ClaimCategory

@Composable
fun ClaimScreen(
    tripId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ClaimViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val missingCategory = stringResource(R.string.claim_error_category)
    val genericError = stringResource(R.string.claim_error_generic)

    LaunchedEffect(tripId) { viewModel.start(tripId) }

    AuthScreen(
        title = stringResource(R.string.claim_title),
        onBack = onBack,
        modifier = modifier.testTag("claim_screen")
    ) {
        ScreenHeading(
            title = stringResource(R.string.claim_title),
            subtitle = stringResource(R.string.claim_subtitle)
        )

        if (state.sent || state.alreadySent) {
            InlineMessage(
                text = stringResource(R.string.claim_sent),
                tone = MessageTone.SUCCESS
            )
            Text(
                text = stringResource(R.string.claim_sent_detail),
                style = MaterialTheme.typography.bodySmall,
                color = TextGray
            )
            PrimaryButton(text = stringResource(R.string.action_back), onClick = onBack)
            return@AuthScreen
        }

        state.error?.let { InlineMessage(text = it, tone = MessageTone.ERROR) }

        FormCard {
            Text(
                text = stringResource(R.string.claim_pick_reason),
                style = MaterialTheme.typography.titleMedium
            )
            ClaimCategory.entries.forEach { category ->
                val selected = state.category == category
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = selected,
                            onClick = { viewModel.onCategoryChange(category) }
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    RadioButton(
                        selected = selected,
                        onClick = { viewModel.onCategoryChange(category) }
                    )
                    Text(
                        text = stringResource(category.labelRes()),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        FormCard {
            Text(
                text = stringResource(R.string.claim_details_title),
                style = MaterialTheme.typography.titleMedium
            )
            OutlinedTextField(
                value = state.details,
                onValueChange = viewModel::onDetailsChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("claim_details"),
                minLines = 3,
                label = { Text(stringResource(R.string.claim_details_label)) }
            )
            val trip = state.trip
            if (trip != null) {
                Text(
                    text = stringResource(R.string.claim_context, trip.routeId),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextGray
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton(
                text = stringResource(R.string.claim_send),
                onClick = { viewModel.submit(missingCategory, genericError) },
                loading = state.isSubmitting,
                enabled = !state.isSubmitting
            )
            Text(
                text = stringResource(R.string.claim_routing_note),
                style = MaterialTheme.typography.bodySmall,
                color = TextGray
            )
        }
    }
}

private fun ClaimCategory.labelRes(): Int = when (this) {
    ClaimCategory.OVERCHARGE -> R.string.claim_cat_overcharge
    ClaimCategory.BENEFIT -> R.string.claim_cat_benefit
    ClaimCategory.REFUSED -> R.string.claim_cat_refused
    ClaimCategory.TREATMENT -> R.string.claim_cat_treatment
    ClaimCategory.SAFETY -> R.string.claim_cat_safety
    ClaimCategory.VEHICLE -> R.string.claim_cat_vehicle
    ClaimCategory.OTHER -> R.string.claim_cat_other
}
