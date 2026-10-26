package com.pasajeya.app.feature.auth.ui.register

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pasajeya.app.feature.auth.domain.AccountRepository
import com.pasajeya.app.feature.auth.domain.AuthRepository
import com.pasajeya.app.feature.auth.domain.CommonPasswordsProvider
import com.pasajeya.app.feature.auth.domain.model.AccountException
import com.pasajeya.app.feature.auth.domain.model.AccountFailure
import com.pasajeya.app.feature.auth.domain.model.AuthDestination
import com.pasajeya.app.feature.auth.domain.model.AuthException
import com.pasajeya.app.feature.auth.domain.model.AuthFailure
import com.pasajeya.app.feature.auth.domain.validation.PasswordValidation
import com.pasajeya.app.feature.auth.domain.validation.Validators
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RegisterUiState(
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val passwordVisible: Boolean = false,
    val confirmPasswordVisible: Boolean = false,
    val validation: PasswordValidation? = null,
    val isLoadingPasswords: Boolean = true,
    val passwordsUnavailable: Boolean = false,
    val emailError: Boolean = false,
    val passwordError: Boolean = false,
    val confirmPasswordError: Boolean = false,
    val authFailure: AuthFailure? = null,
    val accountFailure: AccountFailure? = null,
    val isSubmitting: Boolean = false
)

@HiltViewModel
class RegisterViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val accountRepository: AccountRepository,
    private val commonPasswordsProvider: CommonPasswordsProvider
) : ViewModel() {
    private val _uiState = MutableStateFlow(RegisterUiState())
    val uiState: StateFlow<RegisterUiState> = _uiState.asStateFlow()

    private val _events = Channel<AuthDestination>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var commonPasswords: Set<String>? = null

    init {
        loadCommonPasswords()
    }

    fun onEmailChange(value: String) {
        _uiState.update {
            it.copy(
                email = value,
                emailError = false,
                authFailure = null,
                accountFailure = null,
                validation = computeValidation(value, it.password)
            )
        }
    }

    fun onPasswordChange(value: String) {
        _uiState.update {
            it.copy(
                password = value,
                passwordError = false,
                authFailure = null,
                accountFailure = null,
                validation = computeValidation(it.email, value)
            )
        }
    }

    fun onConfirmPasswordChange(value: String) {
        _uiState.update {
            it.copy(
                confirmPassword = value,
                confirmPasswordError = false
            )
        }
    }

    fun togglePasswordVisibility() {
        _uiState.update { it.copy(passwordVisible = !it.passwordVisible) }
    }

    fun toggleConfirmPasswordVisibility() {
        _uiState.update {
            it.copy(confirmPasswordVisible = !it.confirmPasswordVisible)
        }
    }

    fun retryCommonPasswords() {
        if (!_uiState.value.isLoadingPasswords) {
            loadCommonPasswords()
        }
    }

    fun submit() {
        val current = _uiState.value
        if (current.isSubmitting) return

        val passwords = commonPasswords
        if (passwords == null) {
            _uiState.update {
                it.copy(passwordsUnavailable = !current.isLoadingPasswords)
            }
            return
        }

        val email = current.email.trim()
        val validation = Validators.validatePassword(
            value = current.password,
            email = email,
            commonPasswords = passwords
        )
        val emailError = !Validators.isValidEmail(email)
        val passwordError = !validation.valid
        val confirmError = current.confirmPassword != current.password
        if (emailError || passwordError || confirmError) {
            _uiState.update {
                it.copy(
                    email = email,
                    emailError = emailError,
                    passwordError = passwordError,
                    confirmPasswordError = confirmError,
                    validation = validation,
                    authFailure = null,
                    accountFailure = null
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    email = email,
                    isSubmitting = true,
                    authFailure = null,
                    accountFailure = null
                )
            }

            try {
                val user = authRepository.register(email, current.password)
                accountRepository.ensureSession(user.uid)
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        password = "",
                        confirmPassword = ""
                    )
                }
                _events.send(AuthDestination.VERIFY_EMAIL)
            } catch (error: CancellationException) {
                throw error
            } catch (error: AuthException) {
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        authFailure = error.failure
                    )
                }
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

    private fun loadCommonPasswords() {
        _uiState.update {
            it.copy(isLoadingPasswords = true, passwordsUnavailable = false)
        }
        viewModelScope.launch {
            try {
                val passwords = commonPasswordsProvider.get()
                commonPasswords = passwords
                _uiState.update { current ->
                    current.copy(
                        isLoadingPasswords = false,
                        passwordsUnavailable = false,
                        validation = Validators.validatePassword(
                            value = current.password,
                            email = current.email,
                            commonPasswords = passwords
                        )
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                commonPasswords = null
                _uiState.update {
                    it.copy(
                        isLoadingPasswords = false,
                        passwordsUnavailable = true,
                        validation = null
                    )
                }
            }
        }
    }

    private fun computeValidation(
        email: String,
        password: String
    ): PasswordValidation? {
        val passwords = commonPasswords ?: return null
        return Validators.validatePassword(
            value = password,
            email = email,
            commonPasswords = passwords
        )
    }
}
