package com.pasajeya.app.feature.auth.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pasajeya.app.feature.auth.domain.AccountRepository
import com.pasajeya.app.feature.auth.domain.AuthRepository
import com.pasajeya.app.feature.auth.domain.PrivacyConsent
import com.pasajeya.app.feature.auth.domain.model.AccountException
import com.pasajeya.app.feature.auth.domain.model.AccountFailure
import com.pasajeya.app.feature.auth.domain.model.AuthDestination
import com.pasajeya.app.feature.auth.domain.model.UserRole
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

data class PersonalProfileUiState(
    val firstName: String = "",
    val lastName: String = "",
    val dni: String = "",
    val birthDate: String = "",
    val consentAccepted: Boolean = false,
    val firstNameError: Boolean = false,
    val lastNameError: Boolean = false,
    val dniError: Boolean = false,
    val birthDateError: Boolean = false,
    val consentError: Boolean = false,
    val accountFailure: AccountFailure? = null,
    val isSubmitting: Boolean = false
)

abstract class PersonalProfileViewModel(
    private val authRepository: AuthRepository,
    private val accountRepository: AccountRepository,
    private val role: UserRole,
    private val nextDestination: AuthDestination
) : ViewModel() {
    private val _uiState = MutableStateFlow(PersonalProfileUiState())
    val uiState: StateFlow<PersonalProfileUiState> = _uiState.asStateFlow()

    private val _events = Channel<AuthDestination>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun onFirstNameChange(value: String) {
        _uiState.update {
            it.copy(
                firstName = value,
                firstNameError = false,
                accountFailure = null
            )
        }
    }

    fun onLastNameChange(value: String) {
        _uiState.update {
            it.copy(
                lastName = value,
                lastNameError = false,
                accountFailure = null
            )
        }
    }

    fun onDniChange(value: String) {
        _uiState.update {
            it.copy(
                dni = value.filter(Char::isDigit).take(8),
                dniError = false,
                accountFailure = null
            )
        }
    }

    fun onBirthDateChange(value: String) {
        val digits = value.filter(Char::isDigit).take(8)
        val formatted = buildString {
            append(digits.take(4))
            if (digits.length > 4) {
                append('-').append(digits.substring(4).take(2))
            }
            if (digits.length > 6) {
                append('-').append(digits.substring(6).take(2))
            }
        }
        _uiState.update {
            it.copy(
                birthDate = formatted,
                birthDateError = false,
                accountFailure = null
            )
        }
    }

    fun onConsentChange(accepted: Boolean) {
        _uiState.update {
            it.copy(
                consentAccepted = accepted,
                consentError = false,
                accountFailure = null
            )
        }
    }

    fun submit() {
        val current = _uiState.value
        if (current.isSubmitting) return

        val firstName = current.firstName.trim()
        val lastName = current.lastName.trim()
        val birthDate = current.birthDate.trim()
        val firstNameError = !Validators.isValidPersonName(firstName)
        val lastNameError = !Validators.isValidPersonName(lastName)
        val dniError = !Validators.isValidDni(current.dni)
        val birthDateError = !Validators.isValidBirthDate(birthDate)
        val consentError = !current.consentAccepted

        if (firstNameError || lastNameError || dniError || birthDateError || consentError) {
            _uiState.update {
                it.copy(
                    firstName = firstName,
                    lastName = lastName,
                    birthDate = birthDate,
                    firstNameError = firstNameError,
                    lastNameError = lastNameError,
                    dniError = dniError,
                    birthDateError = birthDateError,
                    consentError = consentError,
                    accountFailure = null
                )
            }
            return
        }

        val user = authRepository.currentUser()
        if (user == null) {
            _uiState.update {
                it.copy(accountFailure = AccountFailure.NotAuthenticated)
            }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isSubmitting = true,
                    accountFailure = null
                )
            }
            try {
                accountRepository.ensureSession(user.uid)
                accountRepository.completeProfile(
                    role = role,
                    firstName = firstName,
                    lastName = lastName,
                    dni = current.dni,
                    birthDate = birthDate,
                    consentVersion = PrivacyConsent.CURRENT_VERSION
                )
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        dni = ""
                    )
                }
                _events.send(nextDestination)
            } catch (error: CancellationException) {
                throw error
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
}

@HiltViewModel
class PassengerProfileViewModel @Inject constructor(
    authRepository: AuthRepository,
    accountRepository: AccountRepository
) : PersonalProfileViewModel(
    authRepository = authRepository,
    accountRepository = accountRepository,
    role = UserRole.PASSENGER,
    nextDestination = AuthDestination.PASSENGER_HOME
)

@HiltViewModel
class OperatorPersonalProfileViewModel @Inject constructor(
    authRepository: AuthRepository,
    accountRepository: AccountRepository
) : PersonalProfileViewModel(
    authRepository = authRepository,
    accountRepository = accountRepository,
    role = UserRole.OPERATOR,
    nextDestination = AuthDestination.OPERATOR_PROFILE
)
