package com.pasajeya.app.feature.trip.ui.passenger

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pasajeya.app.R
import com.pasajeya.app.core.theme.BorderLight
import com.pasajeya.app.core.theme.PrimaryBlue
import com.pasajeya.app.core.theme.SuccessGreen
import com.pasajeya.app.core.theme.TextGray
import com.pasajeya.app.core.theme.TextPrimary
import com.pasajeya.app.core.ui.AuthScreen
import com.pasajeya.app.core.ui.InlineMessage
import com.pasajeya.app.core.ui.MessageTone
import com.pasajeya.app.core.ui.ScreenHeading
import com.pasajeya.app.feature.trip.domain.model.Trip
import com.pasajeya.app.feature.trip.ui.generateQrBitmap
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PassengerTripContent(
    displayName: String,
    email: String,
    profileLoading: Boolean,
    profileError: Boolean,
    onLogout: () -> Unit,
    onEstimate: () -> Unit,
    onHistory: () -> Unit,
    viewModel: PassengerQrViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    AuthScreen(
        title = stringResource(R.string.app_name),
        modifier = modifier.testTag("passenger_home_screen"),
        actions = {
            TextButton(onClick = onLogout) {
                Text(
                    text = stringResource(R.string.action_logout),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    ) {
        ScreenHeading(
            title = stringResource(R.string.home_greeting, displayName),
            subtitle = email
        )

        if (profileLoading) {
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
        if (profileError) {
            InlineMessage(
                text = stringResource(R.string.home_profile_error),
                tone = MessageTone.ERROR
            )
        }

        QrCard(
            token = state.token,
            secondsRemaining = state.secondsRemaining,
            isRefreshing = state.isRefreshing,
            isOffline = state.isOffline,
            onRetry = viewModel::retry
        )

        ActiveTripCard(
            trip = state.trip,
            routeName = state.routeName
        )

        Button(
            onClick = onEstimate,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text(text = "Estimar mi pasaje")
        }

        OutlinedButton(
            onClick = onHistory,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .testTag("open_history"),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text(text = stringResource(R.string.history_open))
        }
    }
}

@Composable
private fun QrCard(
    token: String?,
    secondsRemaining: Int,
    isRefreshing: Boolean,
    isOffline: Boolean,
    onRetry: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, BorderLight)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = stringResource(R.string.passenger_qr_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Text(
                text = stringResource(R.string.passenger_qr_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = TextGray,
                textAlign = TextAlign.Center
            )

            Box(
                modifier = Modifier
                    .size(248.dp)
                    .background(Color.White, RoundedCornerShape(16.dp))
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                when {
                    token != null -> {
                        val bitmap = remember(token) {
                            generateQrBitmap(token, size = 720)
                        }
                        Image(
                            bitmap = bitmap,
                            contentDescription = stringResource(
                                R.string.passenger_qr_title
                            ),
                            modifier = Modifier
                                .size(224.dp)
                                .testTag("passenger_qr")
                        )
                    }
                    isRefreshing -> CircularProgressIndicator(color = PrimaryBlue)
                    else -> Text(
                        text = stringResource(R.string.passenger_qr_error),
                        color = TextGray,
                        textAlign = TextAlign.Center
                    )
                }
            }

            if (token != null && !isOffline) {
                Text(
                    text = stringResource(
                        R.string.passenger_qr_renew,
                        secondsRemaining
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = PrimaryBlue
                )
            }
            if (isOffline) {
                InlineMessage(
                    text = stringResource(R.string.passenger_qr_offline),
                    tone = MessageTone.WARNING
                )
                if (token == null) {
                    TextButton(onClick = onRetry) {
                        Text(stringResource(R.string.operator_action_retry))
                    }
                }
            }
        }
    }
}

@Composable
private fun ActiveTripCard(trip: Trip?, routeName: String?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = if (trip != null) {
                SuccessGreen.copy(alpha = 0.08f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = BorderStroke(1.dp, BorderLight)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.passenger_trip_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            if (trip == null) {
                Text(
                    text = stringResource(R.string.passenger_trip_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextGray
                )
            } else {
                Text(
                    text = stringResource(
                        R.string.passenger_trip_route,
                        routeName ?: trip.routeId
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = SuccessGreen
                )
                trip.startedAtMillis?.let { startedAt ->
                    Text(
                        text = stringResource(
                            R.string.passenger_trip_started,
                            formatTime(startedAt)
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextGray
                    )
                }
                Text(
                    text = stringResource(R.string.operator_trip_waiting),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextGray
                )
            }
        }
    }
}

internal fun formatTime(millis: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(millis))
