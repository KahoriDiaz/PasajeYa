package com.pasajeya.app.feature.auth.ui.register

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pasajeya.app.R
import com.pasajeya.app.core.theme.ErrorRed
import com.pasajeya.app.core.theme.TextGray
import com.pasajeya.app.core.theme.TextPrimary
import com.pasajeya.app.core.ui.AuthScreen
import com.pasajeya.app.core.ui.EmailField
import com.pasajeya.app.core.ui.FormCard
import com.pasajeya.app.core.ui.InlineMessage
import com.pasajeya.app.core.ui.MessageTone
import com.pasajeya.app.core.ui.PasswordField
import com.pasajeya.app.core.ui.PasswordRequirements
import com.pasajeya.app.core.ui.PrimaryButton
import com.pasajeya.app.core.ui.ScreenHeading
import com.pasajeya.app.core.ui.SecondaryTextButton
import com.pasajeya.app.feature.auth.domain.BenefitType
import com.pasajeya.app.feature.auth.domain.RegistrationConfig
import com.pasajeya.app.feature.auth.domain.model.AuthDestination
import com.pasajeya.app.feature.auth.domain.model.UserRole
import com.pasajeya.app.feature.auth.ui.accountFailureMessage
import com.pasajeya.app.feature.auth.ui.authFailureMessage

@Composable
fun RegisterScreen(
    onBack: () -> Unit,
    onLogin: () -> Unit,
    onNavigate: (AuthDestination) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RegisterViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var captureTarget by remember { mutableStateOf<ScanDocument?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect(onNavigate)
    }

    captureTarget?.let { target ->
        DocumentCaptureDialog(
            title = stringResource(
                when (target) {
                    ScanDocument.DNI -> R.string.register_capture_dni_title
                    ScanDocument.LICENSE -> R.string.register_capture_license_title
                    ScanDocument.UNIVERSITY_CARD -> R.string.register_capture_carne_title
                }
            ),
            onDismiss = { captureTarget = null },
            onCaptured = { jpeg, rotation ->
                captureTarget = null
                viewModel.onDocumentCaptured(target, jpeg, rotation)
            }
        )
    }

    val errorText = when {
        state.accountFailure != null -> accountFailureMessage(state.accountFailure!!)
        state.authFailure != null -> authFailureMessage(state.authFailure!!)
        state.passwordsUnavailable -> stringResource(R.string.register_passwords_unavailable)
        else -> null
    }
    val scanErrorText = when (state.scanFailure) {
        ScanFailure.UNREADABLE -> stringResource(R.string.register_scan_unreadable)
        ScanFailure.DNI_NOT_FOUND -> stringResource(R.string.register_scan_dni_missing)
        ScanFailure.LICENSE_NOT_FOUND -> stringResource(R.string.register_scan_license_missing)
        ScanFailure.CATEGORY_UNSUPPORTED ->
            stringResource(R.string.register_scan_category_unsupported)
        ScanFailure.UNIVERSITY_CARD_NOT_FOUND ->
            stringResource(R.string.register_scan_university_missing)
        null -> null
    }
    val busy = state.isSubmitting || state.scanning != null
    val isDriver = state.role == UserRole.DRIVER
    val isCompany = state.role == UserRole.COMPANY

    AuthScreen(
        title = stringResource(R.string.register_title),
        modifier = modifier.testTag("register_screen"),
        onBack = onBack
    ) {
        ScreenHeading(
            title = stringResource(R.string.register_title),
            subtitle = stringResource(R.string.register_subtitle_local)
        )

        FormCard {
            SectionLabel(stringResource(R.string.register_role_title))
            ChoiceRow(
                options = listOf(
                    UserRole.PASSENGER to stringResource(R.string.register_role_passenger),
                    UserRole.DRIVER to stringResource(R.string.register_role_driver),
                    UserRole.COMPANY to stringResource(R.string.register_role_company)
                ),
                selected = state.role,
                enabled = !busy,
                onSelect = viewModel::onRoleChange
            )

            OutlinedButton(
                onClick = {
                    captureTarget = when {
                        isDriver -> ScanDocument.LICENSE
                        else -> ScanDocument.DNI
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("scan_button"),
                enabled = !busy
            ) {
                Text(
                    stringResource(
                        when {
                            isDriver -> R.string.register_scan_license
                            else -> R.string.register_scan_dni
                        }
                    )
                )
            }
            if (!isDriver && !isCompany) {
                OutlinedButton(
                    onClick = { captureTarget = ScanDocument.UNIVERSITY_CARD },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    enabled = !busy
                ) {
                    Text(stringResource(R.string.register_scan_carne))
                }
            }
            if (isDriver) {
                OutlinedButton(
                    onClick = { captureTarget = ScanDocument.DNI },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    enabled = !busy
                ) {
                    Text(stringResource(R.string.register_scan_dni))
                }
            }
            if (state.scanning != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                    Text(stringResource(R.string.register_scanning), color = TextGray)
                }
            }
            if (scanErrorText != null) {
                InlineMessage(text = scanErrorText, tone = MessageTone.WARNING)
            }

            TextField(
                value = state.fullName,
                onValueChange = viewModel::onFullNameChange,
                label = stringResource(R.string.field_full_name),
                enabled = !busy,
                error = if (state.fullNameError) {
                    stringResource(R.string.register_full_name_invalid)
                } else {
                    null
                },
                keyboardType = KeyboardType.Text
            )
            TextField(
                value = state.dni,
                onValueChange = viewModel::onDniChange,
                label = stringResource(R.string.field_dni),
                placeholder = stringResource(R.string.field_dni_hint),
                enabled = !busy,
                error = if (state.dniError) stringResource(R.string.profile_dni_invalid) else null,
                keyboardType = KeyboardType.Number
            )
            TextField(
                value = state.birthDate,
                onValueChange = viewModel::onBirthDateChange,
                label = stringResource(R.string.field_birth_date),
                placeholder = stringResource(R.string.field_birth_date_hint),
                enabled = !busy,
                error = if (state.birthDateError) {
                    stringResource(R.string.register_birth_date_invalid)
                } else {
                    null
                },
                keyboardType = KeyboardType.Number
            )
            EmailField(
                value = state.email,
                onValueChange = viewModel::onEmailChange,
                enabled = !busy,
                isError = state.emailError,
                errorText = if (state.emailError && state.benefitType == BenefitType.UNIVERSITY) {
                    stringResource(R.string.register_email_institutional_required)
                } else if (state.emailError) {
                    stringResource(R.string.login_email_invalid)
                } else {
                    null
                }
            )
            PasswordField(
                value = state.password,
                onValueChange = viewModel::onPasswordChange,
                visible = state.passwordVisible,
                onToggleVisibility = viewModel::togglePasswordVisibility,
                enabled = !busy,
                isError = state.passwordError,
                errorText = if (state.passwordError) {
                    stringResource(R.string.register_weak_password)
                } else {
                    null
                }
            )
            PasswordField(
                value = state.confirmPassword,
                onValueChange = viewModel::onConfirmPasswordChange,
                visible = state.confirmPasswordVisible,
                onToggleVisibility = viewModel::toggleConfirmPasswordVisibility,
                label = stringResource(R.string.confirm_password_label),
                enabled = !busy,
                isError = state.confirmPasswordError,
                errorText = if (state.confirmPasswordError) {
                    stringResource(R.string.register_password_mismatch)
                } else {
                    null
                },
                imeAction = ImeAction.Done
            )
            PasswordRequirements(validation = state.validation)
            if (state.passwordsUnavailable) {
                SecondaryTextButton(
                    text = stringResource(R.string.action_retry),
                    onClick = viewModel::retryCommonPasswords
                )
            }

            if (isCompany) {
                CompanyFields(state = state, viewModel = viewModel, enabled = !busy)
            } else if (!isDriver) {
                SectionLabel(stringResource(R.string.register_benefit_title))
                ChoiceRow(
                    options = listOf(
                        BenefitType.REGULAR to stringResource(R.string.register_benefit_regular),
                        BenefitType.UNIVERSITY to stringResource(R.string.register_benefit_university)
                    ),
                    selected = state.benefitType,
                    enabled = !busy,
                    onSelect = viewModel::onBenefitTypeChange
                )

                if (state.benefitType == BenefitType.UNIVERSITY) {
                    Text(
                        text = stringResource(R.string.register_benefit_university_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextGray
                    )
                    TextField(
                        value = state.universityCardCode,
                        onValueChange = viewModel::onUniversityCardCodeChange,
                        label = stringResource(R.string.register_card_code),
                        placeholder = stringResource(R.string.field_dni_hint),
                        enabled = !busy,
                        error = if (state.universityCardError) {
                            stringResource(R.string.register_card_code_required)
                        } else {
                            null
                        },
                        keyboardType = KeyboardType.Text
                    )
                    state.cardExpiryDate?.let { expiry ->
                        Text(
                            text = "${stringResource(R.string.register_card_expiry)}: $expiry",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextGray
                        )
                    }
                    OutlinedButton(
                        onClick = { captureTarget = ScanDocument.UNIVERSITY_CARD },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        enabled = !busy
                    ) {
                        Text(stringResource(R.string.register_scan_carne))
                    }
                    if (state.cardExpired) {
                        InlineMessage(
                            text = stringResource(R.string.register_card_expired),
                            tone = MessageTone.WARNING,
                            modifier = Modifier.testTag("card_expired_message")
                        )
                    }
                    if (state.universityCardError) {
                        ErrorText(stringResource(R.string.register_card_code_required))
                    }
                }
            } else {
                OperatorFields(state = state, viewModel = viewModel, enabled = !busy)
            }

            if (errorText != null) {
                InlineMessage(text = errorText, tone = MessageTone.ERROR)
            }
                val universityRequired = !isDriver && !isCompany && state.benefitType == BenefitType.UNIVERSITY
                PrimaryButton(
                text = stringResource(R.string.register_submit),
                onClick = viewModel::submit,
                modifier = Modifier.testTag("register_submit"),
                enabled = state.canSubmit && (!universityRequired || state.universityCardCode.isNotBlank()),
                loading = state.isSubmitting
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.register_have_account),
                style = MaterialTheme.typography.bodyMedium
            )
            TextButton(onClick = onLogin, enabled = !busy) {
                Text(stringResource(R.string.register_go_login))
            }
        }
    }
}

@Composable
private fun OperatorFields(
    state: RegisterUiState,
    viewModel: RegisterViewModel,
    enabled: Boolean
) {
    var categoryExpanded by remember { mutableStateOf(false) }

    TextField(
        value = state.plate,
        onValueChange = viewModel::onPlateChange,
        label = stringResource(R.string.field_plate),
        placeholder = stringResource(R.string.field_plate_hint),
        enabled = enabled,
        error = if (state.plateError) stringResource(R.string.operator_plate_invalid) else null,
        keyboardType = KeyboardType.Text
    )

    SectionLabel(stringResource(R.string.register_route_title))
    ChoiceRow(
        options = RegistrationConfig.routes.map { it.id to it.id },
        selected = state.assignedRouteId.takeIf { it.isNotEmpty() },
        enabled = enabled,
        onSelect = viewModel::onRouteChange
    )
    RegistrationConfig.routeById(state.assignedRouteId)?.let { route ->
        Text(text = route.name, style = MaterialTheme.typography.bodySmall, color = TextGray)
    }
    if (state.routeError) {
        ErrorText(stringResource(R.string.register_route_required))
    }

    TextField(
        value = state.licenseNumber,
        onValueChange = viewModel::onLicenseNumberChange,
        label = stringResource(R.string.field_license_number),
        placeholder = stringResource(R.string.field_license_number_hint),
        enabled = enabled,
        error = if (state.licenseNumberError) {
            stringResource(R.string.operator_license_invalid)
        } else {
            null
        },
        keyboardType = KeyboardType.Text
    )

    TextField(
        value = state.companyRuc,
        onValueChange = viewModel::onCompanyRucChange,
        label = stringResource(R.string.field_company_ruc_optional),
        placeholder = stringResource(R.string.field_ruc_hint),
        enabled = enabled,
        error = if (state.companyRucError) stringResource(R.string.register_company_ruc_unknown) else null,
        keyboardType = KeyboardType.Number
    )

    SectionLabel(stringResource(R.string.field_license_class))
    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { categoryExpanded = true },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            enabled = enabled
        ) {
            Text(
                text = state.licenseCategory.ifBlank {
                    stringResource(R.string.operator_license_class_placeholder)
                }
            )
        }
        DropdownMenu(
            expanded = categoryExpanded,
            onDismissRequest = { categoryExpanded = false }
        ) {
            RegistrationConfig.licenseCategories.forEach { category ->
                DropdownMenuItem(
                    text = { Text(category) },
                    onClick = {
                        viewModel.onLicenseCategoryChange(category)
                        categoryExpanded = false
                    }
                )
            }
        }
    }
    if (state.licenseBlocked) {
        InlineMessage(
            text = stringResource(R.string.register_license_blocked),
            tone = MessageTone.ERROR,
            modifier = Modifier.testTag("license_blocked_message")
        )
    } else if (state.licenseCategoryError) {
        ErrorText(stringResource(R.string.register_license_category_required))
    }
}

@Composable
private fun CompanyFields(
    state: RegisterUiState,
    viewModel: RegisterViewModel,
    enabled: Boolean
) {
    TextField(
        value = state.businessName,
        onValueChange = viewModel::onBusinessNameChange,
        label = stringResource(R.string.field_business_name),
        enabled = enabled,
        error = if (state.businessNameError) stringResource(R.string.register_business_name_invalid) else null,
        keyboardType = KeyboardType.Text
    )
    TextField(
        value = state.ruc,
        onValueChange = viewModel::onRucChange,
        label = stringResource(R.string.field_ruc),
        placeholder = stringResource(R.string.field_ruc_hint),
        enabled = enabled,
        error = if (state.rucError) stringResource(R.string.register_ruc_invalid) else null,
        keyboardType = KeyboardType.Number
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold,
        color = TextPrimary
    )
}

@Composable
private fun ErrorText(text: String) {
    Text(text = text, color = ErrorRed, style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun TextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    enabled: Boolean,
    error: String?,
    keyboardType: KeyboardType,
    placeholder: String? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        singleLine = true,
        enabled = enabled,
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Next)
    )
}

@Composable
private fun <T> ChoiceRow(
    options: List<Pair<T, String>>,
    selected: T?,
    enabled: Boolean,
    onSelect: (T) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { (value, label) ->
            FilterChip(
                selected = value == selected,
                onClick = { onSelect(value) },
                label = { Text(label) },
                enabled = enabled,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
            )
        }
    }
}
