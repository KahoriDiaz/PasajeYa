package com.pasajeya.app.feature.fare.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.pasajeya.app.core.theme.ErrorRed
import com.pasajeya.app.core.theme.PrimaryBlue
import com.pasajeya.app.core.theme.ReviewAmber
import com.pasajeya.app.core.theme.SuccessGreen
import com.pasajeya.app.core.theme.TextGray
import com.pasajeya.app.core.theme.TextPrimary
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
import com.pasajeya.app.feature.trip.domain.FareVersionInput

/** Cual de los dos paraderos se esta eligiendo en la hoja de abajo. */
private enum class StopPicker { ORIGIN, DESTINATION }

/**
 * Estimar el pasaje sobre el mapa: la ruta ocupa la pantalla y los datos viven en una hoja
 * de abajo que se puede desplegar, como en las aplicaciones de mapas.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FareEstimateScreen(
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    bottomBar: @Composable () -> Unit = {},
    viewModel: FareEstimateViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val stops = state.context?.route?.stops.orEmpty()

    val fuelRepository = remember { FuelHistoryRepository() }
    var fuelHistory by remember { mutableStateOf<List<FuelRecord>>(emptyList()) }
    var picker by remember { mutableStateOf<StopPicker?>(null) }
    var routePickerOpen by remember { mutableStateOf(false) }
    var sheetExpanded by remember { mutableStateOf(false) }
    var fitRequest by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        // Solo datos reales: si Firestore no tiene historial, la tarjeta no se muestra.
        fuelHistory = fuelRepository.getRecentFuelHistory(limitCount = 5)
    }

    // Al calcular el pasaje la hoja se despliega para mostrar el desglose.
    LaunchedEffect(state.estimate) {
        if (state.estimate != null) sheetExpanded = true
    }

    Scaffold(
        bottomBar = bottomBar,
        modifier = modifier.testTag("fare_estimate_screen")
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (stops.isNotEmpty()) {
                RouteMap(
                    stops = stops,
                    description = stringResource(R.string.estimate_map_description),
                    modifier = Modifier.fillMaxSize(),
                    boardingIndex = state.boardingStopIndex,
                    alightingIndex = state.alightingStopIndex,
                    fitRequest = fitRequest
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    if (state.isLoadingRoutes || state.isLoadingRoute) {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp))
                    }
                }
            }

            // Arriba: la ruta y los dos paraderos, como el buscador de un mapa.
            TripPlanCard(
                routeName = state.routes.find { it.id == state.selectedRouteId }?.name,
                origin = stops.getOrNull(state.boardingStopIndex),
                destination = stops.getOrNull(state.alightingStopIndex),
                originIndex = state.boardingStopIndex,
                destinationIndex = state.alightingStopIndex,
                onBack = onBack,
                onPickRoute = { routePickerOpen = true },
                onPickOrigin = { picker = StopPicker.ORIGIN },
                onPickDestination = { picker = StopPicker.DESTINATION },
                onSwap = {
                    val origin = state.boardingStopIndex
                    viewModel.selectBoarding(state.alightingStopIndex)
                    viewModel.selectAlighting(origin)
                },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 12.dp, vertical = 12.dp)
            )

            // Boton para volver a encuadrar la ruta completa.
            if (stops.size >= 2) {
                IconButton(
                    onClick = { fitRequest++ },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 14.dp)
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    Icon(
                        imageVector = Icons.Filled.CenterFocusStrong,
                        contentDescription = stringResource(R.string.estimate_fit_route),
                        tint = PrimaryBlue
                    )
                }
            }

            EstimateSheet(
                state = state,
                stops = stops,
                fuelHistory = fuelHistory,
                expanded = sheetExpanded,
                onToggle = { sheetExpanded = !sheetExpanded },
                onEstimate = viewModel::estimateFare,
                onRetry = viewModel::retry,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }

    // Elegir paradero: lista completa en una hoja, no un menu diminuto.
    picker?.let { which ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { picker = null },
            sheetState = sheetState
        ) {
            StopList(
                title = stringResource(
                    if (which == StopPicker.ORIGIN) {
                        R.string.estimate_origin
                    } else {
                        R.string.estimate_destination
                    }
                ),
                stops = stops,
                selectedIndex = if (which == StopPicker.ORIGIN) {
                    state.boardingStopIndex
                } else {
                    state.alightingStopIndex
                },
                onSelected = { index ->
                    if (which == StopPicker.ORIGIN) {
                        viewModel.selectBoarding(index)
                    } else {
                        viewModel.selectAlighting(index)
                    }
                    picker = null
                },
                tag = if (which == StopPicker.ORIGIN) {
                    "estimate_origin_list"
                } else {
                    "estimate_destination_list"
                }
            )
        }
    }

    if (routePickerOpen) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { routePickerOpen = false },
            sheetState = sheetState
        ) {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                Text(
                    text = stringResource(R.string.estimate_route),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                state.routes.forEach { route ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.selectRoute(route.id)
                                routePickerOpen = false
                            }
                            .padding(vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Place,
                            contentDescription = null,
                            tint = if (route.id == state.selectedRouteId) {
                                PrimaryBlue
                            } else {
                                TextGray
                            }
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = route.name,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (route.id == state.selectedRouteId) {
                                FontWeight.Bold
                            } else {
                                FontWeight.Normal
                            }
                        )
                    }
                    HorizontalDivider()
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

/** Tarjeta flotante de arriba: ruta elegida, paradero de origen y de destino. */
@Composable
private fun TripPlanCard(
    routeName: String?,
    origin: RouteStop?,
    destination: RouteStop?,
    originIndex: Int,
    destinationIndex: Int,
    onBack: (() -> Unit)?,
    onPickRoute: () -> Unit,
    onPickOrigin: () -> Unit,
    onPickDestination: () -> Unit,
    onSwap: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onBack != null) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                            tint = TextPrimary
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.width(12.dp))
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClick = onPickRoute)
                ) {
                    Text(
                        text = stringResource(R.string.estimate_route),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextGray
                    )
                    Text(
                        text = routeName ?: stringResource(R.string.estimate_select_route),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
                IconButton(onClick = onPickRoute) {
                    Icon(
                        imageVector = Icons.Filled.ExpandMore,
                        contentDescription = stringResource(R.string.estimate_select_route),
                        tint = TextGray
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    StopField(
                        dotColor = SuccessGreen,
                        label = stringResource(R.string.estimate_origin),
                        value = origin?.let { "${originIndex + 1}. ${it.name}" },
                        onClick = onPickOrigin,
                        tag = "estimate_origin"
                    )
                    StopField(
                        dotColor = ErrorRed,
                        label = stringResource(R.string.estimate_destination),
                        value = destination?.let { "${destinationIndex + 1}. ${it.name}" },
                        onClick = onPickDestination,
                        tag = "estimate_destination"
                    )
                }
                IconButton(onClick = onSwap) {
                    Icon(
                        imageVector = Icons.Filled.SwapVert,
                        contentDescription = stringResource(R.string.estimate_swap),
                        tint = PrimaryBlue
                    )
                }
            }
        }
    }
}

@Composable
private fun StopField(
    dotColor: Color,
    label: String,
    value: String?,
    onClick: () -> Unit,
    tag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = TextGray
            )
            Text(
                text = value ?: stringResource(R.string.estimate_pick_stop),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = if (value == null) TextGray else TextPrimary
            )
        }
    }
}

/** Hoja de abajo: el precio y, desplegada, todo el detalle. */
@Composable
private fun EstimateSheet(
    state: FareEstimateUiState,
    stops: List<RouteStop>,
    fuelHistory: List<FuelRecord>,
    expanded: Boolean,
    onToggle: () -> Unit,
    onEstimate: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(if (expanded) Modifier.fillMaxHeight(0.72f) else Modifier)
            .animateContentSize(),
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 12.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .padding(bottom = 16.dp)
        ) {
            // Agarradera, como en las hojas de los mapas.
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .align(Alignment.CenterHorizontally)
                    .size(width = 42.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(BorderLight)
            )

            val estimate = state.estimate
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.estimate_title),
                        style = MaterialTheme.typography.labelMedium,
                        color = TextGray
                    )
                    Text(
                        text = estimate?.let { formatCentsInt(it.fare.totalCents) }
                            ?: stringResource(R.string.estimate_no_result),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (estimate == null) TextGray else PrimaryBlue,
                        modifier = Modifier.testTag("estimate_total")
                    )
                    estimate?.let {
                        Text(
                            text = stringResource(
                                R.string.estimate_distance_summary,
                                "%.2f".format(it.fare.distanceMeters / 1000.0)
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = TextGray
                        )
                    }
                }
                Icon(
                    imageVector = if (expanded) {
                        Icons.Filled.ExpandMore
                    } else {
                        Icons.Filled.ExpandLess
                    },
                    contentDescription = stringResource(R.string.estimate_toggle_detail),
                    tint = TextGray
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (stops.isNotEmpty()) {
                Button(
                    onClick = onEstimate,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 50.dp)
                        .testTag("estimate_calculate"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.MyLocation,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = stringResource(R.string.estimate_calculate))
                }
            }

            state.error?.let { error ->
                Spacer(modifier = Modifier.height(10.dp))
                InlineMessage(text = errorText(error), tone = MessageTone.ERROR)
                if (error != FareEstimateError.SAME_STOP) {
                    OutlinedButton(onClick = onRetry) {
                        Text(stringResource(R.string.action_retry))
                    }
                }
            }

            if (state.routes.isEmpty() && state.error == null && !state.isLoadingRoutes) {
                Spacer(modifier = Modifier.height(10.dp))
                InlineMessage(
                    text = stringResource(R.string.estimate_empty_routes),
                    tone = MessageTone.INFO
                )
            }

            if (expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Spacer(modifier = Modifier.height(4.dp))
                    state.estimate?.let {
                        TransparentFareResult(
                            estimate = it,
                            context = state.context,
                            hasBenefit = state.hasUniversityBenefit
                        )
                    }
                    state.context?.version?.let { version -> TariffTodayCard(version) }
                    FuelHistoryCard(
                        history = fuelHistory,
                        fuelIndex = state.context?.version?.fuelIndex
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            } else {
                TextButton(
                    onClick = onToggle,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text(stringResource(R.string.estimate_see_detail))
                }
            }
        }
    }
}

/** Lista de paraderos para elegir origen o destino. */
@Composable
private fun StopList(
    title: String,
    stops: List<RouteStop>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    tag: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .testTag(tag)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            stops.forEachIndexed { index, stop ->
                val selected = index == selectedIndex
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelected(index) }
                        .padding(vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${index + 1}",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextGray,
                        modifier = Modifier.width(28.dp)
                    )
                    Text(
                        text = stop.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) PrimaryBlue else TextPrimary
                    )
                }
                HorizontalDivider()
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/** Tarifa vigente del dia, indexada al precio del diesel por el script de las 06:00. */
@Composable
private fun TariffTodayCard(version: FareVersionInput) {
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
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = stringResource(R.string.fare_today_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            FareRow(
                label = stringResource(R.string.fare_breakdown_boarding),
                value = formatCentsInt(version.boardingFareCents)
            )
            FareRow(
                label = stringResource(R.string.fare_today_per_km),
                value = formatCentsInt(version.baseFarePerKmCents)
            )
            FareRow(
                label = stringResource(R.string.fare_today_min),
                value = formatCentsInt(version.minFareCents)
            )
            FareRow(
                label = stringResource(R.string.fare_today_max),
                value = formatCentsInt(version.maxFareCents)
            )
            Text(
                text = stringResource(
                    R.string.fare_today_note,
                    "%.2f".format(version.fuelIndex)
                ),
                style = MaterialTheme.typography.bodySmall,
                color = TextGray
            )
            Text(
                text = stringResource(R.string.fare_breakdown_explained),
                style = MaterialTheme.typography.bodySmall,
                color = TextGray
            )
            Text(
                text = stringResource(R.string.fare_benefit_schedule),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = PrimaryBlue
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

@Composable
private fun TransparentFareResult(
    estimate: FareEstimate,
    context: RouteFareContext?,
    hasBenefit: Boolean
) {
    val fare = estimate.fare
    val km = fare.distanceMeters / 1000.0
    val version = context?.version
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
                text = stringResource(R.string.fare_breakdown_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.fare_breakdown_pay),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = formatCentsInt(fare.totalCents),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // Desglose: de donde sale cada centimo.
            FareRow(
                label = stringResource(R.string.fare_breakdown_boarding),
                value = formatCentsInt(fare.boardingCents)
            )
            FareRow(
                label = stringResource(
                    R.string.fare_breakdown_distance,
                    "%.2f".format(km),
                    "%.2f".format(fare.perKmCents / 100.0)
                ),
                value = formatCentsInt(fare.distanceCents)
            )
            HorizontalDivider()
            FareRow(
                label = stringResource(R.string.fare_breakdown_subtotal),
                value = formatCentsInt(fare.rawCalculatedCents.toInt()),
                emphasized = true
            )

            if (fare.minApplied && version != null) {
                Text(
                    text = stringResource(
                        R.string.fare_breakdown_min,
                        formatCentsInt(version.minFareCents)
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextGray
                )
            }
            if (fare.capped) {
                Text(
                    text = stringResource(
                        R.string.fare_breakdown_cap,
                        formatCentsInt(fare.regularCents)
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = SuccessGreen
                )
            }

            if (fare.benefitOutsideSchedule) {
                Text(
                    text = stringResource(R.string.fare_benefit_schedule),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = ReviewAmber
                )
            }

            if (fare.benefit != null) {
                FareRow(
                    label = stringResource(R.string.fare_breakdown_regular),
                    value = formatCentsInt(fare.regularCents)
                )
                Text(
                    text = stringResource(
                        R.string.fare_breakdown_benefit,
                        fare.benefit.percent
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = SuccessGreen
                )
            }

            Text(
                text = stringResource(
                    R.string.fare_breakdown_flat,
                    formatCentsInt(steppedFareCents)
                ),
                style = MaterialTheme.typography.bodySmall,
                color = TextGray
            )
        }
    }
}

@Composable
private fun FareRow(label: String, value: String, emphasized: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (emphasized) MaterialTheme.colorScheme.onSurface else TextGray
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (emphasized) FontWeight.Bold else FontWeight.Normal
        )
    }
}
