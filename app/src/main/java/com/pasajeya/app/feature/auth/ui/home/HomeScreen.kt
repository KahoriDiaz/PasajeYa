package com.pasajeya.app.feature.auth.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pasajeya.app.R
import com.pasajeya.app.core.theme.BorderLight
import com.pasajeya.app.core.theme.ReviewAmber
import com.pasajeya.app.core.theme.SuccessGreen
import com.pasajeya.app.core.theme.TextGray
import com.pasajeya.app.core.theme.TextPrimary
import com.pasajeya.app.core.ui.AuthScreen
import com.pasajeya.app.core.ui.InlineMessage
import com.pasajeya.app.core.ui.MessageTone
import com.pasajeya.app.core.ui.ScreenHeading
import com.pasajeya.app.feature.auth.domain.model.AuthDestination

@Composable
fun PassengerHomeScreen(
    onNavigate: (AuthDestination) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel()
) {
    HomeScreenContent(
        isOperator = false,
        viewModel = viewModel,
        onNavigate = onNavigate,
        modifier = modifier.testTag("passenger_home_screen")
    )
}

@Composable
fun OperatorHomeScreen(
    onNavigate: (AuthDestination) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel()
) {
    HomeScreenContent(
        isOperator = true,
        viewModel = viewModel,
        onNavigate = onNavigate,
        modifier = modifier.testTag("operator_home_screen")
    )
}

@Composable
private fun HomeScreenContent(
    isOperator: Boolean,
    viewModel: HomeViewModel,
    onNavigate: (AuthDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.events.collect(onNavigate)
    }

    AuthScreen(
        title = stringResource(R.string.app_name),
        modifier = modifier,
        actions = {
            TextButton(onClick = viewModel::logout) {
                Text(
                    text = stringResource(R.string.action_logout),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    ) {
        ScreenHeading(
            title = stringResource(R.string.home_greeting, state.displayName),
            subtitle = state.email
        )

        if (state.isLoading) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp
                )
                Text(
                    text = stringResource(R.string.home_loading_profile),
                    color = TextGray
                )
            }
        }

        if (state.hasError) {
            InlineMessage(
                text = stringResource(R.string.home_profile_error),
                tone = MessageTone.ERROR
            )
        }

        val accent = if (isOperator) ReviewAmber else SuccessGreen
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(
                containerColor = accent.copy(alpha = 0.08f)
            ),
            border = BorderStroke(1.dp, BorderLight)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(
                        if (isOperator) R.string.home_operator_title
                        else R.string.home_passenger_title
                    ),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = accent
                )
                Text(
                    text = stringResource(
                        if (isOperator) R.string.home_operator_body
                        else R.string.home_passenger_body
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextPrimary
                )
                if (isOperator) {
                    Text(
                        text = stringResource(R.string.home_operator_status),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = accent
                    )
                }
            }
        }
    }
}
