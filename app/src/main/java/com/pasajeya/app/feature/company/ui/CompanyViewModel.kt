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
    val hasError: Boolean = false
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
