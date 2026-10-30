package com.pasajeya.app.feature.fare.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pasajeya.app.R
import com.pasajeya.app.core.map.RouteMap
import com.pasajeya.app.core.theme.BorderLight
import com.pasajeya.app.core.theme.SuccessGreen
import com.pasajeya.app.core.theme.TextGray
import com.pasajeya.app.core.ui.AuthScreen
import com.pasajeya.app.core.ui.InlineMessage
import com.pasajeya.app.core.ui.MessageTone
import com.pasajeya.app.core.util.formatCentsInt
import com.pasajeya.app.feature.fare.domain.FareEstimate
import com.pasajeya.app.feature.fare.domain.RouteStop
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FareEstimateScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FareEstimateViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var routesExpanded by remember { mutableStateOf(false) }
    val stops = state.context?.route?.stops.orEmpty()

    AuthScreen(
        title = stringResource(R.string.estimate_title),
        onBack = onBack,
        modifier = modifier.testTag("fare_estimate_screen")
    ) {
        when {
            state.isLoadingRoutes -> CircularProgressIndicator(
                modifier = Modifier
                    .size(32.dp)
                    .align(Alignment.CenterHorizontally)
            )
            state.routes.isEmpty() && state.error == null -> InlineMessage(
                text = stringResource(R.string.estimate_empty_routes),
                tone = MessageTone.INFO
            )
        }

        state.error?.let { error ->
            InlineMessage(text = errorText(error), tone = MessageTone.ERROR)
            if (error != FareEstimateError.SAME_STOP) {
                OutlinedButton(onClick = viewModel::retry) {
                    Text(stringResource(R.string.action_retry))
                }
            }
        }

        if (state.routes.isNotEmpty()) {
            ExposedDropdownMenuBox(
                expanded = routesExpanded,
                onExpandedChange = { routesExpanded = !routesExpanded }
            ) {
                OutlinedTextField(
                    value = state.routes.find { it.id == state.selectedRouteId }?.name
                        ?: stringResource(R.string.estimate_select_route),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.estimate_route)) },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = routesExpanded)
                    },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = routesExpanded,
                    onDismissRequest = { routesExpanded = false }
                ) {
                    state.routes.forEach { route ->
                        DropdownMenuItem(
                            text = { Text(route.name) },
                            onClick = {
                                viewModel.selectRoute(route.id)
                                routesExpanded = false
                            }
                        )
                    }
                }
            }
        }

        if (state.isLoadingRoute) {
            CircularProgressIndicator(
                modifier = Modifier
                    .size(28.dp)
                    .align(Alignment.CenterHorizontally)
            )
        }

        if (stops.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, BorderLight, RoundedCornerShape(16.dp))
            ) {
                RouteMap(
                    stops = stops,
                    description = stringResource(R.string.estimate_map_description),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp),
                    boardingIndex = state.boardingStopIndex,
                    alightingIndex = state.alightingStopIndex
                )
            }

            StopDropdown(
                label = stringResource(R.string.estimate_origin),
                stops = stops,
                selectedIndex = state.boardingStopIndex,
                onSelected = viewModel::selectBoarding,
                tag = "estimate_origin"
            )
            StopDropdown(
                label = stringResource(R.string.estimate_destination),
                stops = stops,
                selectedIndex = state.alightingStopIndex,
                onSelected = viewModel::selectAlighting,
                tag = "estimate_destination"
            )

            Button(
                onClick = viewModel::estimateFare,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .testTag("estimate_calculate"),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(text = stringResource(R.string.estimate_calculate))
            }
        }

        state.estimate?.let { estimate ->
            FareEstimateResult(
                estimate = estimate,
                hasBenefit = state.hasUniversityBenefit
            )
        }
    }
}

@Composable
private fun errorText(error: FareEstimateError): String = stringResource(
    when (error) {
        FareEstimateError.LOAD_ROUTES -> R.string.estimate_error_routes
        FareEstimateError.LOAD_ROUTE -> R.string.estimate_error_route
        FareEstimateError.SAME_STOP -> R.string.estimate_same_stop
    }
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StopDropdown(
    label: String,
    stops: List<RouteStop>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    tag: String
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = stops.getOrNull(selectedIndex)?.let { stopLabel(selectedIndex, it) }.orEmpty(),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
                .testTag(tag)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            stops.forEachIndexed { index, stop ->
                DropdownMenuItem(
                    text = { Text(stopLabel(index, stop)) },
                    onClick = {
                        onSelected(index)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun stopLabel(index: Int, stop: RouteStop): String =
    stringResource(R.string.estimate_stop_label, index + 1, stop.name)

@Composable
private fun FareEstimateResult(estimate: FareEstimate, hasBenefit: Boolean) {
    val fare = estimate.fare
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, BorderLight)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.estimate_result_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.estimate_total),
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = formatCentsInt(fare.totalCents),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.testTag("estimate_total")
                )
            }
            Text(
                text = stringResource(
                    R.string.estimate_distance,
                    formatKm(fare.distanceMeters)
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = TextGray
            )
            if (fare.benefit != null || hasBenefit) {
                Text(
                    text = stringResource(
                        R.string.estimate_regular,
                        formatCentsInt(fare.regularCents)
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextGray
                )
                fare.benefit?.let {
                    Text(
                        text = stringResource(R.string.estimate_benefit, it.percent),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = SuccessGreen
                    )
                }
            }
            if (fare.capped) {
                Text(
                    text = stringResource(R.string.estimate_capped),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextGray
                )
            }
            if (fare.minApplied) {
                Text(
                    text = stringResource(R.string.estimate_min_applied),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextGray
                )
            }
            Text(
                text = stringResource(R.string.estimate_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = TextGray,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

internal fun formatKm(meters: Double): String =
    String.format(Locale("es", "PE"), "%.2f km", meters / 1000.0)
