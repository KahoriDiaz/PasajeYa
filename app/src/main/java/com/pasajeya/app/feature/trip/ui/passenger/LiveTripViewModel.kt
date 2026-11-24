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
    val boardingStopName: String? = null,
    val currentStopName: String? = null,
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
            boardingStopName = trip.boardingStopName,
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
        val oneWayMeters = runCatching {
            FareEstimator.routeDistanceMeters(
                context.route.stops,
                0,
                context.route.stops.lastIndex
            )
        }.getOrNull() ?: 0.0
        val maxTripMeters = if (oneWayMeters > 0.0) oneWayMeters * 2 else Double.MAX_VALUE
        var hasBenefit = false
        // La distancia la mide la unidad (odometro del turno); el GPS del telefono solo se
        // usa si el turno todavia no reporta nada.
        var simulatedMeters: Double? = null
        var simulationOn = false

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
                    isSimulated = simulationOn,
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

        // El pasajero ve lo mismo que el conductor: posicion de la unidad, paradero por el
        // que va y la distancia que mide la unidad. Antes este bloque iba despues del
        // collect del GPS, que nunca termina, asi que no se ejecutaba nunca.
        shiftJob?.cancel()
        shiftJob = launch {
            tripRepository.observeShift(shiftId)
                .catch { error -> if (error is CancellationException) throw error }
                .collect { shift ->
                    val simulating = shift?.simulationEnabled == true
                    simulationOn = simulating
                    val position = if (simulating) {
                        shift?.simulatedLatitude?.let { lat ->
                            shift.simulatedLongitude?.let { lng -> MapUserPosition(lat, lng) }
                        }
                    } else {
                        shift?.lastLatitude?.let { lat ->
                            shift.lastLongitude?.let { lng -> MapUserPosition(lat, lng) }
                        } ?: shift?.simulatedLatitude?.let { lat ->
                            shift.simulatedLongitude?.let { lng -> MapUserPosition(lat, lng) }
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
                        // Mismo tope que al cobrar: nadie anda mas que la ruta completa de
                        // ida y vuelta, aunque el odometro venga con un valor raro.
                        simulatedMeters = (odometer - boardingOdometer)
                            .coerceAtMost(maxTripMeters)
                    } else if (simulating && boardingIndex != null) {
                        simulatedMeters = runCatching {
                            FareEstimator.routeDistanceMeters(
                                context.route.stops,
                                boardingIndex,
                                shift.simulatedStopIndex
                            )
                        }.getOrNull() ?: simulatedMeters
                    }
                    _uiState.update {
                        it.copy(
                            busPosition = position ?: it.busPosition,
                            currentStopName = shift?.simulatedStopName?.takeIf { name ->
                                name.isNotBlank() && simulating
                            } ?: it.currentStopName
                        )
                    }
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
    }
}
