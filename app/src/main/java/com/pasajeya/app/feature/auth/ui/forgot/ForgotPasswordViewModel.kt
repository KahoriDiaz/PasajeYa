package com.pasajeya.app.feature.auth.ui.forgot

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pasajeya.app.feature.auth.domain.AuthRepository
import com.pasajeya.app.feature.auth.domain.model.AuthException
import com.pasajeya.app.feature.auth.domain.model.AuthFailure
import com.pasajeya.app.feature.auth.domain.validation.Validators
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ForgotPasswordUiState(
    val email: String = "",
    val emailError: Boolean = false,
    val failure: AuthFailure? = null,
    val isSubmitting: Boolean = false,
    val sent: Boolean = false
)

@HiltViewModel
class ForgotPasswordViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(ForgotPasswordUiState())
    val uiState: StateFlow<ForgotPasswordUiState> = _uiState.asStateFlow()

    fun onEmailChange(value: String) {
        _uiState.update {
            it.copy(email = value, emailError = false, failure = null)
        }
    }

    fun submit() {
        val current = _uiState.value
        if (current.isSubmitting) return

        val email = current.email.trim()
        if (!Validators.isValidEmail(email)) {
            _uiState.update { it.copy(emailError = true, failure = null) }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(email = email, isSubmitting = true, failure = null)
            }
            try {
                authRepository.sendPasswordReset(email)
                _uiState.update {
                    it.copy(isSubmitting = false, sent = true)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: AuthException) {
                when (error.failure) {
                    AuthFailure.Network -> _uiState.update {
                        it.copy(isSubmitting = false, failure = error.failure)
                    }
                    AuthFailure.TooManyRequests -> _uiState.update {
                        it.copy(isSubmitting = false, failure = error.failure)
                    }
                    else -> _uiState.update {
                        it.copy(isSubmitting = false, sent = true)
                    }
                }
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(isSubmitting = false, failure = AuthFailure.Unknown)
                }
            }
        }
    }
}
