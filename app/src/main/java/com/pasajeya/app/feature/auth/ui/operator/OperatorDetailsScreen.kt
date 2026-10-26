package com.pasajeya.app.feature.auth.ui.operator

import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pasajeya.app.R
import com.pasajeya.app.core.theme.ErrorRed
import com.pasajeya.app.core.theme.TextGray
import com.pasajeya.app.core.theme.TextPrimary
import com.pasajeya.app.core.ui.AuthScreen
import com.pasajeya.app.core.ui.FormCard
import com.pasajeya.app.core.ui.InlineMessage
import com.pasajeya.app.core.ui.MessageTone
import com.pasajeya.app.core.ui.PrimaryButton
import com.pasajeya.app.core.ui.ScreenHeading
import com.pasajeya.app.core.ui.SecondaryTextButton
import com.pasajeya.app.feature.auth.domain.model.AuthDestination
import com.pasajeya.app.feature.auth.domain.model.RouteOption
import com.pasajeya.app.feature.auth.ui.accountFailureMessage
import com.pasajeya.app.feature.auth.ui.common.SessionActionsViewModel

@Composable
fun OperatorDetailsScreen(
    onNavigate: (AuthDestination) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OperatorDetailsViewModel = hiltViewModel(),
    sessionActionsViewModel: SessionActionsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.events.collect(onNavigate)
    }
    LaunchedEffect(sessionActionsViewModel) {
        sessionActionsViewModel.events.collect(onNavigate)
    }

    var licenseClassExpanded by remember { mutableStateOf(false) }
    val routesReady = state.routes is RoutesUiState.Ready

    AuthScreen(
        title = stringResource(R.string.operator_details_title),
        modifier = modifier.testTag("operator_details_screen"),
        actions = {
            TextButton(onClick = sessionActionsViewModel::signOut) {
                Text(
                    text = stringResource(R.string.action_logout),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    ) {
        ScreenHeading(
            title = stringResource(R.string.operator_details_title),
            subtitle = stringResource(R.string.operator_details_subtitle)
        )

        FormCard {
            OutlinedTextField(
                value = state.licenseNumber,
                onValueChange = viewModel::onLicenseNumberChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.field_license_number)) },
                placeholder = { Text(stringResource(R.string.field_license_number_hint)) },
                singleLine = true,
                enabled = !state.isSubmitting,
                isError = state.licenseNumberError,
                supportingText = if (state.licenseNumberError) {
                    { Text(stringResource(R.string.operator_license_invalid)) }
                } else {
                    null
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next
                )
            )

            Column {
                Text(
                    text = stringResource(R.string.field_license_class),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextGray
                )
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { licenseClassExpanded = true },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.isSubmitting
                    ) {
                        Text(
                            text = state.licenseClass.ifBlank {
                                stringResource(R.string.operator_license_class_placeholder)
                            }
                        )
                    }
                    DropdownMenu(
                        expanded = licenseClassExpanded,
                        onDismissRequest = { licenseClassExpanded = false }
                    ) {
                        viewModel.licenseClasses.forEach { licenseClass ->
                            DropdownMenuItem(
                                text = { Text(licenseClass) },
                                onClick = {
                                    viewModel.onLicenseClassChange(licenseClass)
                                    licenseClassExpanded = false
                                }
                            )
                        }
                    }
                }
                if (state.licenseClassError) {
                    Text(
                        text = stringResource(R.string.operator_license_class_invalid),
                        color = ErrorRed,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            OutlinedTextField(
                value = state.plate,
                onValueChange = viewModel::onPlateChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.field_plate)) },
                placeholder = { Text(stringResource(R.string.field_plate_hint)) },
                singleLine = true,
                enabled = !state.isSubmitting,
                isError = state.plateError,
                supportingText = if (state.plateError) {
                    { Text(stringResource(R.string.operator_plate_invalid)) }
                } else {
                    null
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Done
                )
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(R.string.operator_routes_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Text(
                text = stringResource(R.string.operator_routes_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = TextGray
            )
        }

        when (val routes = state.routes) {
            RoutesUiState.Loading -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp
                )
                Text(stringResource(R.string.loading))
            }
            RoutesUiState.Empty -> InlineMessage(
                text = stringResource(R.string.operator_routes_empty),
                tone = MessageTone.INFO
            )
            is RoutesUiState.Failed -> {
                InlineMessage(
                    text = stringResource(R.string.operator_routes_error),
                    tone = MessageTone.ERROR
                )
                SecondaryTextButton(
                    text = stringResource(R.string.action_retry),
                    onClick = viewModel::loadRoutes
                )
            }
            is RoutesUiState.Ready -> FormCard {
                routes.routes.forEachIndexed { index, route ->
                    RouteRow(
                        route = route,
                        selected = route.id in state.selectedRouteIds,
                        enabled = !state.isSubmitting,
                        onToggle = { viewModel.toggleRoute(route.id) }
                    )
                    if (index != routes.routes.lastIndex) {
                        HorizontalDivider()
                    }
                }
            }
        }

        if (state.routesError) {
            Text(
                text = stringResource(R.string.operator_routes_required),
                color = ErrorRed,
                style = MaterialTheme.typography.bodySmall
            )
        }
        state.accountFailure?.let { failure ->
            InlineMessage(
                text = accountFailureMessage(failure),
                tone = MessageTone.ERROR
            )
        }

        PrimaryButton(
            text = stringResource(R.string.operator_submit),
            onClick = viewModel::submit,
            enabled = routesReady,
            loading = state.isSubmitting
        )
    }
}

@Composable
private fun RouteRow(
    route: RouteOption,
    selected: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = selected,
                enabled = enabled,
                role = Role.Checkbox,
                onValueChange = { onToggle() }
            )
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = selected,
            onCheckedChange = null,
            enabled = enabled
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = route.name,
                style = MaterialTheme.typography.bodyLarge,
                color = TextPrimary
            )
            Text(
                text = stringResource(R.string.operator_route_stops, route.stopsCount),
                style = MaterialTheme.typography.bodySmall,
                color = TextGray
            )
        }
    }
}

