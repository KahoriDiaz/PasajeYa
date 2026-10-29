package com.pasajeya.app.feature.fare.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pasajeya.app.feature.auth.domain.AccountRepository
import com.pasajeya.app.feature.auth.domain.AuthRepository
import com.pasajeya.app.feature.auth.domain.model.RouteOption
import com.pasajeya.app.feature.trip.domain.TripRepository
import com.pasajeya.app.feature.trip.domain.model.FareBreakdown
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FareEstimateUiState(
    val isLoading: Boolean = false,
    val routes: List<RouteOption> = emptyList(),
    val selectedRouteId: String? = null,
    val boardingStopIndex: Int = 0,
    val alightingStopIndex: Int = 0,
    val estimate: FareBreakdown? = null,
    val error: String? = null
)

@HiltViewModel
class FareEstimateViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val accountRepository: AccountRepository,
    private val tripRepository: TripRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(FareEstimateUiState())
    val uiState: StateFlow<FareEstimateUiState> = _uiState.asStateFlow()

    init {
        loadRoutes()
    }

    private fun loadRoutes() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val routes = accountRepository.loadActiveRoutes()
                _uiState.update { it.copy(
                    isLoading = false, 
                    routes = routes,
                    selectedRouteId = routes.firstOrNull()?.id
                ) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun selectRoute(routeId: String) {
        _uiState.update { it.copy(selectedRouteId = routeId, estimate = null) }
    }

    fun selectStops(boarding: Int, alighting: Int) {
        _uiState.update { it.copy(boardingStopIndex = boarding, alightingStopIndex = alighting, estimate = null) }
    }

    fun estimate() {
        val state = _uiState.value
        val routeId = state.selectedRouteId ?: return
        
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val uid = authRepository.currentUser()?.uid ?: throw IllegalStateException("No auth")
                val sessionId = accountRepository.deviceSession.first { it?.uid == uid }?.sessionId ?: ""
                
                val result = tripRepository.estimateFare(
                    sessionId = sessionId,
                    routeId = routeId,
                    boardingStopIndex = state.boardingStopIndex,
                    alightingStopIndex = state.alightingStopIndex
                )
                _uiState.update { it.copy(isLoading = false, estimate = result) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }
}
