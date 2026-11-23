package com.pasajeya.app.feature.auth.ui.company

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pasajeya.app.R
import com.pasajeya.app.core.theme.TextGray
import com.pasajeya.app.core.ui.AuthScreen
import com.pasajeya.app.core.ui.FormCard
import com.pasajeya.app.core.ui.InlineMessage
import com.pasajeya.app.core.ui.MessageTone
import com.pasajeya.app.core.ui.PrimaryButton
import com.pasajeya.app.core.ui.ScreenHeading
import com.pasajeya.app.feature.auth.domain.model.AuthDestination
import com.pasajeya.app.feature.auth.ui.accountFailureMessage

@Composable
fun CompanyDetailsScreen(
    onNavigate: (AuthDestination) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CompanyDetailsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.events.collect(onNavigate)
    }

    AuthScreen(
        title = stringResource(R.string.company_details_title),
        modifier = modifier.testTag("company_details_screen")
    ) {
        ScreenHeading(
            title = stringResource(R.string.company_details_title),
            subtitle = stringResource(R.string.company_details_subtitle)
        )

        state.accountFailure?.let { failure ->
            InlineMessage(text = accountFailureMessage(failure), tone = MessageTone.ERROR)
        }

        FormCard {
            val nameError = if (state.businessNameError) {
                stringResource(R.string.register_business_name_invalid)
            } else {
                null
            }
            OutlinedTextField(
                value = state.businessName,
                onValueChange = viewModel::onBusinessNameChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.field_business_name)) },
                singleLine = true,
                enabled = !state.isSubmitting,
                isError = nameError != null,
                supportingText = nameError?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
            )

            val rucError = if (state.rucError) {
                stringResource(R.string.register_ruc_invalid)
            } else {
                null
            }
            OutlinedTextField(
                value = state.ruc,
                onValueChange = viewModel::onRucChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.field_ruc)) },
                singleLine = true,
                enabled = !state.isSubmitting,
                isError = rucError != null,
                supportingText = rucError?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done
                )
            )

            Text(
                text = stringResource(R.string.company_details_note),
                style = MaterialTheme.typography.bodySmall,
                color = TextGray
            )
        }

        PrimaryButton(
            text = stringResource(R.string.company_details_action),
            onClick = viewModel::submit,
            loading = state.isSubmitting,
            enabled = !state.isSubmitting
        )
    }
}
