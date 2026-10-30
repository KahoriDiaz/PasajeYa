package com.pasajeya.app.feature.wallet.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pasajeya.app.feature.auth.domain.AuthRepository
import com.pasajeya.app.feature.fare.domain.FareEstimator
import com.pasajeya.app.feature.history.domain.HistoryFilter
import com.pasajeya.app.feature.history.domain.HistoryRepository
import com.pasajeya.app.feature.wallet.domain.DemoWallet
import com.pasajeya.app.feature.wallet.domain.WalletError
import com.pasajeya.app.feature.wallet.domain.WalletRepository
import com.pasajeya.app.feature.wallet.domain.WalletResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WalletUiState(
    val isLoading: Boolean = true,
    val hasAccount: Boolean = true,
    val wallet: DemoWallet = DemoWallet(),
    /** Total del último viaje cerrado, real, tomado del historial. */
    val lastTripCents: Int? = null,
    val lastTripRouteName: String? = null,
    val error: WalletError? = null,
    val loadFailed: Boolean = false
)

@HiltViewModel
class WalletViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val walletRepository: WalletRepository,
    private val historyRepository: HistoryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(WalletUiState())
    val uiState: StateFlow<WalletUiState> = _uiState.asStateFlow()

    private val uid: String? = authRepository.currentUser()?.uid

    init {
        if (uid == null) {
            _uiState.update { it.copy(isLoading = false, hasAccount = false) }
        } else {
            observeWallet(uid)
            loadLastTrip(uid)
        }
    }

    fun retry() {
        val current = uid ?: return
        _uiState.update { it.copy(loadFailed = false) }
        loadLastTrip(current)
    }

    fun recharge(amountCents: Int) = run { walletRepository.recharge(it, amountCents) }

    /** Retiene la tarifa máxima posible antes de subir al vehículo. */
    fun holdMaxFare() = run {
        walletRepository.hold(it, FareEstimator.SAFETY_CEILING_CENTS)
    }

    /** Cobra el total real del último viaje cerrado contra la retención. */
    fun chargeLastTrip() {
        val cents = _uiState.value.lastTripCents ?: return
        run { walletRepository.charge(it, cents) }
    }

    fun releaseHold() = run { walletRepository.release(it) }

    private fun run(action: suspend (String) -> WalletResult) {
        val current = uid ?: return
        viewModelScope.launch {
            when (val result = action(current)) {
                is WalletResult.Success -> _uiState.update { it.copy(error = null) }
                is WalletResult.Failure -> _uiState.update { it.copy(error = result.error) }
            }
        }
    }

    private fun observeWallet(uid: String) {
        viewModelScope.launch {
            walletRepository.observe(uid)
                .catch { error ->
                    if (error is CancellationException) throw error
                    _uiState.update { it.copy(isLoading = false, loadFailed = true) }
                }
                .collect { wallet ->
                    _uiState.update { it.copy(isLoading = false, wallet = wallet) }
                }
        }
    }

    private fun loadLastTrip(uid: String) {
        viewModelScope.launch {
            try {
                val page = historyRepository.loadPage(uid, HistoryFilter.ALL, null)
                val last = page.trips.firstOrNull {
                    it.trip.status == "closed" && it.trip.fareBreakdown != null
                }
                _uiState.update {
                    it.copy(
                        lastTripCents = last?.trip?.fareBreakdown?.totalCents,
                        lastTripRouteName = last?.routeName
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.update { it.copy(loadFailed = true) }
            }
        }
    }
}
