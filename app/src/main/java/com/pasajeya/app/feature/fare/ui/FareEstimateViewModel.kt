package com.pasajeya.app.feature.fare.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pasajeya.app.feature.auth.domain.AccountRepository
import com.pasajeya.app.feature.auth.domain.AuthRepository
import com.pasajeya.app.feature.auth.domain.model.RouteOption
import com.pasajeya.app.feature.fare.domain.FareEstimate
import com.pasajeya.app.feature.fare.domain.FareEstimator
import com.pasajeya.app.feature.fare.domain.FareRepository
import com.pasajeya.app.feature.fare.domain.RouteFareContext
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class FareEstimateError { LOAD_ROUTES, LOAD_ROUTE, SAME_STOP }

data class FareEstimateUiState(
    val isLoadingRoutes: Boolean = true,
    val isLoadingRoute: Boolean = false,
    val routes: List<RouteOption> = emptyList(),
    val selectedRouteId: String? = null,
    val context: RouteFareContext? = null,
    val boardingStopIndex: Int = 0,
    val alightingStopIndex: Int = 0,
    val hasUniversityBenefit: Boolean = false,
    val estimate: FareEstimate? = null,
    val error: FareEstimateError? = null
)

@HiltViewModel
class FareEstimateViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val accountRepository: AccountRepository,
    private val fareRepository: FareRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(FareEstimateUiState())
    val uiState: StateFlow<FareEstimateUiState> = _uiState.asStateFlow()

    private var routeJob: Job? = null

    init {
        observeBenefit()
        loadRoutes()
    }

    fun retry() {
        val routeId = _uiState.value.selectedRouteId
        if (routeId == null) loadRoutes() else selectRoute(routeId)
    }

    fun selectRoute(routeId: String) {
        _uiState.update {
            it.copy(
                selectedRouteId = routeId,
                context = null,
                estimate = null,
                boardingStopIndex = 0,
                alightingStopIndex = 0,
                isLoadingRoute = true,
                error = null
            )
        }
        routeJob?.cancel()
        routeJob = viewModelScope.launch {
            try {
                val context = fareRepository.loadRouteFareContext(routeId)
                _uiState.update {
                    it.copy(
                        isLoadingRoute = false,
                        context = context,
                        boardingStopIndex = 0,
                        alightingStopIndex = context.route.stops.lastIndex
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(isLoadingRoute = false, error = FareEstimateError.LOAD_ROUTE)
                }
            }
        }
    }

    fun selectBoarding(index: Int) {
        _uiState.update { it.copy(boardingStopIndex = index, estimate = null, error = null) }
    }

    fun selectAlighting(index: Int) {
        _uiState.update { it.copy(alightingStopIndex = index, estimate = null, error = null) }
    }

    /** Calcula la tarifa con la distancia real entre los paraderos elegidos. */
    fun estimateFare() {
        val state = _uiState.value
        val context = state.context ?: return
        if (state.boardingStopIndex == state.alightingStopIndex) {
            _uiState.update { it.copy(estimate = null, error = FareEstimateError.SAME_STOP) }
            return
        }
        _uiState.update {
            it.copy(
                estimate = computeEstimate(it, context),
                error = null
            )
        }
    }

    private fun computeEstimate(
        state: FareEstimateUiState,
        context: RouteFareContext
    ): FareEstimate = FareEstimator.estimateFare(
        context = context,
        boardingStopIndex = state.boardingStopIndex,
        alightingStopIndex = state.alightingStopIndex,
        hasUniversityBenefit = state.hasUniversityBenefit
    )

    private fun observeBenefit() {
        val uid = authRepository.currentUser()?.uid ?: return
        viewModelScope.launch {
            fareRepository.observeUniversityBenefit(uid)
                .catch { error -> if (error is CancellationException) throw error }
                .collect { active ->
                    _uiState.update { current ->
                        val updated = current.copy(hasUniversityBenefit = active)
                        val context = updated.context
                        if (context != null && updated.estimate != null) {
                            updated.copy(estimate = computeEstimate(updated, context))
                        } else {
                            updated
                        }
                    }
                }
        }
    }

    private fun loadRoutes() {
        _uiState.update { it.copy(isLoadingRoutes = true, error = null) }
        viewModelScope.launch {
            try {
                val routes = accountRepository.loadActiveRoutes()
                _uiState.update { it.copy(isLoadingRoutes = false, routes = routes) }
                routes.firstOrNull()?.let { selectRoute(it.id) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(isLoadingRoutes = false, error = FareEstimateError.LOAD_ROUTES)
                }
            }
        }
    }
}
