package com.pasajeya.app.feature.auth.ui.company

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pasajeya.app.feature.auth.domain.AccountRepository
import com.pasajeya.app.feature.auth.domain.AuthRepository
import com.pasajeya.app.feature.auth.domain.model.AccountException
import com.pasajeya.app.feature.auth.domain.model.AccountFailure
import com.pasajeya.app.feature.auth.domain.model.AuthDestination
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

data class CompanyDetailsUiState(
    val businessName: String = "",
    val ruc: String = "",
    val businessNameError: Boolean = false,
    val rucError: Boolean = false,
    val isSubmitting: Boolean = false,
    val accountFailure: AccountFailure? = null
)

/** Datos de la empresa para quien eligio el rol de operador y aun no los dio. */
@HiltViewModel
class CompanyDetailsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val accountRepository: AccountRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CompanyDetailsUiState())
    val uiState: StateFlow<CompanyDetailsUiState> = _uiState.asStateFlow()

    private val _events = Channel<AuthDestination>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun onBusinessNameChange(value: String) {
        _uiState.update {
            it.copy(businessName = value.take(80), businessNameError = false, accountFailure = null)
        }
    }

    fun onRucChange(value: String) {
        _uiState.update {
            it.copy(
                ruc = value.filter(Char::isDigit).take(11),
                rucError = false,
                accountFailure = null
            )
        }
    }

    fun submit() {
        val current = _uiState.value
        if (current.isSubmitting) return

        val businessName = current.businessName.trim()
        val ruc = current.ruc.trim()
        val businessNameError = businessName.length < 3
        val rucError = !Validators.isValidRuc(ruc)
        if (businessNameError || rucError) {
            _uiState.update {
                it.copy(businessNameError = businessNameError, rucError = rucError)
            }
            return
        }

        val user = authRepository.currentUser()
        if (user == null) {
            _uiState.update { it.copy(accountFailure = AccountFailure.NotAuthenticated) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, accountFailure = null) }
            try {
                accountRepository.ensureSession(user.uid)
                accountRepository.registerCompany(businessName, ruc)
                _uiState.update { it.copy(isSubmitting = false) }
                _events.send(AuthDestination.COMPANY_HOME)
            } catch (error: CancellationException) {
                throw error
            } catch (error: AccountException) {
                _uiState.update {
                    it.copy(isSubmitting = false, accountFailure = error.failure)
                }
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(isSubmitting = false, accountFailure = AccountFailure.Unknown)
                }
            }
        }
    }
}
