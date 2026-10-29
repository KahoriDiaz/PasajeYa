package com.pasajeya.app.feature.trip.ui.operator

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pasajeya.app.R
import com.pasajeya.app.feature.auth.domain.AccountRepository
import com.pasajeya.app.feature.auth.domain.AuthRepository
import com.pasajeya.app.feature.auth.domain.model.RouteOption
import com.pasajeya.app.feature.trip.domain.QrScanGate
import com.pasajeya.app.feature.trip.domain.TripRepository
import com.pasajeya.app.feature.trip.domain.model.OperatorContext
import com.pasajeya.app.feature.trip.domain.model.Shift
import com.pasajeya.app.feature.trip.domain.model.TapInResult
import com.pasajeya.app.feature.trip.domain.model.Trip
import com.pasajeya.app.feature.trip.location.LocationProvider
import com.pasajeya.app.feature.trip.location.ShiftLocationService
import com.pasajeya.app.feature.voice.domain.SpeechAnnouncer
import com.pasajeya.app.feature.voice.domain.VoiceAnnouncementFormatter
import com.pasajeya.app.feature.voice.domain.VoicePreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
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
    val voiceWarning: VoiceWarning? = null
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
    private var tapInCardJob: Job? = null
    private var serviceShiftId: String? = null
    private var observedShiftId: String? = null
    private var noticeSequence = 0L

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
                startLocationService(shift)
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

    fun onQrScanned(rawValue: String) {
        val state = _uiState.value
        val shift = state.shift ?: return
        if (state.isProcessingScan) return
        if (!scanGate.accept(rawValue)) return
        _uiState.update { it.copy(isProcessingScan = true) }
        viewModelScope.launch {
            try {
                val position = locationProvider.currentPosition()
                val result = tripRepository.tapIn(
                    sessionId = sessionId(),
                    shiftId = shift.id,
                    qrToken = rawValue,
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
                val position = locationProvider.currentPosition()
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
        speechAnnouncer.release()
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
        val text = age?.let {
            voiceAnnouncementFormatter.format(result.passengerFirstName, it)
        }
        if (text == null) {
            _uiState.update { it.copy(voiceWarning = VoiceWarning.INVALID_DATA) }
            return
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
                val routes = accountRepository.loadActiveRoutes()
                    .filter { it.id in context.routeIds }
                _uiState.update { current ->
                    current.copy(
                        isLoading = false,
                        loadFailed = false,
                        context = context,
                        routes = routes,
                        selectedRouteId = current.selectedRouteId
                            ?.takeIf { id -> routes.any { it.id == id } }
                            ?: routes.firstOrNull()?.id
                    )
                }
                observeShift(uid)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        loadFailed = true,
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
                            }
                        )
                    }
                    if (shift == null) {
                        tripsJob?.cancel()
                        tripsJob = null
                        _uiState.update { it.copy(openTrips = emptyList()) }
                        ShiftLocationService.stop(appContext)
                        serviceShiftId = null
                        observedShiftId = null
                    } else {
                        if (shift.id != observedShiftId) {
                            observedShiftId = shift.id
                            observeTrips(shift.id)
                            loadRouteName(shift.routeId)
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
    }
}
