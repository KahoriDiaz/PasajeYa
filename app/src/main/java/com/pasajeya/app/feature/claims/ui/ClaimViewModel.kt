package com.pasajeya.app.feature.claims.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pasajeya.app.feature.claims.domain.Claim
import com.pasajeya.app.feature.claims.domain.ClaimRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ClaimUiState {
    object Idle : ClaimUiState
    object Submitting : ClaimUiState
    data class Success(val message: String = "Reclamo enviado con éxito") : ClaimUiState
    data class Error(val message: String) : ClaimUiState
}

@HiltViewModel
class ClaimViewModel @Inject constructor(
    private val claimRepository: ClaimRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<ClaimUiState>(ClaimUiState.Idle)
    val uiState: StateFlow<ClaimUiState> = _uiState.asStateFlow()

    fun submitClaim(
        tripId: String,
        passengerId: String,
        operatorId: String?,
        reason: String,
        proposedDistanceMeters: Double?
    ) {
        if (reason.isBlank()) {
            _uiState.value = ClaimUiState.Error("El motivo no puede estar vacío")
            return
        }

        _uiState.value = ClaimUiState.Submitting
        viewModelScope.launch {
            val claim = Claim(
                tripId = tripId,
                passengerId = passengerId,
                operatorId = operatorId,
                reason = reason.trim(),
                proposedDistanceMeters = proposedDistanceMeters,
                status = "submitted",
                refundAmountCents = 0,
                resolutionNotes = null,
                createdAt = System.currentTimeMillis()
            )
            val result = claimRepository.submitClaim(claim)
            _uiState.value = if (result.isSuccess) {
                ClaimUiState.Success()
            } else {
                ClaimUiState.Error(result.exceptionOrNull()?.message ?: "Error al enviar el reclamo")
            }
        }
    }

    fun resetState() {
        _uiState.value = ClaimUiState.Idle
    }
}