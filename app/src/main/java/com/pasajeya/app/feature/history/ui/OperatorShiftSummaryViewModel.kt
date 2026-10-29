package com.pasajeya.app.feature.history.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pasajeya.app.feature.auth.domain.AuthRepository
import com.pasajeya.app.feature.history.domain.HistoryRepository
import com.pasajeya.app.feature.history.domain.OperatorShiftSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OperatorShiftSummaryUiState(
    val summary: OperatorShiftSummary? = null,
    val isLoading: Boolean = true,
    val hasError: Boolean = false
)

@HiltViewModel
class OperatorShiftSummaryViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val historyRepository: HistoryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(OperatorShiftSummaryUiState())
    val uiState: StateFlow<OperatorShiftSummaryUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    fun refresh() {
        loadJob?.cancel()
        val uid = authRepository.currentUser()?.uid
        if (uid == null) {
            _uiState.update {
                it.copy(isLoading = false, hasError = true)
            }
            return
        }

        _uiState.update {
            it.copy(
                isLoading = it.summary == null,
                hasError = false
            )
        }
        loadJob = viewModelScope.launch {
            try {
                val summary = historyRepository
                    .loadLatestOperatorShiftSummary(uid)
                _uiState.update {
                    it.copy(
                        summary = summary,
                        isLoading = false,
                        hasError = false
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, hasError = true)
                }
            }
        }
    }
}
