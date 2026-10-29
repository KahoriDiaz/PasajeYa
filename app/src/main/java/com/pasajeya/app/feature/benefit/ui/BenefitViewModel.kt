package com.pasajeya.app.feature.benefit.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pasajeya.app.feature.benefit.domain.Benefit
import com.pasajeya.app.feature.benefit.domain.BenefitRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface BenefitUiState {
    object Idle : BenefitUiState
    object Loading : BenefitUiState
    data class CurrentBenefit(val benefit: Benefit?) : BenefitUiState
    data class Success(val message: String) : BenefitUiState
    data class Error(val message: String) : BenefitUiState
}

@HiltViewModel
class BenefitViewModel @Inject constructor(
    private val benefitRepository: BenefitRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<BenefitUiState>(BenefitUiState.Idle)
    val uiState: StateFlow<BenefitUiState> = _uiState.asStateFlow()

    fun submitBenefitRequest(uid: String, university: String, studentCode: String) {
        if (university.isBlank() || studentCode.isBlank()) {
            _uiState.value = BenefitUiState.Error("Universidad y código no pueden estar vacíos")
            return
        }

        _uiState.value = BenefitUiState.Loading
        viewModelScope.launch {
            val result = benefitRepository.requestBenefit(uid, university.trim(), studentCode.trim())
            _uiState.value = if (result.isSuccess) {
                BenefitUiState.Success("Solicitud de beneficio enviada con éxito")
            } else {
                BenefitUiState.Error(result.exceptionOrNull()?.message ?: "Error al enviar la solicitud")
            }
        }
    }

    fun resetState() {
        _uiState.value = BenefitUiState.Idle
    }
}