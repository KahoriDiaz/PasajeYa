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
import com.pasajeya.app.core.util.Haversine
import com.pasajeya.app.feature.fare.domain.RouteStop
import com.pasajeya.app.feature.trip.domain.model.Shift
import com.pasajeya.app.feature.trip.domain.TripProgress
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

        var hasBenefit = false
        // La distancia la mide la unidad y se calcula igual que al cobrar, con o sin
        // demostracion: el telefono del pasajero solo dice en que paradero va la combi
        // mientras el turno no publique su propia posicion.
        var unitMeters: Double? = null
        var lastShift: Shift? = null
        var simulationOn = false
        var lastStopIndex: Int? = null

        fun publish(position: GpsFix?) {
            val meters = unitMeters ?: 0.0
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
        // que va y la misma distancia. Antes este bloque iba despues del collect del GPS,
        // que nunca termina, asi que no se ejecutaba nunca.
        fun measure(shift: Shift?, stopIndex: Int?) {
            val boardingIndex = trip.boardingStopIndex ?: return
            val simulating = shift?.simulationEnabled == true
            val currentIndex = if (simulating) {
                shift.simulatedStopIndex
            } else {
                stopIndex ?: return
            }
            unitMeters = TripProgress.tripMeters(
                stops = context.route.stops,
                simulationEnabled = simulating,
                boardingStopIndex = boardingIndex,
                stopIndex = currentIndex,
                boardingLaps = trip.boardingLaps ?: shift?.odometerLaps ?: 0,
                laps = shift?.odometerLaps ?: 0,
                boardingOdometerMeters = trip.boardingOdometerMeters,
                unitOdometerMeters = shift?.odometerMeters
            )
        }

        shiftJob?.cancel()
        shiftJob = launch {
            tripRepository.observeShift(shiftId)
                .catch { error -> if (error is CancellationException) throw error }
                .collect { shift ->
                    lastShift = shift
                    val simulating = shift?.simulationEnabled == true
                    simulationOn = simulating
                    // Con demostracion manda la posicion simulada; en marcha real, la
                    // ultima posicion que publica el telefono del conductor.
                    val position = if (simulating) {
                        shift?.simulatedLatitude to shift?.simulatedLongitude
                    } else {
                        (shift?.lastLatitude ?: shift?.simulatedLatitude) to
                            (shift?.lastLongitude ?: shift?.simulatedLongitude)
                    }
                    val busPosition = position.first?.let { lat ->
                        position.second?.let { lng -> MapUserPosition(lat, lng) }
                    }
                    if (!simulating && busPosition != null) {
                        // El paradero por el que va la unidad, segun su propia posicion.
                        lastStopIndex = nearestStopIndex(context.route.stops, busPosition)
                    }
                    measure(shift, lastStopIndex)
                    _uiState.update {
                        it.copy(
                            busPosition = busPosition ?: it.busPosition,
                            currentStopName = currentStopName(
                                stops = context.route.stops,
                                simulating = simulating,
                                simulatedName = shift?.simulatedStopName,
                                stopIndex = lastStopIndex
                            ) ?: it.currentStopName
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
                // El telefono del pasajero no mide el viaje: solo sirve para saber por que
                // paradero va la combi mientras el turno no publique su posicion.
                if (lastShift?.simulationEnabled != true && lastShift?.lastLatitude == null) {
                    lastStopIndex = nearestStopIndex(
                        context.route.stops,
                        MapUserPosition(fix.lat, fix.lng)
                    )
                    measure(lastShift, lastStopIndex)
                }
                publish(position = fix)
            }
    }
}

/** Paradero mas cercano a una posicion, dentro de la ruta. */
private fun nearestStopIndex(stops: List<RouteStop>, position: MapUserPosition): Int {
    if (stops.isEmpty()) return 0
    return stops.indices.minByOrNull { index ->
        Haversine.distanceMeters(
            position.lat,
            position.lng,
            stops[index].lat,
            stops[index].lng
        )
    } ?: 0
}

/** Nombre del paradero por el que va la unidad, con demostracion o sin ella. */
private fun currentStopName(
    stops: List<RouteStop>,
    simulating: Boolean,
    simulatedName: String?,
    stopIndex: Int?
): String? = if (simulating) {
    simulatedName?.takeIf { it.isNotBlank() }
} else {
    stopIndex?.let { stops.getOrNull(it)?.name }?.takeIf { it.isNotBlank() }
}
