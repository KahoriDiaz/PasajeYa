package com.pasajeya.app.feature.company.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pasajeya.app.feature.auth.domain.AuthRepository
import com.pasajeya.app.feature.company.domain.CompanyDashboard
import com.pasajeya.app.feature.company.domain.CompanyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CompanyUiState(
    val dashboard: CompanyDashboard? = null,
    val isLoading: Boolean = true,
    val hasError: Boolean = false,
    // Alta de un conductor de la empresa.
    val driverName: String = "",
    val driverDni: String = "",
    val driverPlate: String = "",
    val isRegisteringDriver: Boolean = false,
    val driverRegistered: Boolean = false,
    val driverError: Boolean = false
)

@HiltViewModel
class CompanyViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val companyRepository: CompanyRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CompanyUiState())
    val uiState: StateFlow<CompanyUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun onDriverNameChange(value: String) {
        _uiState.update { it.copy(driverName = value.take(60), driverRegistered = false) }
    }

    fun onDriverDniChange(value: String) {
        _uiState.update {
            it.copy(
                driverDni = value.filter(Char::isDigit).take(8),
                driverRegistered = false
            )
        }
    }

    fun onDriverPlateChange(value: String) {
        _uiState.update {
            it.copy(
                driverPlate = value.uppercase().filter(Char::isLetterOrDigit).take(6),
                driverRegistered = false
            )
        }
    }

    /** Da de alta a un conductor por DNI: al registrarse en la app queda vinculado. */
    fun registerDriver() {
        val state = _uiState.value
        val uid = authRepository.currentUser()?.uid ?: return
        if (state.isRegisteringDriver) return
        if (state.driverName.isBlank() || state.driverDni.length != 8) {
            _uiState.update { it.copy(driverError = true) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isRegisteringDriver = true, driverError = false) }
            try {
                companyRepository.registerDriver(
                    companyUid = uid,
                    fullName = state.driverName.trim(),
                    dni = state.driverDni,
                    plate = state.driverPlate,
                    routeId = ""
                )
                _uiState.update {
                    it.copy(
                        isRegisteringDriver = false,
                        driverRegistered = true,
                        driverName = "",
                        driverDni = "",
                        driverPlate = ""
                    )
                }
                refresh()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(isRegisteringDriver = false, driverError = true)
                }
            }
        }
    }

    fun refresh() {
        val uid = authRepository.currentUser()?.uid
        if (uid == null) {
            _uiState.update { it.copy(isLoading = false, hasError = true) }
            return
        }
        _uiState.update { it.copy(isLoading = it.dashboard == null, hasError = false) }
        viewModelScope.launch {
            try {
                val dashboard = companyRepository.loadDashboard(uid)
                _uiState.update { CompanyUiState(dashboard = dashboard, isLoading = false) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.update { it.copy(isLoading = false, hasError = true) }
            }
        }
    }
}
