package com.pasajeya.app.feature.auth.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pasajeya.app.feature.auth.domain.model.AuthDestination
import com.pasajeya.app.feature.trip.ui.operator.OperatorTripContent
import com.pasajeya.app.feature.trip.ui.operator.OperatorTripViewModel
import com.pasajeya.app.feature.trip.ui.passenger.PassengerQrViewModel
import com.pasajeya.app.feature.trip.ui.passenger.PassengerTripContent

@Composable
fun PassengerHomeScreen(
    onNavigate: (AuthDestination) -> Unit,
    onEstimate: () -> Unit,
    onHistory: () -> Unit,
    onTripFinished: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
    tripViewModel: PassengerQrViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.events.collect(onNavigate)
    }

    LaunchedEffect(tripViewModel) {
        tripViewModel.tripFinishedEvents.collect(onTripFinished)
    }

    PassengerTripContent(
        displayName = state.displayName,
        email = state.email,
        profileLoading = state.isLoading,
        profileError = state.hasError,
        onLogout = viewModel::logout,
        onEstimate = onEstimate,
        onHistory = onHistory,
        viewModel = tripViewModel,
        modifier = modifier
    )
}

@Composable
fun OperatorHomeScreen(
    onNavigate: (AuthDestination) -> Unit,
    onTripFinished: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
    tripViewModel: OperatorTripViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.events.collect(onNavigate)
    }

    LaunchedEffect(tripViewModel) {
        tripViewModel.tripFinishedEvents.collect(onTripFinished)
    }

    OperatorTripContent(
        displayName = state.displayName,
        email = state.email,
        profileLoading = state.isLoading,
        profileError = state.hasError,
        onLogout = viewModel::logout,
        viewModel = tripViewModel,
        modifier = modifier
    )
}
