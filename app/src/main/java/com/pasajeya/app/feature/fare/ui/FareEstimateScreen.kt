package com.pasajeya.app.feature.fare.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.pasajeya.app.feature.fare.data.FuelHistoryRepository
import com.pasajeya.app.feature.fare.domain.FareEstimate
import com.pasajeya.app.feature.fare.domain.FuelRecord
import com.pasajeya.app.feature.fare.domain.RouteFareContext
import com.pasajeya.app.feature.fare.domain.RouteStop
import com.pasajeya.app.feature.fare.presentation.FuelHistoryCard
import com.pasajeya.app.feature.trip.domain.FareCalculator

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

    val fuelRepository = remember { FuelHistoryRepository() }
    var fuelHistory by remember { mutableStateOf<List<FuelRecord>>(emptyList()) }

    LaunchedEffect(Unit) {
        val records = fuelRepository.getRecentFuelHistory(limitCount = 4)
        if (records.isNotEmpty()) {
            fuelHistory = records
        } else {
            fuelHistory = listOf(
                FuelRecord(price = 17.50, fuelIndex = 1.13, changePercent = 2.3, source = "Osinergmin Arequipa"),
                FuelRecord(price = 17.10, fuelIndex = 1.10, changePercent = 1.8, source = "Osinergmin Arequipa")
            )
        }
    }

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
                    .height(240.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, BorderLight, RoundedCornerShape(16.dp))
            ) {
                RouteMap(
                    stops = stops,
                    description = stringResource(R.string.estimate_map_description),
                    modifier = Modifier.fillMaxWidth().height(240.dp),
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
            TransparentFareResult(
                estimate = estimate,
                context = state.context,
                hasBenefit = state.hasUniversityBenefit
            )
            FuelHistoryCard(
                history = fuelHistory,
                modifier = Modifier.padding(top = 10.dp, bottom = 24.dp)
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
private fun TransparentFareResult(
    estimate: FareEstimate,
    context: RouteFareContext?,
    hasBenefit: Boolean
) {
    val fare = estimate.fare
    val km = fare.distanceMeters / 1000.0
    val fuelFactor = context?.version?.fuelIndex ?: 1.13
    val steppedFareCents = FareCalculator.computeSteppedFareCents(fare.distanceMeters)

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
                text = "1. Tarifa Continua Proporcional (Oficial)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "A pagar (redondeo al alza):",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = formatCentsInt(fare.totalCents),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.testTag("estimate_total")
                )
            }

            Text(
                text = "• Distancia exacta: ${"%.2f".format(km)} km\n" +
                       "• Costo neto continuo: S/ ${"%.2f".format(fare.rawCalculatedCents / 100.0)}\n" +
                       "• Factor Osinergmin: ${"%.2f".format(fuelFactor)}x",
                style = MaterialTheme.typography.bodySmall,
                color = TextGray
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            Text(
                text = "2. Comparativa con Esquema Rígido (Por Zonas):",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Bajo el esquema convencional plano pagarías: ${formatCentsInt(steppedFareCents)}. Con la curva continua de PasajeYa! el pasaje asciende de forma proporcional a los kilómetros reales.",
                style = MaterialTheme.typography.bodySmall,
                color = TextGray
            )

            if (fare.benefit != null || hasBenefit) {
                fare.benefit?.let {
                    Text(
                        text = "✓ Descuento universitario aplicado (${it.percent}%)",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = SuccessGreen
                    )
                }
            }
        }
    }
}
