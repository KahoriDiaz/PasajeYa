package com.pasajeya.app.feature.auth.ui.role

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.pasajeya.app.R
import com.pasajeya.app.core.theme.TextPrimary
import com.pasajeya.app.core.ui.AuthScreen
import com.pasajeya.app.core.ui.FormCard
import com.pasajeya.app.core.ui.PrimaryButton
import com.pasajeya.app.core.ui.ScreenHeading
import com.pasajeya.app.feature.auth.domain.model.AuthDestination
import com.pasajeya.app.feature.auth.ui.common.SessionActionsViewModel

@Composable
fun RoleSelectionScreen(
    onPassenger: () -> Unit,
    onOperator: () -> Unit,
    onNavigate: (AuthDestination) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SessionActionsViewModel = hiltViewModel()
) {
    LaunchedEffect(viewModel) {
        viewModel.events.collect(onNavigate)
    }

    AuthScreen(
        title = stringResource(R.string.role_title),
        modifier = modifier.testTag("role_selection_screen"),
        actions = {
            TextButton(onClick = viewModel::signOut) {
                Text(
                    text = stringResource(R.string.action_logout),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    ) {
        ScreenHeading(
            title = stringResource(R.string.role_title),
            subtitle = stringResource(R.string.role_subtitle)
        )

        RoleCard(
            title = stringResource(R.string.role_passenger_title),
            description = stringResource(R.string.role_passenger_description),
            onClick = onPassenger
        )
        RoleCard(
            title = stringResource(R.string.role_operator_title),
            description = stringResource(R.string.role_operator_description),
            onClick = onOperator
        )
    }
}

@Composable
private fun RoleCard(
    title: String,
    description: String,
    onClick: () -> Unit
) {
    FormCard {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium
        )
        PrimaryButton(
            text = stringResource(R.string.role_select),
            onClick = onClick
        )
    }
}
