package com.pasajeya.app.feature.auth.ui.register

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pasajeya.app.feature.auth.domain.AccountRepository
import com.pasajeya.app.feature.auth.domain.AuthRepository
import com.pasajeya.app.feature.auth.domain.BenefitType
import com.pasajeya.app.feature.auth.domain.DeviceSessionRepository
import com.pasajeya.app.feature.auth.domain.CommonPasswordsProvider
import com.pasajeya.app.feature.auth.domain.DocumentTextParser
import com.pasajeya.app.feature.auth.domain.DocumentTextRecognizer
import com.pasajeya.app.feature.auth.domain.RegistrationConfig
import com.pasajeya.app.feature.auth.domain.RegistrationRepository
import com.pasajeya.app.feature.auth.domain.model.AccountException
import com.pasajeya.app.feature.auth.domain.model.AccountFailure
import com.pasajeya.app.feature.auth.domain.model.AuthDestination
import com.pasajeya.app.feature.auth.domain.model.AuthException
import com.pasajeya.app.feature.auth.domain.model.AuthFailure
import com.pasajeya.app.feature.auth.domain.model.UserRole
import com.pasajeya.app.feature.auth.domain.model.resolveAuthDestination
import com.pasajeya.app.feature.auth.domain.validation.PasswordValidation
import com.pasajeya.app.feature.auth.domain.validation.Validators
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ScanDocument { DNI, LICENSE, UNIVERSITY_CARD }

enum class ScanFailure {
    UNREADABLE,
    DNI_NOT_FOUND,
    LICENSE_NOT_FOUND,
    CATEGORY_UNSUPPORTED,
    UNIVERSITY_CARD_NOT_FOUND
}

data class RegisterUiState(
    val role: UserRole = UserRole.PASSENGER,
    val fullName: String = "",
    val dni: String = "",
    val birthDate: String = "",
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val benefitType: BenefitType = BenefitType.REGULAR,
    val plate: String = "",
    val assignedRouteId: String = "",
    val licenseNumber: String = "",
    val licenseCategory: String = "",
    val universityCardCode: String = "",
    val cardExpiryDate: String? = null,
    val cardExpired: Boolean = false,
    val universityCardError: Boolean = false,
    val passwordVisible: Boolean = false,
    val confirmPasswordVisible: Boolean = false,
    val validation: PasswordValidation? = null,
    val isLoadingPasswords: Boolean = true,
    val passwordsUnavailable: Boolean = false,
    val fullNameError: Boolean = false,
    val dniError: Boolean = false,
    val birthDateError: Boolean = false,
    val emailError: Boolean = false,
    val passwordError: Boolean = false,
    val confirmPasswordError: Boolean = false,
    val plateError: Boolean = false,
    val routeError: Boolean = false,
    val licenseNumberError: Boolean = false,
    val licenseCategoryError: Boolean = false,
    val scanning: ScanDocument? = null,
    val scanFailure: ScanFailure? = null,
    val authFailure: AuthFailure? = null,
    val accountFailure: AccountFailure? = null,
    val isSubmitting: Boolean = false
) {
    /** A-I no autoriza transporte publico urbano: bloquea el registro del conductor. */
    val licenseBlocked: Boolean
        get() = role == UserRole.OPERATOR &&
            licenseCategory == RegistrationConfig.BLOCKED_LICENSE_CATEGORY

    /** Cuando el carné está vencido, el beneficio se mantiene como regular. */
    val universityBlocked: Boolean
        get() = role == UserRole.PASSENGER &&
            benefitType == BenefitType.UNIVERSITY &&
            cardExpired

    /** Edad mínima requerida para crear cuenta: 18 años cumplidos. */
    val age: Int
        get() {
            val birthDate = this.birthDate.trim()
            if (birthDate.isBlank()) return 0
            return runCatching {
                val parsed = java.time.LocalDate.parse(
                    birthDate,
                    java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")
                )
                java.time.Period.between(parsed, java.time.LocalDate.now()).years
            }.getOrDefault(0)
        }

    val isAdult: Boolean
        get() = age >= 18

    val canSubmit: Boolean
        get() = !isLoadingPasswords && !licenseBlocked && scanning == null &&
            birthDateError == false && isAdult
}

@HiltViewModel
class RegisterViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val accountRepository: AccountRepository,
    private val registrationRepository: RegistrationRepository,
    private val documentTextRecognizer: DocumentTextRecognizer,
    private val commonPasswordsProvider: CommonPasswordsProvider,
    private val deviceSessionRepository: DeviceSessionRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(RegisterUiState())
    val uiState: StateFlow<RegisterUiState> = _uiState.asStateFlow()

    private val _events = Channel<AuthDestination>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var commonPasswords: Set<String>? = null

    init {
        loadCommonPasswords()
    }

    private fun edit(block: (RegisterUiState) -> RegisterUiState) {
        _uiState.update { block(it).copy(authFailure = null, accountFailure = null) }
    }

    fun onRoleChange(role: UserRole) = edit { it.copy(role = role, scanFailure = null) }

    fun onFullNameChange(value: String) =
        edit { it.copy(fullName = value.take(80), fullNameError = false) }

    fun onDniChange(value: String) =
        edit { it.copy(dni = value.filter(Char::isDigit).take(8), dniError = false) }

    fun onBirthDateChange(value: String) {
        val digits = value.filter(Char::isDigit).take(8)
        val formatted = buildString {
            append(digits.take(2))
            if (digits.length > 2) append('/').append(digits.substring(2).take(2))
            if (digits.length > 4) append('/').append(digits.substring(4).take(4))
        }
        _uiState.update {
            it.copy(
                birthDate = formatted,
                birthDateError = false,
                accountFailure = null
            )
        }
    }

    fun onBenefitTypeChange(value: BenefitType) = edit { it.copy(benefitType = value) }

    fun onPlateChange(value: String) = edit {
        it.copy(plate = value.uppercase().filter(Char::isLetterOrDigit).take(6), plateError = false)
    }

    fun onRouteChange(routeId: String) =
        edit { it.copy(assignedRouteId = routeId, routeError = false) }

    fun onUniversityCardCodeChange(value: String) =
        edit { it.copy(universityCardCode = value.trim(), universityCardError = false) }

    fun onLicenseNumberChange(value: String) = edit {
        it.copy(
            licenseNumber = value.uppercase().filter(Char::isLetterOrDigit).take(9),
            licenseNumberError = false
        )
    }

    fun onLicenseCategoryChange(value: String) =
        edit { it.copy(licenseCategory = value, licenseCategoryError = false) }

    fun onEmailChange(value: String) {
        edit {
            it.copy(
                email = value,
                emailError = false,
                validation = computeValidation(value, it.password)
            )
        }
    }

    fun onPasswordChange(value: String) {
        edit {
            it.copy(
                password = value,
                passwordError = false,
                validation = computeValidation(it.email, value)
            )
        }
    }

    fun onConfirmPasswordChange(value: String) {
        _uiState.update { it.copy(confirmPassword = value, confirmPasswordError = false) }
    }

    fun togglePasswordVisibility() {
        _uiState.update { it.copy(passwordVisible = !it.passwordVisible) }
    }

    fun toggleConfirmPasswordVisibility() {
        _uiState.update { it.copy(confirmPasswordVisible = !it.confirmPasswordVisible) }
    }

    fun retryCommonPasswords() {
        if (!_uiState.value.isLoadingPasswords) loadCommonPasswords()
    }

    fun dismissScanFailure() {
        _uiState.update { it.copy(scanFailure = null) }
    }

    /** OCR local: la foto se lee en el telefono y solo se autocompletan los campos leidos. */
    fun onDocumentCaptured(document: ScanDocument, jpeg: ByteArray, rotationDegrees: Int) {
        if (_uiState.value.scanning != null) return
        _uiState.update { it.copy(scanning = document, scanFailure = null) }
        viewModelScope.launch {
            val text = try {
                documentTextRecognizer.recognize(jpeg, rotationDegrees)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.update { it.copy(scanning = null, scanFailure = ScanFailure.UNREADABLE) }
                return@launch
            }
            when (document) {
                ScanDocument.DNI -> applyDniScan(text)
                ScanDocument.LICENSE -> applyLicenseScan(text)
                ScanDocument.UNIVERSITY_CARD -> applyUniversityCardScan(text)
            }
        }
    }

    private fun applyDniScan(text: String) {
        val scan = DocumentTextParser.parseDni(text)
        _uiState.update { current ->
            current.copy(
                scanning = null,
                scanFailure = if (scan.dni == null) ScanFailure.DNI_NOT_FOUND else null,
                dni = scan.dni ?: current.dni,
                fullName = scan.fullName ?: current.fullName,
                birthDate = scan.birthDate ?: current.birthDate,
                dniError = if (scan.dni != null) false else current.dniError,
                fullNameError = if (scan.fullName != null) false else current.fullNameError,
                birthDateError = if (scan.birthDate != null) false else current.birthDateError
            )
        }
    }

    private fun applyLicenseScan(text: String) {
        val scan = DocumentTextParser.parseLicense(text)
        _uiState.update { current ->
            current.copy(
                scanning = null,
                scanFailure = when {
                    scan.isEmpty -> ScanFailure.LICENSE_NOT_FOUND
                    scan.category == null -> ScanFailure.CATEGORY_UNSUPPORTED
                    else -> null
                },
                licenseNumber = scan.licenseNumber ?: current.licenseNumber,
                licenseCategory = scan.category ?: current.licenseCategory,
                licenseNumberError = if (scan.licenseNumber != null) false else current.licenseNumberError,
                licenseCategoryError = scan.category == RegistrationConfig.BLOCKED_LICENSE_CATEGORY
            )
        }
    }

    private fun applyUniversityCardScan(text: String) {
        val scan = DocumentTextParser.parseUniversityCard(text, LocalDate.now())
        _uiState.update { current ->
            current.copy(
                scanning = null,
                scanFailure = if (scan.studentCode == null) ScanFailure.UNIVERSITY_CARD_NOT_FOUND else null,
                universityCardCode = scan.studentCode ?: current.universityCardCode,
                cardExpiryDate = scan.expiryDate?.format(DATE_FORMAT),
                cardExpired = scan.expired == true,
                universityCardError = scan.studentCode == null
            )
        }
    }

    fun submit() {
        val current = _uiState.value
        if (current.isSubmitting || !current.canSubmit) return

        val passwords = commonPasswords
        if (passwords == null) {
            _uiState.update { it.copy(passwordsUnavailable = !current.isLoadingPasswords) }
            return
        }

        val email = current.email.trim()
        val fullName = current.fullName.trim().replace(Regex("\\s+"), " ")
        val validation = Validators.validatePassword(current.password, email, passwords)
        val isOperator = current.role == UserRole.OPERATOR
        val plate = Validators.normalizePlate(current.plate)
        val licenseNumber = Validators.normalizeLicenseNumber(current.licenseNumber)
        val route = RegistrationConfig.routeById(current.assignedRouteId)

        // Validar fecha de nacimiento: formato DD/MM/AAAA y edad mínima 18 años.
        val birthDate = current.birthDate.trim()
        val parsedBirthDate = runCatching {
            java.time.LocalDate.parse(
                birthDate,
                java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")
            )
        }.getOrNull()
        val birthDateError = birthDate.isBlank() ||
            parsedBirthDate == null ||
            parsedBirthDate.isAfter(java.time.LocalDate.now()) ||
            java.time.Period.between(parsedBirthDate, java.time.LocalDate.now()).years < 18
        val age = parsedBirthDate?.let {
            java.time.Period.between(it, java.time.LocalDate.now()).years
        } ?: 0

        // El carné vencido no da beneficio universitario: se registra como regular.
        val effectiveBenefitType = if (current.benefitType == BenefitType.UNIVERSITY && current.cardExpired) {
            BenefitType.REGULAR
        } else {
            current.benefitType
        }

        val universityRequired = !isOperator && effectiveBenefitType == BenefitType.UNIVERSITY
        val universityCardError = universityRequired && current.universityCardCode.isBlank()

        val next = current.copy(
            email = email,
            fullName = fullName,
            plate = plate,
            licenseNumber = licenseNumber,
            birthDate = birthDate,
            validation = validation,
            benefitType = effectiveBenefitType,
            fullNameError = !Validators.isValidPersonName(fullName),
            dniError = !Validators.isValidDni(current.dni),
            birthDateError = birthDateError,
            emailError = !Validators.isValidEmail(email) ||
                (effectiveBenefitType == BenefitType.UNIVERSITY && !Validators.isInstitutionalEmail(email)),
            passwordError = !validation.valid,
            confirmPasswordError = current.confirmPassword != current.password,
            plateError = isOperator && !Validators.isValidPlate(plate),
            routeError = isOperator && route == null,
            licenseNumberError = isOperator && !Validators.isValidLicenseNumber(licenseNumber),
            licenseCategoryError = isOperator &&
                !RegistrationConfig.isLicenseCategoryAllowed(current.licenseCategory),
            universityCardError = universityCardError,
            authFailure = null,
            accountFailure = null
        )
        val hasErrors = next.fullNameError || next.dniError || next.birthDateError || next.emailError ||
            next.passwordError || next.confirmPasswordError || next.plateError ||
            next.routeError || next.licenseNumberError || next.licenseCategoryError ||
            next.universityCardError
        if (hasErrors) {
            _uiState.value = next
            return
        }

        _uiState.value = next.copy(isSubmitting = true)
        viewModelScope.launch {
            try {
                // Si el perfil fallo tras crear la cuenta, el reintento reutiliza esa cuenta.
                val existing = authRepository.currentUser()
                val user = if (existing != null && existing.email.equals(email, ignoreCase = true)) {
                    existing
                } else {
                    authRepository.register(email, current.password)
                }

                if (isOperator) {
                    registrationRepository.saveOperator(
                        uid = user.uid,
                        email = email,
                        fullName = fullName,
                        dni = current.dni,
                        birthDate = birthDate,
                        age = age,
                        plate = plate,
                        route = checkNotNull(route),
                        licenseNumber = licenseNumber,
                        licenseCategory = current.licenseCategory,
                        deviceId = deviceSessionRepository.deviceId
                    )
                } else {
                    val expiryDate = current.cardExpiryDate?.let { java.time.LocalDate.parse(it, DATE_FORMAT) }
                    registrationRepository.savePassenger(
                        uid = user.uid,
                        email = email,
                        fullName = fullName,
                        dni = current.dni,
                        birthDate = birthDate,
                        age = age,
                        benefitType = effectiveBenefitType,
                        deviceId = deviceSessionRepository.deviceId,
                        universityCardCode = if (effectiveBenefitType == BenefitType.UNIVERSITY) current.universityCardCode else null,
                        universityCardExpiry = expiryDate
                    )
                }

                val session = accountRepository.ensureSession(user.uid)
                _uiState.update {
                    it.copy(isSubmitting = false, password = "", confirmPassword = "", dni = "")
                }
                _events.send(resolveAuthDestination(session))
            } catch (error: CancellationException) {
                throw error
            } catch (error: AuthException) {
                Log.e("AUTH_SESSION", "Error de auth al registrar", error)
                _uiState.update { it.copy(isSubmitting = false, authFailure = error.failure) }
            } catch (error: AccountException) {
                Log.e("AUTH_SESSION", "Error de cuenta al registrar", error)
                _uiState.update { it.copy(isSubmitting = false, accountFailure = error.failure) }
            } catch (error: Exception) {
                Log.e("AUTH_SESSION", "Error desconocido al registrar", error)
                _uiState.update {
                    it.copy(isSubmitting = false, accountFailure = AccountFailure.Unknown)
                }
            }
        }
    }

    private companion object {
        private val DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale("es", "PE"))
    }

    private fun loadCommonPasswords() {
        _uiState.update { it.copy(isLoadingPasswords = true, passwordsUnavailable = false) }
        viewModelScope.launch {
            try {
                val passwords = commonPasswordsProvider.get()
                commonPasswords = passwords
                _uiState.update { current ->
                    current.copy(
                        isLoadingPasswords = false,
                        passwordsUnavailable = false,
                        validation = Validators.validatePassword(
                            value = current.password,
                            email = current.email,
                            commonPasswords = passwords
                        )
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                commonPasswords = null
                _uiState.update {
                    it.copy(
                        isLoadingPasswords = false,
                        passwordsUnavailable = true,
                        validation = null
                    )
                }
            }
        }
    }

    private fun computeValidation(email: String, password: String): PasswordValidation? {
        val passwords = commonPasswords ?: return null
        return Validators.validatePassword(password, email, passwords)
    }
}
