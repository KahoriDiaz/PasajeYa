package com.pasajeya.app.feature.auth.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pasajeya.app.feature.auth.domain.AccountRepository
import com.pasajeya.app.feature.auth.domain.AuthRepository
import com.pasajeya.app.feature.auth.domain.model.AuthDestination
import com.pasajeya.app.feature.auth.domain.model.UserProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val email: String = "",
    val profile: UserProfile? = null,
    val isLoading: Boolean = true,
    val hasError: Boolean = false
) {
    val displayName: String
        get() = profile?.firstName
            ?.takeIf { it.isNotBlank() }
            ?: email.substringBefore('@')
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val accountRepository: AccountRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _events = Channel<AuthDestination>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        val user = authRepository.currentUser()
        if (user == null) {
            _uiState.update { it.copy(isLoading = false) }
            viewModelScope.launch {
                _events.send(AuthDestination.WELCOME)
            }
        } else {
            _uiState.update { it.copy(email = user.email) }
            viewModelScope.launch {
                accountRepository.observeUserProfile(user.uid)
                    .catch { error ->
                        if (error is CancellationException) throw error
                        _uiState.update {
                            it.copy(isLoading = false, hasError = true)
                        }
                    }
                    .collect { profile ->
                        _uiState.update {
                            it.copy(
                                profile = profile,
                                isLoading = false,
                                hasError = false
                            )
                        }
                    }
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            accountRepository.clearSession()
            runCatching { authRepository.signOut() }
            _events.send(AuthDestination.WELCOME)
        }
    }
}
