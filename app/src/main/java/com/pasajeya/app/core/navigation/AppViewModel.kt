package com.pasajeya.app.core.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pasajeya.app.feature.auth.domain.AccountRepository
import com.pasajeya.app.feature.auth.domain.AuthRepository
import com.pasajeya.app.feature.auth.domain.model.AuthDestination
import com.pasajeya.app.feature.auth.domain.model.DeviceSession
import com.pasajeya.app.feature.auth.domain.model.resolveAuthDestination
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

sealed interface AppUiState {
    data object Loading : AppUiState
    data class Ready(val destination: AuthDestination) : AppUiState
    data object StartupError : AppUiState
}

@HiltViewModel
class AppViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val accountRepository: AccountRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow<AppUiState>(AppUiState.Loading)
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    private val _sessionExpired = MutableStateFlow(false)
    val sessionExpired: StateFlow<Boolean> = _sessionExpired.asStateFlow()

    private var sessionMonitorJob: Job? = null
    private var monitoredSessionId: String? = null

    init {
        bootstrap()
        observeSessionChanges()
    }

    fun retry() {
        bootstrap()
    }

    fun acknowledgeSessionExpired() {
        _sessionExpired.value = false
    }

    private fun bootstrap() {
        viewModelScope.launch {
            _uiState.value = AppUiState.Loading
            val user = authRepository.authState.first()
            if (user == null) {
                _uiState.value = AppUiState.Ready(AuthDestination.WELCOME)
                return@launch
            }

            try {
                val session = accountRepository.ensureSession(user.uid)
                _uiState.value = AppUiState.Ready(
                    resolveAuthDestination(
                        emailVerified = user.emailVerified,
                        session = session
                    )
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.value = AppUiState.StartupError
            }
        }
    }

    private fun observeSessionChanges() {
        viewModelScope.launch {
            authRepository.authState
                .combine(accountRepository.deviceSession) { user, session ->
                    user to session
                }
                .collect { (user, session) ->
                    if (user == null) {
                        stopSessionMonitor()
                        accountRepository.clearSession()
                    } else if (session != null && session.uid == user.uid) {
                        startSessionMonitor(user.uid, session)
                    }
                }
        }
    }

    private fun startSessionMonitor(uid: String, session: DeviceSession) {
        if (monitoredSessionId == session.sessionId) return

        monitoredSessionId = session.sessionId
        sessionMonitorJob?.cancel()
        sessionMonitorJob = viewModelScope.launch {
            accountRepository.observeRemoteSession(uid)
                .catch { error ->
                    if (error is CancellationException) throw error
                }
                .collect { remoteSession ->
                    val remoteId = remoteSession.sessionId
                    if (
                        !remoteSession.fromCache &&
                        !remoteId.isNullOrBlank() &&
                        remoteId != session.sessionId
                    ) {
                        expireSession()
                    }
                }
        }
    }

    private fun stopSessionMonitor() {
        sessionMonitorJob?.cancel()
        sessionMonitorJob = null
        monitoredSessionId = null
    }

    private fun expireSession() {
        if (_sessionExpired.value) return

        _sessionExpired.value = true
        stopSessionMonitor()
        accountRepository.clearSession()
        viewModelScope.launch {
            runCatching { authRepository.signOut() }
        }
    }
}
