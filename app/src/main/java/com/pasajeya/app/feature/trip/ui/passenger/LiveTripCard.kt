package com.pasajeya.app.feature.trip.ui.passenger

import android.Manifest
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.pasajeya.app.R
import com.pasajeya.app.core.map.MapUserPosition
import com.pasajeya.app.core.map.RouteMap
import com.pasajeya.app.core.theme.BorderLight
import com.pasajeya.app.core.theme.PrimaryBlue
import com.pasajeya.app.core.theme.SuccessGreen
import com.pasajeya.app.core.theme.TextGray
import com.pasajeya.app.core.theme.TextPrimary
import com.pasajeya.app.core.util.formatCentsInt
import com.pasajeya.app.feature.fare.ui.formatKm

/**
 * Mapa del viaje activo con una tarjeta flotante que muestra distancia recorrida y costo.
 * Ambos valores se actualizan con cada lectura del GPS (StateFlow del ViewModel).
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun LiveTripCard(
    modifier: Modifier = Modifier,
    viewModel: LiveTripViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    if (!state.hasTrip) return

    val locationPermission = rememberPermissionState(Manifest.permission.ACCESS_FINE_LOCATION)
    LaunchedEffect(locationPermission.status.isGranted) {
        viewModel.onPermissionResult(locationPermission.status.isGranted)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("live_trip_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, BorderLight)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.live_trip_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                modifier = Modifier.padding(horizontal = 6.dp)
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(340.dp)
                    .clip(RoundedCornerShape(12.dp))
            ) {
                val route = state.route
                if (route != null) {
                    RouteMap(
                        stops = route.stops,
                        description = stringResource(R.string.live_trip_map_description),
                        modifier = Modifier.fillMaxSize(),
                        userPosition = state.position?.let { MapUserPosition(it.lat, it.lng) }
                    )
                } else if (state.isLoading) {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp))
                        Text(
                            text = stringResource(R.string.live_trip_loading),
                            color = TextGray
                        )
                    }
                }

                if (route != null) {
                    FloatingFareCard(
                        fareCents = state.fareCents,
                        distanceMeters = state.distanceMeters,
                        benefitPercent = if (state.hasUniversityBenefit) 50 else null,
                        capped = state.capped,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(10.dp)
                    )
                }

                if (!state.permissionGranted) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
                            )
                            .padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.live_trip_permission),
                            textAlign = TextAlign.Center,
                            color = TextPrimary
                        )
                        Button(
                            onClick = { locationPermission.launchPermissionRequest() },
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text(stringResource(R.string.live_trip_permission_action))
                        }
                    }
                }
            }

            state.error?.let {
                Text(
                    text = stringResource(R.string.live_trip_error, it),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 6.dp)
                )
            }
            Text(
                text = stringResource(R.string.live_trip_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = TextGray,
                modifier = Modifier.padding(horizontal = 6.dp)
            )
        }
    }
}

@Composable
private fun FloatingFareCard(
    fareCents: Int,
    distanceMeters: Double,
    benefitPercent: Int?,
    capped: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.live_trip_cost_label),
                style = MaterialTheme.typography.labelMedium,
                color = TextGray
            )
            Text(
                text = formatCentsInt(fareCents),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = PrimaryBlue,
                modifier = Modifier.testTag("live_trip_fare")
            )
            Text(
                text = stringResource(R.string.live_trip_distance, formatKm(distanceMeters)),
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary,
                modifier = Modifier.testTag("live_trip_distance")
            )
            if (benefitPercent != null) {
                Text(
                    text = stringResource(R.string.live_trip_benefit, benefitPercent),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = SuccessGreen
                )
            }
            if (capped) {
                Text(
                    text = stringResource(R.string.live_trip_capped),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextGray
                )
            }
        }
    }
}
