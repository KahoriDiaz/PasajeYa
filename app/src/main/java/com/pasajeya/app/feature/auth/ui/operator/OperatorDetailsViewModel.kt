package com.pasajeya.app.feature.auth.ui.operator

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pasajeya.app.feature.auth.domain.AccountRepository
import com.pasajeya.app.feature.auth.domain.AuthRepository
import com.pasajeya.app.feature.auth.domain.model.AccountException
import com.pasajeya.app.feature.auth.domain.model.AccountFailure
import com.pasajeya.app.feature.auth.domain.model.AuthDestination
import com.pasajeya.app.feature.auth.domain.model.RouteOption
import com.pasajeya.app.feature.auth.domain.validation.OperatorConfig
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

sealed interface RoutesUiState {
    data object Loading : RoutesUiState
    data object Empty : RoutesUiState
    data class Failed(val failure: AccountFailure) : RoutesUiState
    data class Ready(val routes: List<RouteOption>) : RoutesUiState
}

data class OperatorDetailsUiState(
    val licenseNumber: String = "",
    val licenseClass: String = "",
    val plate: String = "",
    val selectedRouteIds: Set<String> = emptySet(),
    val routes: RoutesUiState = RoutesUiState.Loading,
    val licenseNumberError: Boolean = false,
    val licenseClassError: Boolean = false,
    val plateError: Boolean = false,
    val routesError: Boolean = false,
    val accountFailure: AccountFailure? = null,
    val isSubmitting: Boolean = false
)

@HiltViewModel
class OperatorDetailsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val accountRepository: AccountRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(OperatorDetailsUiState())
    val uiState: StateFlow<OperatorDetailsUiState> = _uiState.asStateFlow()

    private val _events = Channel<AuthDestination>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        loadRoutes()
    }

    fun onLicenseNumberChange(value: String) {
        _uiState.update {
            it.copy(
                licenseNumber = value.uppercase().take(9),
                licenseNumberError = false,
                accountFailure = null
            )
        }
    }

    fun onLicenseClassChange(value: String) {
        _uiState.update {
            it.copy(
                licenseClass = value,
                licenseClassError = false,
                accountFailure = null
            )
        }
    }

    fun onPlateChange(value: String) {
        _uiState.update {
            it.copy(
                plate = value.uppercase().filter(Char::isLetterOrDigit).take(6),
                plateError = false,
                accountFailure = null
            )
        }
    }

    fun toggleRoute(routeId: String) {
        _uiState.update { current ->
            val routes = (current.routes as? RoutesUiState.Ready)?.routes.orEmpty()
            if (routes.none { it.id == routeId }) {
                current
            } else {
                current.copy(
                    selectedRouteIds = if (routeId in current.selectedRouteIds) {
                        current.selectedRouteIds - routeId
                    } else {
                        current.selectedRouteIds + routeId
                    },
                    routesError = false,
                    accountFailure = null
                )
            }
        }
    }

    fun loadRoutes() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(routes = RoutesUiState.Loading, accountFailure = null)
            }
            try {
                val routes = accountRepository.loadActiveRoutes()
                _uiState.update { current ->
                    val availableIds = routes.mapTo(mutableSetOf()) { it.id }
                    current.copy(
                        routes = if (routes.isEmpty()) {
                            RoutesUiState.Empty
                        } else {
                            RoutesUiState.Ready(routes)
                        },
                        selectedRouteIds = current.selectedRouteIds.intersect(availableIds)
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: AccountException) {
                _uiState.update {
                    it.copy(routes = RoutesUiState.Failed(error.failure))
                }
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(routes = RoutesUiState.Failed(AccountFailure.Unknown))
                }
            }
        }
    }

    fun submit() {
        val current = _uiState.value
        if (current.isSubmitting) return

        val licenseNumber = Validators.normalizeLicenseNumber(current.licenseNumber)
        val plate = Validators.normalizePlate(current.plate)
        val licenseNumberError = !Validators.isValidLicenseNumber(licenseNumber)
        val licenseClassError = !Validators.isValidLicenseClass(current.licenseClass)
        val plateError = !Validators.isValidPlate(plate)
        val readyRoutes = (current.routes as? RoutesUiState.Ready)?.routes.orEmpty()
        val routeIds = readyRoutes
            .filter { it.id in current.selectedRouteIds }
            .map { it.id }
        val routesError = routeIds.isEmpty()

        if (licenseNumberError || licenseClassError || plateError || routesError) {
            _uiState.update {
                it.copy(
                    licenseNumber = licenseNumber,
                    plate = plate,
                    licenseNumberError = licenseNumberError,
                    licenseClassError = licenseClassError,
                    plateError = plateError,
                    routesError = routesError,
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
                it.copy(isSubmitting = true, accountFailure = null)
            }
            try {
                accountRepository.ensureSession(user.uid)
                accountRepository.registerOperator(
                    licenseNumber = licenseNumber,
                    licenseClass = current.licenseClass,
                    plate = plate,
                    routeIds = routeIds
                )
                _uiState.update { it.copy(isSubmitting = false) }
                _events.send(AuthDestination.OPERATOR_HOME)
            } catch (error: CancellationException) {
                throw error
            } catch (error: AccountException) {
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        accountFailure = error.failure
                    )
                }
                if (error.failure == AccountFailure.RouteUnavailable) {
                    loadRoutes()
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

    val licenseClasses: List<String> = OperatorConfig.licenseClasses
}
