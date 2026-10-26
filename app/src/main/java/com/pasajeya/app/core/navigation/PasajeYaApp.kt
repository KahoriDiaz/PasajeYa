package com.pasajeya.app.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pasajeya.app.core.ui.LoadingScreen
import com.pasajeya.app.core.ui.StartupErrorScreen

@Composable
fun PasajeYaApp(
    modifier: Modifier = Modifier,
    appViewModel: AppViewModel = hiltViewModel()
) {
    val state by appViewModel.uiState.collectAsStateWithLifecycle()

    when (val current = state) {
        AppUiState.Loading -> LoadingScreen(modifier = modifier)
        AppUiState.StartupError -> StartupErrorScreen(
            onRetry = appViewModel::retry,
            modifier = modifier
        )
        is AppUiState.Ready -> AppNavHost(
            startDestination = current.destination,
            appViewModel = appViewModel,
            modifier = modifier
        )
    }
}
