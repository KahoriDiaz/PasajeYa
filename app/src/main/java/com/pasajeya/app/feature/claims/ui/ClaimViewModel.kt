package com.pasajeya.app.feature.claims.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pasajeya.app.feature.claims.data.ClaimRepository
import com.pasajeya.app.feature.claims.domain.Claim
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.isActive

@HiltViewModel
class ClaimViewModel @Inject constructor(
    private val claimRepository: ClaimRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ClaimUiState.Idle)
    val uiState: StateFlow<ClaimUiState> = _uiState.asStateFlow()

    enum class ClaimUiState {
        Idle,
        Loading,
        Success(Claim),
        Error(String)
    }

    fun submitClaim(claim: Claim) {
        _uiState.update { it.copy(ClaimUiState.Loading) }
        viewModelScope.launch {
            claimRepository.submitClaim(claim)
            _uiState.update { it.copy(ClaimUiState.Success(claim)) }
        }
    }

    fun observeClaimByTripId(tripId: String) {
        viewModelScope.launch {
            val claims = claimRepository.observeClaimByTripId(tripId)
            // TODO: collect and update uiState
        }
    }

    fun observePassengerClaims(passengerId: String) {
        viewModelScope.launch {
            val claims = claimRepository.observePassengerClaims(passengerId)
            // TODO: collect and update uiState
        }
    }
}