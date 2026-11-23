package com.pasajeya.app.core.navigation

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pasajeya.app.feature.auth.domain.AccountRepository
import com.pasajeya.app.feature.auth.domain.AuthRepository
import com.pasajeya.app.feature.auth.domain.DeviceSessionRepository
import com.pasajeya.app.feature.auth.domain.model.AuthDestination
import com.pasajeya.app.feature.auth.domain.model.resolveAuthDestination
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

sealed interface AppUiState {
    data object Loading : AppUiState
    data class Ready(val destination: AuthDestination) : AppUiState
    data object StartupError : AppUiState
}

@HiltViewModel
class AppViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authRepository: AuthRepository,
    private val accountRepository: AccountRepository,
    private val deviceSessionRepository: DeviceSessionRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow<AppUiState>(AppUiState.Loading)
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    private val _sessionExpired = MutableStateFlow(false)
    val sessionExpired: StateFlow<Boolean> = _sessionExpired.asStateFlow()

    private val localDeviceId = deviceSessionRepository.deviceId

    init {
        bootstrap()
        observeSessionChanges()
        observeDeviceSessionChanges()
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
                // Evita el "cargando..." infinito si la nube no responde.
                val session = withTimeoutOrNull(STARTUP_TIMEOUT_MILLIS) {
                    accountRepository.ensureSession(user.uid)
                }
                if (session == null) {
                    _uiState.value = AppUiState.StartupError
                    return@launch
                }
                _uiState.value = AppUiState.Ready(resolveAuthDestination(session))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.value = AppUiState.StartupError
            }
        }
    }

    /** Sesion solo local: al cerrar sesion en Firebase Auth se descarta la sesion del telefono. */
    private fun observeSessionChanges() {
        viewModelScope.launch {
            authRepository.authState.collect { user ->
                if (user == null) accountRepository.clearSession()
            }
        }
    }

    /** Detecta en tiempo real si otra sesión inició en otro dispositivo y fuerza el cierre. */
    private fun observeDeviceSessionChanges() {
        viewModelScope.launch {
            try {
                authRepository.authState.collect { user ->
                    if (user != null) {
                        deviceSessionRepository.observeActiveDeviceId(user.uid).collect { remoteDeviceId ->
                            if (remoteDeviceId != null && remoteDeviceId != localDeviceId) {
                                withContext(Dispatchers.Main) {
                                    try {
                                        authRepository.signOut()
                                    } catch (_: Exception) {
                                        // Ignorar errores de signOut; ya se notifica el cierre.
                                    }
                                    accountRepository.clearSession()
                                    _sessionExpired.value = true
                                }
                            }
                        }
                    }
                }
            } catch (error: Exception) {
                Log.e("AUTH_SESSION", "Error en observeDeviceSessionChanges", error)
            }
        }
    }

    private companion object {
        const val STARTUP_TIMEOUT_MILLIS = 20_000L
    }
}
