package com.pasajeya.app.feature.auth.ui.verification

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pasajeya.app.feature.auth.domain.AccountRepository
import com.pasajeya.app.feature.auth.domain.AuthRepository
import com.pasajeya.app.feature.auth.domain.model.AccountException
import com.pasajeya.app.feature.auth.domain.model.AccountFailure
import com.pasajeya.app.feature.auth.domain.model.AuthDestination
import com.pasajeya.app.feature.auth.domain.model.AuthException
import com.pasajeya.app.feature.auth.domain.model.AuthFailure
import com.pasajeya.app.feature.auth.domain.model.AuthUser
import com.pasajeya.app.feature.auth.domain.model.resolveAuthDestination
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class VerificationInfo {
    SENT,
    NOT_VERIFIED
}

enum class VerificationError {
    SEND,
    NETWORK,
    TOO_MANY,
    SESSION,
    GENERIC
}

data class VerificationUiState(
    val email: String = "",
    val hasUser: Boolean = true,
    val isLoadingUser: Boolean = true,
    val isChecking: Boolean = false,
    val isSending: Boolean = false,
    val info: VerificationInfo? = null,
    val error: VerificationError? = null,
    val accountFailure: AccountFailure? = null,
    val cooldownSeconds: Int = 0
)

@HiltViewModel
class VerificationViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val accountRepository: AccountRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(VerificationUiState())
    val uiState: StateFlow<VerificationUiState> = _uiState.asStateFlow()

    private val _events = Channel<AuthDestination>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var cooldownJob: Job? = null

    init {
        val user = authRepository.currentUser()
        if (user == null) {
            _uiState.update {
                it.copy(hasUser = false, isLoadingUser = false)
            }
        } else {
            _uiState.update {
                it.copy(
                    email = user.email,
                    hasUser = true,
                    isLoadingUser = false
                )
            }
            if (user.emailVerified) {
                completeVerification(user)
            } else {
                sendVerification()
            }
        }
    }

    fun resend() {
        if (_uiState.value.cooldownSeconds > 0) return
        sendVerification()
    }

    fun checkVerification() {
        val current = _uiState.value
        if (current.isChecking || !current.hasUser) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isChecking = true,
                    info = null,
                    error = null,
                    accountFailure = null
                )
            }
            try {
                val user = authRepository.reloadUser()
                if (user.emailVerified) {
                    completeVerification(user)
                } else {
                    _uiState.update {
                        it.copy(
                            isChecking = false,
                            info = VerificationInfo.NOT_VERIFIED
                        )
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: AuthException) {
                _uiState.update {
                    it.copy(
                        isChecking = false,
                        error = error.failure.toVerificationError()
                    )
                }
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(isChecking = false, error = VerificationError.GENERIC)
                }
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            accountRepository.clearSession()
            runCatching { authRepository.signOut() }
            _events.send(AuthDestination.WELCOME)
        }
    }

    private fun sendVerification() {
        val current = _uiState.value
        if (current.isSending || current.cooldownSeconds > 0 || !current.hasUser) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isSending = true,
                    error = null,
                    accountFailure = null
                )
            }
            try {
                authRepository.sendEmailVerification()
                _uiState.update {
                    it.copy(
                        isSending = false,
                        info = VerificationInfo.SENT,
                        cooldownSeconds = RESEND_SECONDS
                    )
                }
                startCooldownTicker()
            } catch (error: CancellationException) {
                throw error
            } catch (error: AuthException) {
                _uiState.update {
                    it.copy(
                        isSending = false,
                        error = error.failure.toVerificationError()
                    )
                }
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(isSending = false, error = VerificationError.SEND)
                }
            }
        }
    }

    private fun completeVerification(user: AuthUser) {
        viewModelScope.launch {
            _uiState.update { it.copy(isChecking = true) }
            try {
                val session = accountRepository.ensureSession(user.uid)
                _events.send(
                    resolveAuthDestination(
                        emailVerified = true,
                        session = session
                    )
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: AccountException) {
                _uiState.update {
                    it.copy(
                        isChecking = false,
                        accountFailure = error.failure
                    )
                }
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(
                        isChecking = false,
                        accountFailure = AccountFailure.Unknown
                    )
                }
            }
        }
    }

    private fun startCooldownTicker() {
        cooldownJob?.cancel()
        cooldownJob = viewModelScope.launch {
            while (_uiState.value.cooldownSeconds > 0) {
                delay(1_000L)
                _uiState.update {
                    it.copy(cooldownSeconds = (it.cooldownSeconds - 1).coerceAtLeast(0))
                }
            }
        }
    }

    private fun AuthFailure.toVerificationError(): VerificationError = when (this) {
        AuthFailure.Network -> VerificationError.NETWORK
        AuthFailure.TooManyRequests -> VerificationError.TOO_MANY
        AuthFailure.NotSignedIn -> VerificationError.SESSION
        else -> VerificationError.GENERIC
    }

    private companion object {
        const val RESEND_SECONDS = 60
    }
}
