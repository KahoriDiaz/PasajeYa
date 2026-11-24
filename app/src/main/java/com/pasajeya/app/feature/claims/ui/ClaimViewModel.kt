package com.pasajeya.app.feature.claims.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pasajeya.app.feature.auth.domain.AuthRepository
import com.pasajeya.app.feature.claims.domain.Claim
import com.pasajeya.app.feature.claims.domain.ClaimCategory
import com.pasajeya.app.feature.claims.domain.ClaimRepository
import com.pasajeya.app.feature.trip.domain.TripRepository
import com.pasajeya.app.feature.trip.domain.model.Trip
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ClaimUiState(
    val tripId: String = "",
    val trip: Trip? = null,
    val category: ClaimCategory? = null,
    val details: String = "",
    val isSubmitting: Boolean = false,
    val sent: Boolean = false,
    val alreadySent: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class ClaimViewModel @Inject constructor(
    private val claimRepository: ClaimRepository,
    private val tripRepository: TripRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ClaimUiState())
    val uiState: StateFlow<ClaimUiState> = _uiState.asStateFlow()

    fun start(tripId: String) {
        if (_uiState.value.tripId == tripId) return
        _uiState.update { it.copy(tripId = tripId) }

        viewModelScope.launch {
            tripRepository.observeTrip(tripId)
                .catch { _uiState.update { state -> state.copy(trip = null) } }
                .collect { trip -> _uiState.update { state -> state.copy(trip = trip) } }
        }
        viewModelScope.launch {
            claimRepository.observeClaimByTripId(tripId)
                .catch { }
                .collect { claim ->
                    _uiState.update { state -> state.copy(alreadySent = claim != null) }
                }
        }
    }

    fun onCategoryChange(category: ClaimCategory) {
        _uiState.update { it.copy(category = category, error = null) }
    }

    fun onDetailsChange(value: String) {
        _uiState.update { it.copy(details = value.take(400), error = null) }
    }

    fun submit(missingCategoryMessage: String, genericError: String) {
        val state = _uiState.value
        if (state.isSubmitting) return
        val category = state.category
        if (category == null) {
            _uiState.update { it.copy(error = missingCategoryMessage) }
            return
        }
        val uid = authRepository.currentUser()?.uid
        if (uid == null) {
            _uiState.update { it.copy(error = genericError) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, error = null) }
            val trip = state.trip
            val claim = Claim(
                tripId = state.tripId,
                passengerId = uid,
                passengerName = trip?.passengerFirstName,
                operatorId = trip?.operatorUid,
                routeId = trip?.routeId,
                category = category.wire,
                reason = state.details.trim(),
                createdAt = System.currentTimeMillis()
            )
            val result = claimRepository.submitClaim(claim)
            _uiState.update {
                if (result.isSuccess) {
                    it.copy(isSubmitting = false, sent = true)
                } else {
                    it.copy(
                        isSubmitting = false,
                        error = result.exceptionOrNull()?.message ?: genericError
                    )
                }
            }
        }
    }
}
