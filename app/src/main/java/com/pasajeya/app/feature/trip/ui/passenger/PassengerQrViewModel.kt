package com.pasajeya.app.feature.trip.ui.passenger

import androidx.annotation.VisibleForTesting
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pasajeya.app.feature.auth.domain.AccountRepository
import com.pasajeya.app.feature.auth.domain.AuthRepository
import com.pasajeya.app.feature.trip.domain.TripRepository
import com.pasajeya.app.feature.trip.domain.model.Trip
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class PassengerQrUiState(
    val token: String? = null,
    val expiresAtMillis: Long = 0L,
    val secondsRemaining: Int = 0,
    val isRefreshing: Boolean = true,
    val isOffline: Boolean = false,
    val trip: Trip? = null,
    val routeName: String? = null
)

@HiltViewModel
class PassengerQrViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val accountRepository: AccountRepository,
    private val tripRepository: TripRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PassengerQrUiState())
    val uiState: StateFlow<PassengerQrUiState> = _uiState.asStateFlow()

    private var tokenJob: Job? = null
    private var tripJob: Job? = null

    init {
        val uid = authRepository.currentUser()?.uid
        if (uid != null) {
            observeTrip(uid)
            tokenJob = viewModelScope.launch { refreshTokens() }
        } else {
            _uiState.update { it.copy(isRefreshing = false, isOffline = true) }
        }
    }

    @VisibleForTesting
    internal fun stopForTest() {
        tokenJob?.cancel()
        tripJob?.cancel()
    }

    fun retry() {
        if (tokenJob?.isActive == true) return
        _uiState.update { it.copy(isRefreshing = true, isOffline = false) }
        tokenJob = viewModelScope.launch { refreshTokens() }
    }

    private fun observeTrip(uid: String) {
        tripJob?.cancel()
        tripJob = viewModelScope.launch {
            tripRepository.observeActiveTrip(uid)
                .catch { error ->
                    if (error is CancellationException) throw error
                }
                .collect { trip ->
                    val previousRouteId = _uiState.value.trip?.let { current ->
                        if (current.id == trip?.id) current.routeId else null
                    }
                    _uiState.update { it.copy(trip = trip) }
                    if (trip != null && trip.routeId != previousRouteId) {
                        val name = tripRepository.loadRouteName(trip.routeId)
                        _uiState.update { current ->
                            if (current.trip?.id == trip.id) {
                                current.copy(routeName = name)
                            } else {
                                current
                            }
                        }
                    }
                }
        }
    }

    private suspend fun refreshTokens() {
        while (currentCoroutineContext().isActive) {
            try {
                val sessionId = sessionId()
                val token = tripRepository.issueQrToken(sessionId)
                val remainingSeconds = (
                    (token.expiresAtMillis - System.currentTimeMillis()) / 1_000
                    ).toInt().coerceIn(1, REFRESH_SECONDS)
                _uiState.update {
                    it.copy(
                        token = token.token,
                        expiresAtMillis = token.expiresAtMillis,
                        isRefreshing = false,
                        isOffline = false
                    )
                }
                for (remaining in remainingSeconds downTo 1) {
                    _uiState.update {
                        it.copy(secondsRemaining = remaining)
                    }
                    delay(1_000)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                val stillValid = _uiState.value.expiresAtMillis >
                    System.currentTimeMillis()
                if (!stillValid) {
                    _uiState.update {
                        it.copy(
                            token = null,
                            isRefreshing = false,
                            isOffline = true,
                            secondsRemaining = 0
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(isOffline = true, isRefreshing = false)
                    }
                }
                delay(RETRY_MILLIS)
            }
        }
    }

    private suspend fun sessionId(): String {
        val uid = authRepository.currentUser()?.uid
            ?: throw IllegalStateException("No hay una cuenta activa.")
        return accountRepository.deviceSession
            .first { it?.uid == uid && !it.sessionId.isBlank() }
            ?.sessionId
            ?: accountRepository.ensureSession(uid).sessionId
    }

    companion object {
        private const val REFRESH_SECONDS = 45
        private const val RETRY_MILLIS = 5_000L
    }
}
