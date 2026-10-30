package com.pasajeya.app.feature.auth.ui.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pasajeya.app.R
import com.pasajeya.app.core.theme.ErrorRed
import com.pasajeya.app.core.ui.AuthScreen
import com.pasajeya.app.core.ui.FormCard
import com.pasajeya.app.core.ui.InlineMessage
import com.pasajeya.app.core.ui.MessageTone
import com.pasajeya.app.core.ui.PrimaryButton
import com.pasajeya.app.core.ui.ScreenHeading
import com.pasajeya.app.feature.auth.domain.model.AuthDestination
import com.pasajeya.app.feature.auth.ui.accountFailureMessage

@Composable
fun PassengerProfileScreen(
    onBack: () -> Unit,
    onPrivacy: () -> Unit,
    onNavigate: (AuthDestination) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PassengerProfileViewModel = hiltViewModel()
) {
    PersonalProfileScreen(
        title = stringResource(R.string.profile_passenger_title),
        viewModel = viewModel,
        onBack = onBack,
        onPrivacy = onPrivacy,
        onNavigate = onNavigate,
        modifier = modifier.testTag("passenger_profile_screen")
    )
}

@Composable
fun OperatorPersonalProfileScreen(
    onBack: () -> Unit,
    onPrivacy: () -> Unit,
    onNavigate: (AuthDestination) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OperatorPersonalProfileViewModel = hiltViewModel()
) {
    PersonalProfileScreen(
        title = stringResource(R.string.profile_operator_title),
        viewModel = viewModel,
        onBack = onBack,
        onPrivacy = onPrivacy,
        onNavigate = onNavigate,
        modifier = modifier.testTag("operator_profile_screen")
    )
}

@Composable
private fun PersonalProfileScreen(
    title: String,
    viewModel: PersonalProfileViewModel,
    onBack: () -> Unit,
    onPrivacy: () -> Unit,
    onNavigate: (AuthDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val birthDateValue = remember(state.birthDate) {
        TextFieldValue(
            text = state.birthDate,
            selection = TextRange(state.birthDate.length)
        )
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect(onNavigate)
    }

    AuthScreen(
        title = title,
        modifier = modifier,
        onBack = onBack
    ) {
        ScreenHeading(
            title = title,
            subtitle = stringResource(R.string.profile_subtitle)
        )
        FormCard {
            OutlinedTextField(
                value = state.firstName,
                onValueChange = viewModel::onFirstNameChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.field_first_name)) },
                singleLine = true,
                enabled = !state.isSubmitting,
                isError = state.firstNameError,
                supportingText = if (state.firstNameError) {
                    { Text(stringResource(R.string.profile_name_invalid)) }
                } else {
                    null
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next
                )
            )
            OutlinedTextField(
                value = state.lastName,
                onValueChange = viewModel::onLastNameChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.field_last_name)) },
                singleLine = true,
                enabled = !state.isSubmitting,
                isError = state.lastNameError,
                supportingText = if (state.lastNameError) {
                    { Text(stringResource(R.string.profile_name_invalid)) }
                } else {
                    null
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next
                )
            )
            OutlinedTextField(
                value = state.dni,
                onValueChange = viewModel::onDniChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.field_dni)) },
                placeholder = { Text(stringResource(R.string.field_dni_hint)) },
                singleLine = true,
                enabled = !state.isSubmitting,
                isError = state.dniError,
                supportingText = if (state.dniError) {
                    { Text(stringResource(R.string.profile_dni_invalid)) }
                } else {
                    null
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Next
                )
            )
            OutlinedTextField(
                value = birthDateValue,
                onValueChange = { viewModel.onBirthDateChange(it.text) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.field_birth_date)) },
                placeholder = { Text(stringResource(R.string.field_birth_date_hint)) },
                singleLine = true,
                enabled = !state.isSubmitting,
                isError = state.birthDateError,
                supportingText = if (state.birthDateError) {
                    { Text(stringResource(R.string.profile_birth_invalid)) }
                } else {
                    null
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done
                )
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = state.consentAccepted,
                    onCheckedChange = viewModel::onConsentChange,
                    enabled = !state.isSubmitting
                )
                Text(
                    text = stringResource(R.string.profile_consent_label),
                    modifier = Modifier
                        .weight(1f)
                        .clickable(enabled = !state.isSubmitting) {
                            viewModel.onConsentChange(!state.consentAccepted)
                        },
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            if (state.consentError) {
                Text(
                    text = stringResource(R.string.profile_consent_required),
                    color = ErrorRed,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            TextButton(
                onClick = onPrivacy,
                enabled = !state.isSubmitting
            ) {
                Text(stringResource(R.string.profile_privacy_link))
            }
            state.accountFailure?.let { failure ->
                InlineMessage(
                    text = accountFailureMessage(failure),
                    tone = MessageTone.ERROR
                )
            }
            PrimaryButton(
                text = stringResource(R.string.profile_submit),
                onClick = viewModel::submit,
                loading = state.isSubmitting
            )
        }
    }
}
