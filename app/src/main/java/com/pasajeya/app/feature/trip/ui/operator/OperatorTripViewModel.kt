package com.pasajeya.app.feature.trip.ui.operator

import android.content.Context
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pasajeya.app.R
import com.pasajeya.app.feature.auth.domain.AccountRepository
import com.pasajeya.app.feature.auth.domain.AuthRepository
import com.pasajeya.app.feature.auth.domain.model.RouteOption
import com.pasajeya.app.feature.fare.domain.RouteStop
import com.pasajeya.app.feature.trip.domain.BusQrCodec
import com.pasajeya.app.feature.trip.domain.QrScanGate
import com.pasajeya.app.feature.trip.domain.RoutePath
import com.pasajeya.app.feature.trip.domain.TripPosition
import com.pasajeya.app.feature.trip.domain.NfcCredentialProtocol
import com.pasajeya.app.feature.trip.domain.PassengerQrCodec
import com.pasajeya.app.feature.trip.domain.TripRepository
import com.pasajeya.app.feature.trip.domain.model.OperatorContext
import com.pasajeya.app.feature.trip.domain.model.Shift
import com.pasajeya.app.feature.trip.domain.model.TapInResult
import com.pasajeya.app.feature.trip.domain.model.Trip
import com.pasajeya.app.feature.trip.location.LocationProvider
import com.pasajeya.app.feature.trip.location.LocationSimulator
import com.pasajeya.app.feature.trip.location.ShiftLocationService
import com.pasajeya.app.feature.voice.domain.SpeechAnnouncer
import com.pasajeya.app.feature.voice.domain.VoiceAnnouncementFormatter
import com.pasajeya.app.feature.voice.domain.VoicePreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class OperatorNoticeKind {
    SHIFT_STARTED,
    SHIFT_ENDED,
    TAP_IN,
    TAP_OUT,
    TAP_OUT_REVIEW,
    ERROR,
    GENERIC_ERROR,
    LOCATION_DENIED,
    NOTIFICATIONS_DENIED,
    CAMERA_DENIED
}

enum class VoiceWarning {
    UNAVAILABLE,
    INVALID_DATA
}

data class OperatorNotice(
    val id: Long,
    val kind: OperatorNoticeKind,
    val passengerName: String? = null,
    val reviewCount: Int = 0,
    val serverMessage: String? = null
)

data class OperatorTripUiState(
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val context: OperatorContext? = null,
    val routes: List<RouteOption> = emptyList(),
    val selectedRouteId: String? = null,
    val shift: Shift? = null,
    val routeName: String? = null,
    val openTrips: List<Trip> = emptyList(),
    val isStartingShift: Boolean = false,
    val isEndingShift: Boolean = false,
    val isProcessingScan: Boolean = false,
    val tapOutTripId: String? = null,
    val scannerOpen: Boolean = false,
    val endShiftConfirm: Boolean = false,
    val tapOutConfirmation: Trip? = null,
    val notice: OperatorNotice? = null,
    val lastTapIn: TapInResult? = null,
    val voiceEnabled: Boolean = true,
    val voiceWarning: VoiceWarning? = null,
    val busQrPayload: String? = null,
    val simulationEnabled: Boolean = true,
    val simulatedStopIndex: Int = 0,
    val simulatedStopName: String? = null,
    val stopCount: Int = 0,
    val routeStops: List<RouteStop> = emptyList(),
    val emailVerified: Boolean = false,
    /** La unidad llego al ultimo paradero del recorrido. */
    val routeFinished: Boolean = false
)

@HiltViewModel
class OperatorTripViewModel @Inject constructor(
    @param:ApplicationContext private val appContext: Context,
    private val authRepository: AuthRepository,
    private val accountRepository: AccountRepository,
    private val tripRepository: TripRepository,
    private val locationProvider: LocationProvider,
    private val speechAnnouncer: SpeechAnnouncer,
    private val voicePreferences: VoicePreferences,
    private val voiceAnnouncementFormatter: VoiceAnnouncementFormatter
) : ViewModel() {

    private val _uiState = MutableStateFlow(OperatorTripUiState())
    val uiState: StateFlow<OperatorTripUiState> = _uiState.asStateFlow()

    private val _tripFinishedEvents = MutableSharedFlow<String>()
    val tripFinishedEvents: SharedFlow<String> = _tripFinishedEvents.asSharedFlow()

    private val scanGate = QrScanGate()
    private var shiftJob: Job? = null
    private var tripsJob: Job? = null
    private var tripEventsJob: Job? = null
    private var simulationJob: Job? = null
    private var tapInCardJob: Job? = null
    private var serviceShiftId: String? = null
    private var observedShiftId: String? = null
    private var noticeSequence = 0L
    private var routePath: RoutePath? = null
    private var knownTrips: Map<String, Trip> = emptyMap()
    private var simulationShiftId: String? = null

    init {
        load()
        observeVoicePreferences()
    }

    fun retry() {
        load()
    }

    fun selectRoute(routeId: String) {
        _uiState.update { it.copy(selectedRouteId = routeId) }
    }

    fun setVoiceEnabled(enabled: Boolean) {
        _uiState.update {
            it.copy(
                voiceEnabled = enabled,
                voiceWarning = if (enabled) it.voiceWarning else null
            )
        }
        if (!enabled) {
            speechAnnouncer.stop()
        }
        viewModelScope.launch {
            voicePreferences.setVoiceEnabled(enabled)
        }
    }

    fun testVoice() {
        viewModelScope.launch {
            val available = speechAnnouncer.speak(
                appContext.getString(R.string.operator_voice_test_text)
            )
            _uiState.update {
                it.copy(
                    voiceWarning = if (available) null else VoiceWarning.UNAVAILABLE
                )
            }
        }
    }

    fun startShift() {
        val state = _uiState.value
        if (state.isStartingShift || state.shift != null) return
        val routeId = state.selectedRouteId
        if (routeId == null) {
            publishNotice(OperatorNoticeKind.GENERIC_ERROR)
            return
        }
        _uiState.update { it.copy(isStartingShift = true) }
        viewModelScope.launch {
            try {
                val shift = tripRepository.startShift(sessionId(), routeId)
                _uiState.update {
                    it.copy(
                        shift = shift,
                        isStartingShift = false,
                        notice = notice(OperatorNoticeKind.SHIFT_STARTED)
                    )
                }
                prepareShift(shift)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(
                        isStartingShift = false,
                        notice = errorNotice(error)
                    )
                }
            }
        }
    }

    fun requestEndShift() {
        if (_uiState.value.shift == null) return
        _uiState.update { it.copy(endShiftConfirm = true) }
    }

    fun dismissEndShift() {
        _uiState.update { it.copy(endShiftConfirm = false) }
    }

    fun confirmEndShift() {
        val shift = _uiState.value.shift ?: return
        if (_uiState.value.isEndingShift) return
        _uiState.update {
            it.copy(isEndingShift = true, endShiftConfirm = false)
        }
        viewModelScope.launch {
            try {
                val result = tripRepository.endShift(sessionId(), shift.id)
                stopSimulation()
                tripEventsJob?.cancel()
                tripEventsJob = null
                knownTrips = emptyMap()
                ShiftLocationService.stop(appContext)
                serviceShiftId = null
                _uiState.update {
                    it.copy(
                        isEndingShift = false,
                        shift = null,
                        routeName = null,
                        openTrips = emptyList(),
                        notice = notice(
                            OperatorNoticeKind.SHIFT_ENDED,
                            reviewCount = result.reviewCount
                        )
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(isEndingShift = false, notice = errorNotice(error))
                }
            }
        }
    }

    fun openScanner() {
        if (_uiState.value.shift == null) return
        _uiState.update { it.copy(scannerOpen = true, notice = null) }
    }

    fun closeScanner() {
        _uiState.update { it.copy(scannerOpen = false) }
    }

    fun onCameraPermissionDenied() {
        _uiState.update {
            it.copy(
                scannerOpen = false,
                notice = notice(OperatorNoticeKind.CAMERA_DENIED)
            )
        }
    }

    fun onLocationPermissionDenied() {
        publishNotice(OperatorNoticeKind.LOCATION_DENIED)
    }

    fun onNotificationsPermissionDenied() {
        publishNotice(OperatorNoticeKind.NOTIFICATIONS_DENIED)
    }

    fun toggleSimulation(enabled: Boolean) {
        val shift = _uiState.value.shift ?: run {
            _uiState.update { it.copy(simulationEnabled = enabled) }
            return
        }
        _uiState.update { it.copy(simulationEnabled = enabled) }
        viewModelScope.launch {
            val position = currentSimulatedPosition(shift)
            runCatching {
                tripRepository.updateSimulation(
                    shiftId = shift.id,
                    routeId = shift.routeId,
                    stopIndex = _uiState.value.simulatedStopIndex,
                    position = position,
                    enabled = enabled
                )
            }
            if (enabled) startSimulation(shift) else stopSimulation(keepState = true)
        }
    }

    fun onQrScanned(rawValue: String) {
        val state = _uiState.value
        val shift = state.shift ?: return
        if (state.isProcessingScan) return
        if (!scanGate.accept(rawValue)) return
        _uiState.update { it.copy(isProcessingScan = true) }
        viewModelScope.launch {
            try {
                // El QR del pasajero trae el prefijo de la credencial; el viaje se guarda
                // con el uid limpio. Sin quitarlo, la segunda lectura nunca encontraba el
                // viaje abierto y en lugar de Tap Out volvia a decir Tap In.
                val passengerId = PassengerQrCodec.passengerUid(rawValue)
                if (passengerId == null) {
                    _uiState.update {
                        it.copy(
                            isProcessingScan = false,
                            notice = notice(OperatorNoticeKind.GENERIC_ERROR)
                        )
                    }
                    return@launch
                }
                // Si el pasajero ya tiene un viaje abierto en ESTE turno, la segunda lectura
                // es el Tap Out. Un viaje abierto en otra unidad no se toca.
                val existingOpenTrip = tripRepository.findOpenTripForPassenger(
                    passengerUid = passengerId,
                    shiftId = shift.id
                )
                if (existingOpenTrip != null) {
                    _uiState.update {
                        it.copy(
                            tapOutConfirmation = existingOpenTrip,
                            tapOutTripId = null,
                            isProcessingScan = false,
                            scannerOpen = false
                        )
                    }
                    confirmTapOut()
                    return@launch
                }

                val position = currentPositionForShift(shift)
                val result = tripRepository.tapIn(
                    sessionId = sessionId(),
                    shiftId = shift.id,
                    qrToken = passengerId,
                    position = position
                )
                _uiState.update {
                    it.copy(
                        isProcessingScan = false,
                        scannerOpen = false,
                        lastTapIn = result,
                        notice = notice(
                            OperatorNoticeKind.TAP_IN,
                            passengerName = result.passengerFirstName
                        )
                    )
                }
                scheduleTapInCardDismissal(result.tripId)
                announceTapIn(result)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(
                        isProcessingScan = false,
                        notice = errorNotice(error)
                    )
                }
            }
        }
    }

    fun consumeTapIn() {
        tapInCardJob?.cancel()
        _uiState.update { it.copy(lastTapIn = null) }
    }

    /** Lee un tag NFC del pasajero y ejecuta Tap In o Tap Out igual que el escaneo QR. */
    fun onNfcTagDiscovered(tag: Tag) {
        val state = _uiState.value
        val shift = state.shift
        if (shift == null) {
            _uiState.update {
                it.copy(
                    notice = notice(OperatorNoticeKind.GENERIC_ERROR)
                )
            }
            return
        }

        _uiState.update { it.copy(isProcessingScan = true) }
        viewModelScope.launch {
            try {
                val isoDep = IsoDep.get(tag) ?: return@launch
                val response = withContext(Dispatchers.IO) {
                    try {
                        isoDep.connect()
                        isoDep.timeout = 2_000
                        isoDep.transceive(NfcCredentialProtocol.selectApdu())
                    } catch (lost: android.nfc.TagLostException) {
                        null
                    } catch (io: java.io.IOException) {
                        null
                    } finally {
                        runCatching { isoDep.close() }
                    }
                }
                val passengerId = NfcCredentialProtocol.passengerUidFrom(response)
                // Una respuesta que no es credencial de PasajeYa no es un pasajero: no se abre viaje.
                if (passengerId == null) {
                    _uiState.update {
                        it.copy(
                            isProcessingScan = false,
                            notice = errorNotice(IllegalStateException(NfcCredentialProtocol.problemFor(response)))
                        )
                    }
                    return@launch
                }

                // Igual que el QR: segunda lectura en este turno es Tap Out.
                val existingOpenTrip = tripRepository.findOpenTripForPassenger(
                    passengerUid = passengerId,
                    shiftId = shift.id
                )
                if (existingOpenTrip != null) {
                    _uiState.update {
                        it.copy(
                            tapOutConfirmation = existingOpenTrip,
                            tapOutTripId = null,
                            isProcessingScan = false
                        )
                    }
                    confirmTapOut()
                    return@launch
                }

                val position = currentPositionForShift(shift)
                val result = tripRepository.tapIn(sessionId(), shift.id, qrToken = passengerId, position = position)
                _uiState.update {
                    it.copy(
                        isProcessingScan = false,
                        scannerOpen = false,
                        lastTapIn = result,
                        notice = notice(OperatorNoticeKind.TAP_IN, passengerName = result.passengerFirstName)
                    )
                }
                scheduleTapInCardDismissal(result.tripId)
                announceTapIn(result)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(
                        isProcessingScan = false,
                        notice = errorNotice(error)
                    )
                }
            }
        }
    }


    fun requestTapOut(trip: Trip) {
        if (_uiState.value.tapOutTripId != null) return
        _uiState.update { it.copy(tapOutConfirmation = trip) }
    }

    fun dismissTapOut() {
        _uiState.update { it.copy(tapOutConfirmation = null) }
    }

    fun confirmTapOut() {
        val trip = _uiState.value.tapOutConfirmation ?: return
        if (_uiState.value.tapOutTripId != null) return
        _uiState.update {
            it.copy(
                tapOutTripId = trip.id,
                tapOutConfirmation = null
            )
        }
        viewModelScope.launch {
            try {
                val shift = _uiState.value.shift ?: return@launch
                val position = currentPositionForShift(shift)
                val result = tripRepository.tapOut(
                    sessionId = sessionId(),
                    tripId = trip.id,
                    position = position
                )
                _uiState.update {
                    it.copy(
                        tapOutTripId = null,
                        notice = notice(
                            if (result.requiresReview) {
                                OperatorNoticeKind.TAP_OUT_REVIEW
                            } else {
                                OperatorNoticeKind.TAP_OUT
                            },
                            passengerName = trip.passengerFirstName
                        )
                    )
                }
                if (!result.requiresReview) {
                    _tripFinishedEvents.emit(trip.id)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(tapOutTripId = null, notice = errorNotice(error))
                }
            }
        }
    }

    fun consumeNotice() {
        _uiState.update { it.copy(notice = null) }
    }

    override fun onCleared() {
        tapInCardJob?.cancel()
        simulationJob?.cancel()
        tripEventsJob?.cancel()
        // El motor de voz es compartido por toda la app: apagarlo aqui dejaba al conductor
        // sin voz al volver desde otra pestana. Solo se corta lo que este sonando.
        speechAnnouncer.stop()
        super.onCleared()
    }

    private fun observeVoicePreferences() {
        viewModelScope.launch {
            voicePreferences.voiceEnabled.collect { enabled ->
                _uiState.update { it.copy(voiceEnabled = enabled) }
            }
        }
    }

    private fun scheduleTapInCardDismissal(tripId: String) {
        tapInCardJob?.cancel()
        tapInCardJob = viewModelScope.launch {
            delay(TAP_IN_CARD_MILLIS)
            _uiState.update { current ->
                if (current.lastTapIn?.tripId == tripId) {
                    current.copy(lastTapIn = null)
                } else {
                    current
                }
            }
        }
    }

    private suspend fun announceTapIn(result: TapInResult) {
        if (!_uiState.value.voiceEnabled) return

        val age = result.passengerAge
        val formattedName = age?.let { voiceAnnouncementFormatter.format(result.passengerFirstName, it) }
        val text = formattedName
            ?.takeIf { it.isNotBlank() }
            ?: if (result.passengerFirstName.isNotBlank() && voiceAnnouncementFormatter.format(result.passengerFirstName, 0) != null) {
                result.passengerFirstName
            } else if (result.fareType == "university") {
                "Pasajero universitario"
            } else {
                "Pasajero"
            }

        val available = speechAnnouncer.speak(text)
        _uiState.update {
            it.copy(
                voiceWarning = if (available) null else VoiceWarning.UNAVAILABLE
            )
        }
    }

    private fun load() {
        val uid = authRepository.currentUser()?.uid
        if (uid == null) {
            _uiState.update { it.copy(isLoading = false, loadFailed = true) }
            return
        }
        _uiState.update { it.copy(isLoading = true, loadFailed = false) }
        viewModelScope.launch {
            try {
                val context = tripRepository.loadOperatorContext(sessionId())
                val remoteRoutes = accountRepository.loadActiveRoutes()
                    .filter { it.id in context.routeIds }
                val routes = remoteRoutes.ifEmpty {
                    context.routeIds.mapNotNull { routeId ->
                        tripRepository.loadRoutePath(routeId)?.let { route ->
                            RouteOption(
                                id = route.id,
                                name = route.name,
                                stopsCount = route.stops.size
                            )
                        }
                    }
                }
                _uiState.update { current ->
                    current.copy(
                        isLoading = false,
                        loadFailed = false,
                        context = context,
                        routes = routes,
                        selectedRouteId = current.selectedRouteId
                            ?.takeIf { id -> routes.any { it.id == id } }
                            ?: routes.firstOrNull()?.id,
                        emailVerified = authRepository.currentUser()?.emailVerified == true
                    )
                }
                observeShift(uid)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        loadFailed = false,
                        notice = errorNotice(error)
                    )
                }
            }
        }
    }

    private fun observeShift(uid: String) {
        shiftJob?.cancel()
        shiftJob = viewModelScope.launch {
            tripRepository.observeActiveShift(uid)
                .catch { error ->
                    if (error is CancellationException) throw error
                    _uiState.update { it.copy(notice = errorNotice(error)) }
                }
                .collect { shift ->
                    _uiState.update { current ->
                        current.copy(
                            shift = shift,
                            routeName = if (shift?.id == current.shift?.id) {
                                current.routeName
                            } else {
                                null
                            },
                            simulationEnabled = shift?.simulationEnabled
                                ?: current.simulationEnabled,
                            simulatedStopIndex = shift?.simulatedStopIndex
                                ?: current.simulatedStopIndex,
                            simulatedStopName = shift?.simulatedStopName
                                ?: current.simulatedStopName
                        )
                    }
                    if (shift == null) {
                        tripsJob?.cancel()
                        tripsJob = null
                        stopSimulation()
                        tripEventsJob?.cancel()
                        tripEventsJob = null
                        knownTrips = emptyMap()
                        _uiState.update { it.copy(openTrips = emptyList()) }
                        ShiftLocationService.stop(appContext)
                        serviceShiftId = null
                        observedShiftId = null
                    } else {
                        if (shift.id != observedShiftId) {
                            observedShiftId = shift.id
                            observeTrips(shift.id)
                            loadRouteName(shift.routeId)
                            prepareShift(shift)
                        }
                        startLocationService(shift)
                    }
                }
        }
    }

    private fun observeTrips(shiftId: String) {
        tripsJob?.cancel()
        tripsJob = viewModelScope.launch {
            tripRepository.observeOpenTrips(shiftId)
                .catch { error ->
                    if (error is CancellationException) throw error
                    _uiState.update { it.copy(notice = errorNotice(error)) }
                }
                .collect { trips ->
                    _uiState.update { it.copy(openTrips = trips) }
                }
        }
    }

    private fun prepareShift(shift: Shift) {
        _uiState.update {
            it.copy(
                busQrPayload = BusQrCodec.encode(shift.id, shift.routeId, shift.plate)
            )
        }
        observeTripEvents(shift.id)
        viewModelScope.launch {
            val route = runCatching { tripRepository.loadRoutePath(shift.routeId) }.getOrNull()
            routePath = route
            _uiState.update { current ->
                if (current.shift?.id != shift.id) return@update current
                current.copy(
                    routeName = route?.name ?: current.routeName,
                    simulatedStopIndex = current.simulatedStopIndex
                        .coerceIn(0, (route?.stops?.size?.minus(1) ?: 0).coerceAtLeast(0)),
                    simulatedStopName = current.simulatedStopName
                        ?: route?.stops?.firstOrNull()?.name,
                    stopCount = route?.stops?.size ?: 0,
                    routeStops = route?.stops ?: emptyList(),
                    busQrPayload = BusQrCodec.encode(shift.id, shift.routeId, shift.plate)
                )
            }
            if (_uiState.value.simulationEnabled) startSimulation(shift)
        }
    }

    private fun startSimulation(shift: Shift) {
        if (simulationShiftId == shift.id && simulationJob?.isActive == true) return
        simulationJob?.cancel()
        simulationShiftId = shift.id
        simulationJob = viewModelScope.launch {
            _uiState.update { it.copy(routeFinished = false) }
            var index = _uiState.value.simulatedStopIndex
            while (true) {
                val route = routePath
                    ?: runCatching { tripRepository.loadRoutePath(shift.routeId) }.getOrNull()
                if (route == null || route.stops.isEmpty()) break
                routePath = route
                index = index.coerceIn(0, route.stops.lastIndex)
                val simulator = LocationSimulator(route)
                val position = simulator.positionAt(index)
                val stopName = simulator.stopNameAt(index)
                _uiState.update { current ->
                    current.copy(
                        simulationEnabled = true,
                        simulatedStopIndex = index,
                        simulatedStopName = stopName,
                        stopCount = route.stops.size,
                        routeName = route.name
                    )
                }
                runCatching {
                    tripRepository.updateSimulation(
                        shiftId = shift.id,
                        routeId = route.id,
                        stopIndex = index,
                        position = position,
                        enabled = true
                    )
                }
                delay(SIMULATION_STEP_MILLIS)
                val next = simulator.nextIndex(index)
                if (next == index) {
                    // Ultimo paradero: el recorrido de ida termino.
                    _uiState.update { current ->
                        if (current.shift?.id != shift.id) current
                        else current.copy(routeFinished = true)
                    }
                    break
                }
                index = next
            }
        }
    }

    private fun stopSimulation(keepState: Boolean = false) {
        simulationJob?.cancel()
        simulationJob = null
        simulationShiftId = null
        if (!keepState) {
            _uiState.update { it.copy(simulationEnabled = false) }
        }
    }

    /** Vuelve al primer paradero para repetir el recorrido. */
    fun restartRoute() {
        val shift = _uiState.value.shift ?: return
        viewModelScope.launch {
            val route = routePath
                ?: runCatching { tripRepository.loadRoutePath(shift.routeId) }.getOrNull()
            if (route == null || route.stops.isEmpty()) return@launch
            val simulator = LocationSimulator(route)
            runCatching {
                tripRepository.updateSimulation(
                    shiftId = shift.id,
                    routeId = route.id,
                    stopIndex = 0,
                    position = simulator.positionAt(0),
                    enabled = shift.simulationEnabled,
                    resetOdometer = true
                )
            }
            _uiState.update { current ->
                if (current.shift?.id != shift.id) {
                    current
                } else {
                    current.copy(
                        simulatedStopIndex = 0,
                        simulatedStopName = simulator.stopNameAt(0),
                        routeFinished = false
                    )
                }
            }
            if (_uiState.value.simulationEnabled) {
                simulationJob?.cancel()
                simulationShiftId = null
                startSimulation(shift)
            }
        }
    }

    fun advanceStop() {
        val shift = _uiState.value.shift ?: return
        if (!shift.simulationEnabled) return
        val currentIndex = _uiState.value.simulatedStopIndex
        viewModelScope.launch {
            val route = routePath
                ?: runCatching { tripRepository.loadRoutePath(shift.routeId) }.getOrNull()
            if (route == null || route.stops.isEmpty()) return@launch
            val nextIndex = (currentIndex + 1).coerceIn(0, route.stops.lastIndex)
            if (nextIndex == currentIndex) {
                _uiState.update { it.copy(routeFinished = true) }
                return@launch
            }
            val simulator = LocationSimulator(route)
            val position = simulator.positionAt(nextIndex)
            val stopName = simulator.stopNameAt(nextIndex)
            try {
                tripRepository.updateSimulation(
                    shiftId = shift.id,
                    routeId = route.id,
                    stopIndex = nextIndex,
                    position = position,
                    enabled = true
                )
                _uiState.update { current ->
                    if (current.shift?.id != shift.id) return@update current
                    current.copy(
                        simulatedStopIndex = nextIndex,
                        simulatedStopName = stopName,
                        routeName = route.name
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(notice = errorNotice(error))
                }
            }
        }
    }

    private fun currentSimulatedPosition(shift: Shift): TripPosition {
        val route = routePath
        val index = _uiState.value.simulatedStopIndex
        if (route != null && route.stops.isNotEmpty()) {
            return LocationSimulator(route).positionAt(index)
        }
        val lat = shift.simulatedLatitude ?: -16.3989
        val lng = shift.simulatedLongitude ?: -71.5350
        return TripPosition(lat, lng, accuracy = 5.0)
    }

    /** Devuelve la posición simulada si la simulación está activa; si no, el GPS físico. */
    private suspend fun currentPositionForShift(shift: Shift): TripPosition {
        return if (shift.simulationEnabled) {
            currentSimulatedPosition(shift)
        } else {
            locationProvider.currentPosition()
        }
    }

    private fun observeTripEvents(shiftId: String) {
        tripEventsJob?.cancel()
        knownTrips = emptyMap()
        tripEventsJob = viewModelScope.launch {
            tripRepository.observeShiftTrips(shiftId)
                .catch { error ->
                    if (error is CancellationException) throw error
                }
                .collect { trips ->
                    announceTripChanges(knownTrips, trips)
                    knownTrips = trips.associateBy { it.id }
                }
        }
    }

    private suspend fun announceTripChanges(
        previous: Map<String, Trip>,
        current: List<Trip>
    ) {
        if (!_uiState.value.voiceEnabled) return
        current.forEach { trip ->
            val before = previous[trip.id]
            when {
                before == null && trip.status == "open" -> announceWelcome(trip)
                before?.status == "open" && trip.status == "closed" ->
                    announceFarewell(trip)
            }
        }
    }

    private suspend fun announceWelcome(trip: Trip) {
        val text = voiceAnnouncementFormatter.formatWelcome(
            trip.passengerFirstName,
            trip.fareType
        ) ?: return
        val available = speechAnnouncer.speak(text)
        if (!available) {
            _uiState.update { it.copy(voiceWarning = VoiceWarning.UNAVAILABLE) }
        }
    }

    private suspend fun announceFarewell(trip: Trip) {
        val totalCents = trip.fareBreakdown?.totalCents ?: 0
        val text = voiceAnnouncementFormatter.formatFarewell(
            trip.passengerFirstName,
            trip.fareType,
            totalCents
        ) ?: return
        val available = speechAnnouncer.speak(text)
        if (!available) {
            _uiState.update { it.copy(voiceWarning = VoiceWarning.UNAVAILABLE) }
        }
    }

    private fun loadRouteName(routeId: String) {
        viewModelScope.launch {
            val name = tripRepository.loadRouteName(routeId)
            _uiState.update { current ->
                if (current.shift?.routeId == routeId) {
                    current.copy(routeName = name)
                } else {
                    current
                }
            }
        }
    }

    private fun startLocationService(shift: Shift) {
        if (_uiState.value.simulationEnabled) {
            ShiftLocationService.stop(appContext)
            serviceShiftId = null
            return
        }
        if (shift.id == serviceShiftId) return
        if (!locationProvider.hasPermission()) return
        serviceShiftId = shift.id
        ShiftLocationService.start(appContext, shift.id)
    }

    private suspend fun sessionId(): String {
        val uid = authRepository.currentUser()?.uid
            ?: throw IllegalStateException("No hay una cuenta activa.")
        return accountRepository.deviceSession
            .first { it?.uid == uid && !it.sessionId.isBlank() }
            ?.sessionId
            ?: accountRepository.ensureSession(uid).sessionId
    }

    private fun publishNotice(kind: OperatorNoticeKind) {
        _uiState.update { it.copy(notice = notice(kind)) }
    }

    private fun notice(
        kind: OperatorNoticeKind,
        passengerName: String? = null,
        reviewCount: Int = 0
    ): OperatorNotice {
        noticeSequence += 1
        return OperatorNotice(
            id = noticeSequence,
            kind = kind,
            passengerName = passengerName,
            reviewCount = reviewCount
        )
    }

    private fun errorNotice(error: Throwable): OperatorNotice {
        noticeSequence += 1
        return OperatorNotice(
            id = noticeSequence,
            kind = OperatorNoticeKind.ERROR,
            serverMessage = error.message
        )
    }

    private companion object {
        const val TAP_IN_CARD_MILLIS = 4_000L
        const val SIMULATION_STEP_MILLIS = 3_000L
    }
}
