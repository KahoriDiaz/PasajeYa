package com.pasajeya.app.feature.trip.ui.summary

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

data class TripSummaryUiState(
    val isLoading: Boolean = true,
    val trip: Trip? = null,
    val error: String? = null
)

@HiltViewModel
class TripSummaryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val tripRepository: TripRepository
) : ViewModel() {

    private val tripId: String = checkNotNull(savedStateHandle["tripId"])
    private val _uiState = MutableStateFlow(TripSummaryUiState())
    val uiState: StateFlow<TripSummaryUiState> = _uiState.asStateFlow()

    init {
        loadTrip()
    }

    private fun loadTrip() {
        viewModelScope.launch {
            tripRepository.observeTrip(tripId)
                .catch { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                }
                .collect { trip ->
                    _uiState.update { it.copy(isLoading = false, trip = trip) }
                }
        }
    }
}
