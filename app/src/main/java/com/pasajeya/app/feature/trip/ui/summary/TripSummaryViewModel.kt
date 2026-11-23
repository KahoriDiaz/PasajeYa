package com.pasajeya.app.feature.trip.ui.summary

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pasajeya.app.feature.auth.domain.AuthRepository
import com.pasajeya.app.feature.trip.domain.TripRepository
import com.pasajeya.app.feature.trip.domain.model.Trip
import com.pasajeya.app.feature.wallet.domain.WalletRepository
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
    private val tripRepository: TripRepository,
    private val authRepository: AuthRepository,
    private val walletRepository: WalletRepository
) : ViewModel() {

    private val tripId: String = checkNotNull(savedStateHandle["tripId"])
    private var charged = false
    private val _uiState = MutableStateFlow(TripSummaryUiState())
    val uiState: StateFlow<TripSummaryUiState> = _uiState.asStateFlow()

    init {
        loadTrip()
    }

    /**
     * Cierra la retencion del viaje: cobra el total real una sola vez. Si no hay
     * retencion activa, la billetera devuelve un fallo y no pasa nada.
     */
    private suspend fun chargeOnce(trip: Trip?) {
        if (charged) return
        if (trip == null || trip.status != "closed") return
        val total = trip.fareBreakdown?.totalCents ?: return
        val uid = authRepository.currentUser()?.uid ?: return
        charged = true
        walletRepository.charge(uid, total)
    }

    private fun loadTrip() {
        viewModelScope.launch {
            tripRepository.observeTrip(tripId)
                .catch { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                }
                .collect { trip ->
                    _uiState.update { it.copy(isLoading = false, trip = trip) }
                    chargeOnce(trip)
                }
        }
    }
}
