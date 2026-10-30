package com.pasajeya.app.feature.trip.ui.passenger

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pasajeya.app.feature.auth.domain.AuthRepository
import com.pasajeya.app.feature.fare.domain.FareEstimator
import com.pasajeya.app.feature.fare.domain.FareRepository
import com.pasajeya.app.feature.fare.domain.RouteFareContext
import com.pasajeya.app.feature.fare.domain.RouteGeo
import com.pasajeya.app.feature.trip.domain.GpsFix
import com.pasajeya.app.feature.trip.domain.TripDistanceTracker
import com.pasajeya.app.feature.trip.domain.TripRepository
import com.pasajeya.app.feature.trip.location.LocationProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Estado en vivo del viaje activo: distancia y costo se actualizan con cada lectura GPS. */
data class LiveTripUiState(
    val tripId: String? = null,
    val isLoading: Boolean = false,
    val route: RouteGeo? = null,
    val permissionGranted: Boolean = false,
    val distanceMeters: Double = 0.0,
    val fareCents: Int = 0,
    val regularCents: Int = 0,
    val hasUniversityBenefit: Boolean = false,
    val capped: Boolean = false,
    val position: GpsFix? = null,
    val error: String? = null
) {
    val hasTrip: Boolean get() = tripId != null
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LiveTripViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val tripRepository: TripRepository,
    private val fareRepository: FareRepository,
    private val locationProvider: LocationProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow(LiveTripUiState())
    val uiState: StateFlow<LiveTripUiState> = _uiState.asStateFlow()

    private val permission = MutableStateFlow(locationProvider.hasPermission())

    init {
        _uiState.update { it.copy(permissionGranted = permission.value) }
        observeActiveTrip()
    }

    fun onPermissionResult(granted: Boolean) {
        permission.value = granted
        _uiState.update { it.copy(permissionGranted = granted) }
    }

    private fun observeActiveTrip() {
        val uid = authRepository.currentUser()?.uid ?: return
        viewModelScope.launch {
            tripRepository.observeActiveTrip(uid)
                .catch { error ->
                    if (error is CancellationException) throw error
                    emit(null)
                }
                .distinctUntilChangedBy { it?.id }
                .collectLatest { trip ->
                    if (trip == null) {
                        _uiState.value = LiveTripUiState(permissionGranted = permission.value)
                    } else {
                        track(uid, trip.id, trip.routeId)
                    }
                }
        }
    }

    private suspend fun track(uid: String, tripId: String, routeId: String) = coroutineScope {
        _uiState.value = LiveTripUiState(
            tripId = tripId,
            isLoading = true,
            permissionGranted = permission.value
        )

        val context: RouteFareContext = try {
            fareRepository.loadRouteFareContext(routeId)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    error = error.message ?: "No se pudo cargar la tarifa de la ruta."
                )
            }
            return@coroutineScope
        }

        val tracker = TripDistanceTracker()
        var hasBenefit = false

        fun publish(position: GpsFix?) {
            val fare = FareEstimator.fareForDistance(
                context = context,
                distanceMeters = tracker.distanceMeters,
                hasUniversityBenefit = hasBenefit
            )
            _uiState.update {
                it.copy(
                    isLoading = false,
                    route = context.route,
                    distanceMeters = tracker.distanceMeters,
                    fareCents = fare.totalCents,
                    regularCents = fare.regularCents,
                    hasUniversityBenefit = hasBenefit,
                    capped = fare.capped,
                    position = position ?: it.position,
                    error = null
                )
            }
        }

        publish(position = null)

        launch {
            fareRepository.observeUniversityBenefit(uid)
                .catch { error -> if (error is CancellationException) throw error }
                .collect { active ->
                    hasBenefit = active
                    publish(position = null)
                }
        }

        permission
            .flatMapLatest { granted ->
                if (granted) locationProvider.locationUpdates() else emptyFlow()
            }
            .catch { error ->
                if (error is CancellationException) throw error
                _uiState.update {
                    it.copy(error = error.message ?: "No se pudo leer el GPS.")
                }
            }
            .collect { fix ->
                tracker.onFix(fix)
                publish(position = fix)
            }
    }
}
