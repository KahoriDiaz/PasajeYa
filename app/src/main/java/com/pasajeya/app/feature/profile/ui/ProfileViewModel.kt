package com.pasajeya.app.feature.profile.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import com.pasajeya.app.feature.auth.domain.AuthRepository
import com.pasajeya.app.feature.wallet.domain.WalletRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Locale

data class ProfileUiState(
    val fullName: String = "",
    val email: String = "",
    val dni: String = "",
    val birthDate: String = "",
    val age: Int = 0,
    val role: String = "",
    val benefitType: String = "Regular",
    val balanceCents: Int = 0,
    val emailVerified: Boolean = false,
    val resendInProgress: Boolean = false,
    val resendError: String? = null,
    val resendSuccess: Boolean = false,
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val authRepository: AuthRepository,
    private val walletRepository: WalletRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        loadProfile()
    }

    private fun loadProfile() {
        val uid = authRepository.currentUser()?.uid ?: run {
            _uiState.update { it.copy(isLoading = false, loadFailed = true) }
            return
        }
        val user = authRepository.currentUser()
        _uiState.update {
            it.copy(
                email = user?.email ?: "",
                emailVerified = user?.emailVerified == true
            )
        }

        viewModelScope.launch {
            try {
                val doc = firestore.collection("users").document(uid).get().await()
                if (doc.exists()) {
                    val fullName = doc.getString("fullName") ?: doc.getString("firstName") ?: ""
                    val dni = doc.getString("dni") ?: ""
                    val birthDate = doc.getString("birthDate") ?: ""
                    val age = doc.getLong("age")?.toInt() ?: 0
                    val role = doc.getString("role") ?: ""
                    val benefitTypeRaw = doc.getString("benefitType") ?: "regular"
                    val benefitLabel = if (benefitTypeRaw == "university") "Universitario" else "Regular"

                    _uiState.update {
                        it.copy(
                            fullName = fullName,
                            dni = dni,
                            birthDate = birthDate,
                            age = age,
                            role = if (role.isNotBlank()) role.capitalize(Locale.ROOT) else "",
                            benefitType = benefitLabel,
                            isLoading = false
                        )
                    }
                }

                walletRepository.observe(uid)
                    .catch { error ->
                        if (error is CancellationException) throw error
                    }
                    .collect { wallet ->
                        _uiState.update { it.copy(balanceCents = wallet.balanceCents) }
                    }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.update { it.copy(isLoading = false, loadFailed = true) }
            }
        }
    }

    fun resendVerification() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(resendInProgress = true, resendError = null, resendSuccess = false)
            }
            try {
                val user = authRepository.currentUser()
                if (user != null && !user.emailVerified) {
                    withContext(Dispatchers.IO) {
                        authRepository.sendEmailVerification()
                    }
                    _uiState.update {
                        it.copy(resendInProgress = false, resendSuccess = true)
                    }
                } else {
                    _uiState.update {
                        it.copy(resendInProgress = false, resendError = "El correo ya está verificado o no hay sesión activa.")
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _uiState.update {
                        it.copy(
                            resendInProgress = false,
                            resendError = "Por favor espera unos momentos antes de volver a intentar."
                        )
                    }
                }
            }
        }
    }
}
