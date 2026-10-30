package com.pasajeya.app.feature.benefit.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pasajeya.app.feature.auth.domain.AccountRepository
import com.pasajeya.app.feature.auth.domain.AuthRepository
import com.pasajeya.app.feature.benefit.data.CarneImageAnalyzer
import com.pasajeya.app.feature.benefit.domain.BenefitRepository
import com.pasajeya.app.feature.benefit.domain.CarneTextCheck
import com.pasajeya.app.feature.benefit.domain.model.BenefitOperationResult
import com.pasajeya.app.feature.benefit.domain.model.BenefitRequestState
import com.pasajeya.app.feature.benefit.domain.model.BenefitStatus
import com.pasajeya.app.feature.benefit.domain.model.UserBenefit
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BenefitUiState(
    val isLoading: Boolean = false,
    val benefit: UserBenefit? = null,
    val request: BenefitRequestState? = null,
    val error: String? = null,
    val institutionalEmail: String = "",
    val verificationCode: String = "",
    val carneUnreadable: Boolean = false,
    val codeTimerSeconds: Int = 0,
    val canResendCode: Boolean = true
)

@HiltViewModel
class BenefitViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val accountRepository: AccountRepository,
    private val benefitRepository: BenefitRepository,
    private val carneImageAnalyzer: CarneImageAnalyzer
) : ViewModel() {

    private val _uiState = MutableStateFlow(BenefitUiState())
    val uiState: StateFlow<BenefitUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<BenefitEvent>()
    val events: SharedFlow<BenefitEvent> = _events.asSharedFlow()

    private var timerJob: Job? = null

    init {
        val uid = authRepository.currentUser()?.uid
        if (uid != null) {
            observeStatus(uid)
        }
    }

    private fun observeStatus(uid: String) {
        viewModelScope.launch {
            benefitRepository.observeUserBenefit(uid).collect { benefit ->
                _uiState.update { it.copy(benefit = benefit) }
            }
        }
        viewModelScope.launch {
            benefitRepository.observeBenefitRequest(uid).collect { request ->
                _uiState.update { it.copy(request = request) }
                if (request?.stage == "code_sent") {
                    startTimer()
                } else {
                    stopTimer()
                }
            }
        }
    }

    fun onEmailChange(email: String) {
        _uiState.update { it.copy(institutionalEmail = email) }
    }

    fun onCodeChange(code: String) {
        if (code.length <= 6 && code.all { it.isDigit() }) {
            _uiState.update { it.copy(verificationCode = code) }
        }
    }

    /**
     * Endereza la foto, lee su texto con ML Kit y, si parece un carné de la universidad,
     * la sube y avisa al servidor (Cloud Function `submitCarne`).
     */
    fun submitCarne(imageBytes: ByteArray, rotationDegrees: Int) {
        if (_uiState.value.isLoading) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(isLoading = true, error = null, carneUnreadable = false)
            }
            try {
                val analysis = carneImageAnalyzer.analyze(imageBytes, rotationDegrees)
                if (!CarneTextCheck.looksLikeUniversityCard(analysis.recognizedText)) {
                    _uiState.update { it.copy(isLoading = false, carneUnreadable = true) }
                    return@launch
                }

                val sessionId = getSessionId()
                val requestId = System.currentTimeMillis().toString()
                val result = benefitRepository.submitCarne(
                    sessionId,
                    requestId,
                    analysis.jpegBytes
                )
                if (result is BenefitOperationResult.Error) {
                    _uiState.update { it.copy(isLoading = false, error = result.message) }
                } else {
                    _uiState.update { it.copy(isLoading = false) }
                    _events.emit(BenefitEvent.CarneSubmitted)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.update { it.copy(isLoading = false, carneUnreadable = true) }
            }
        }
    }

    fun requestEmailCode() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val sessionId = getSessionId()
            val email = _uiState.value.institutionalEmail
            
            val result = benefitRepository.requestBenefitCode(sessionId, email)
            _uiState.update { it.copy(isLoading = false) }
            
            if (result is BenefitOperationResult.Error) {
                _uiState.update { it.copy(error = result.message) }
            }
        }
    }

    fun confirmCode() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val sessionId = getSessionId()
            val code = _uiState.value.verificationCode
            
            val result = benefitRepository.confirmBenefitCode(sessionId, code)
            _uiState.update { it.copy(isLoading = false) }
            
            if (result is BenefitOperationResult.Error) {
                _uiState.update { it.copy(error = result.message) }
            } else {
                _events.emit(BenefitEvent.Success)
            }
        }
    }

    private fun startTimer() {
        timerJob?.cancel()
        _uiState.update { it.copy(codeTimerSeconds = 600, canResendCode = false) }
        timerJob = viewModelScope.launch {
            while (_uiState.value.codeTimerSeconds > 0) {
                delay(1000)
                _uiState.update { 
                    it.copy(
                        codeTimerSeconds = it.codeTimerSeconds - 1,
                        canResendCode = it.codeTimerSeconds <= 540 // Permite reenvío tras 1 min
                    ) 
                }
            }
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        _uiState.update { it.copy(codeTimerSeconds = 0, canResendCode = true) }
    }

    private suspend fun getSessionId(): String {
        val uid = authRepository.currentUser()?.uid ?: throw IllegalStateException("No session")
        return accountRepository.deviceSession.first { it?.uid == uid }?.sessionId ?: ""
    }
}

sealed class BenefitEvent {
    object Success : BenefitEvent()
    object CarneSubmitted : BenefitEvent()
}
