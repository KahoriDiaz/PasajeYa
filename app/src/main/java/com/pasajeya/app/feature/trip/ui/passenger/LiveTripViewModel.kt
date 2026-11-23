package com.pasajeya.app.feature.trip.ui.passenger

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pasajeya.app.feature.auth.domain.AuthRepository
import com.pasajeya.app.core.map.MapUserPosition
import com.pasajeya.app.feature.fare.domain.FareEstimator
import com.pasajeya.app.feature.fare.domain.FareRepository
import com.pasajeya.app.feature.fare.domain.RouteFareContext
import com.pasajeya.app.feature.fare.domain.RouteGeo
import com.pasajeya.app.feature.trip.domain.GpsFix
import com.pasajeya.app.feature.trip.domain.TripDistanceTracker
import com.pasajeya.app.feature.trip.domain.TripRepository
import com.pasajeya.app.feature.trip.domain.model.Trip
import com.pasajeya.app.feature.trip.location.LocationProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
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
    val busPosition: MapUserPosition? = null,
    /** La distancia viene de la ruta porque el conductor tiene el modo demostracion activo. */
    val isSimulated: Boolean = false,
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

    private var shiftJob: Job? = null

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
                        shiftJob?.cancel()
                        shiftJob = null
                        _uiState.value = LiveTripUiState(
                            permissionGranted = permission.value,
                            busPosition = null
                        )
                    } else {
                        track(uid, trip)
                    }
                }
        }
    }

    private suspend fun track(uid: String, trip: Trip) = coroutineScope {
        val tripId = trip.id
        val shiftId = trip.shiftId
        _uiState.value = LiveTripUiState(
            tripId = tripId,
            isLoading = true,
            permissionGranted = permission.value,
            busPosition = null
        )

        val context: RouteFareContext = try {
            fareRepository.loadRouteFareContext(trip.routeId)
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
        // En modo demostracion la distancia sale de los paraderos de la ruta y no del GPS.
        var simulatedMeters: Double? = null

        fun publish(position: GpsFix?) {
            val meters = simulatedMeters ?: tracker.distanceMeters
            val fare = FareEstimator.fareForDistance(
                context = context,
                distanceMeters = meters,
                hasUniversityBenefit = hasBenefit
            )
            _uiState.update {
                it.copy(
                    isLoading = false,
                    route = context.route,
                    distanceMeters = meters,
                    isSimulated = simulatedMeters != null,
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
                if (simulatedMeters == null) tracker.onFix(fix)
                publish(position = fix)
            }

            // Observar el turno del operador para actualizar la posición del bus en tiempo real.
            shiftJob?.cancel()
            shiftJob = viewModelScope.launch {
                tripRepository.observeShift(shiftId)
                    .catch { error -> if (error is CancellationException) throw error }
                    .collect { shift ->
                        val pos = shift?.let { s ->
                            s.simulatedLatitude?.let { lat ->
                                s.simulatedLongitude?.let { lng ->
                                    MapUserPosition(lat, lng)
                                }
                            }
                        }
                        // La distancia que se cobra la mide la unidad, no el telefono del
                        // pasajero: su odometro solo crece, asi que vale igual si la combi
                        // da la vuelta completa y vuelve a pasar por el mismo paradero.
                        val boardingOdometer = trip.boardingOdometerMeters
                        val odometer = shift?.odometerMeters
                        val boardingIndex = trip.boardingStopIndex
                        if (boardingOdometer != null && odometer != null &&
                            odometer > boardingOdometer
                        ) {
                            simulatedMeters = odometer - boardingOdometer
                        } else if (shift?.simulationEnabled == true && boardingIndex != null) {
                            simulatedMeters = runCatching {
                                FareEstimator.routeDistanceMeters(
                                    context.route.stops,
                                    boardingIndex,
                                    shift.simulatedStopIndex
                                )
                            }.getOrNull() ?: simulatedMeters
                        }
                        _uiState.update { it.copy(busPosition = pos) }
                        publish(position = null)
                    }
            }
    }
}
