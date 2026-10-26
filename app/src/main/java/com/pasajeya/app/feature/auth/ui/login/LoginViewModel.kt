package com.pasajeya.app.feature.auth.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pasajeya.app.feature.auth.domain.AccountRepository
import com.pasajeya.app.feature.auth.domain.AuthRepository
import com.pasajeya.app.feature.auth.domain.LoginAttemptLimiter
import com.pasajeya.app.feature.auth.domain.model.AccountException
import com.pasajeya.app.feature.auth.domain.model.AccountFailure
import com.pasajeya.app.feature.auth.domain.model.AuthDestination
import com.pasajeya.app.feature.auth.domain.model.AuthException
import com.pasajeya.app.feature.auth.domain.model.AuthFailure
import com.pasajeya.app.feature.auth.domain.model.resolveAuthDestination
import com.pasajeya.app.feature.auth.domain.validation.Validators
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

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val passwordVisible: Boolean = false,
    val emailError: Boolean = false,
    val passwordError: Boolean = false,
    val authFailure: AuthFailure? = null,
    val accountFailure: AccountFailure? = null,
    val lockedSeconds: Int = 0,
    val isSubmitting: Boolean = false
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val accountRepository: AccountRepository
) : ViewModel() {
    private val limiter = LoginAttemptLimiter()
    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private val _events = Channel<AuthDestination>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var lockTicker: Job? = null

    fun onEmailChange(value: String) {
        _uiState.update {
            it.copy(
                email = value,
                emailError = false,
                authFailure = null,
                accountFailure = null
            )
        }
    }

    fun onPasswordChange(value: String) {
        _uiState.update {
            it.copy(
                password = value,
                passwordError = false,
                authFailure = null,
                accountFailure = null
            )
        }
    }

    fun togglePasswordVisibility() {
        _uiState.update { it.copy(passwordVisible = !it.passwordVisible) }
    }

    fun submit() {
        val current = _uiState.value
        if (current.isSubmitting) return

        if (limiter.isLocked()) {
            startLockTicker()
            return
        }

        val email = current.email.trim()
        val emailError = !Validators.isValidEmail(email)
        val passwordError = current.password.isBlank()
        if (emailError || passwordError) {
            _uiState.update {
                it.copy(
                    emailError = emailError,
                    passwordError = passwordError,
                    authFailure = null,
                    accountFailure = null
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isSubmitting = true,
                    email = email,
                    authFailure = null,
                    accountFailure = null
                )
            }

            try {
                val user = authRepository.signIn(email, current.password)
                val session = accountRepository.ensureSession(user.uid)
                limiter.reset()
                _events.send(
                    resolveAuthDestination(
                        emailVerified = user.emailVerified,
                        session = session
                    )
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: AuthException) {
                handleAuthFailure(error.failure)
            } catch (error: AccountException) {
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        accountFailure = error.failure
                    )
                }
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        accountFailure = AccountFailure.Unknown
                    )
                }
            }
        }
    }

    private fun handleAuthFailure(failure: AuthFailure) {
        if (failure == AuthFailure.InvalidCredentials) {
            val remainingSeconds = limiter.recordFailure()
            _uiState.update {
                it.copy(
                    isSubmitting = false,
                    authFailure = if (remainingSeconds > 0) null else failure,
                    lockedSeconds = remainingSeconds
                )
            }
            if (remainingSeconds > 0) startLockTicker()
        } else {
            _uiState.update {
                it.copy(
                    isSubmitting = false,
                    authFailure = failure
                )
            }
        }
    }

    private fun startLockTicker() {
        lockTicker?.cancel()
        lockTicker = viewModelScope.launch {
            while (true) {
                val remainingSeconds = limiter.remainingSeconds()
                _uiState.update { it.copy(lockedSeconds = remainingSeconds) }
                if (remainingSeconds <= 0) break
                delay(1_000L)
            }
        }
    }
}
