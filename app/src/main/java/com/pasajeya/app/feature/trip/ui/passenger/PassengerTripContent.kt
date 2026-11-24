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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import com.pasajeya.app.R
import com.pasajeya.app.core.theme.BorderLight
import com.pasajeya.app.core.theme.ErrorRed
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
import com.pasajeya.app.feature.profile.ui.ProfileUserIcon
import com.pasajeya.app.feature.trip.ui.operator.QrScannerDialog
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
    onBenefits: () -> Unit,
    onProfile: () -> Unit,
    viewModel: PassengerQrViewModel,
    modifier: Modifier = Modifier,
    bottomBar: @Composable () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val scope = rememberCoroutineScope()

    AuthScreen(
        title = stringResource(R.string.app_name),
        modifier = modifier.testTag("passenger_home_screen"),
        bottomBar = bottomBar,
        actions = { ProfileUserIcon(onProfileClick = onProfile) }
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
        LaunchedEffect(Unit) { viewModel.refreshEmailVerified() }

        if (profileError) {
            InlineMessage(
                text = stringResource(R.string.home_profile_error),
                tone = MessageTone.ERROR
            )
        }

        state.lastError?.let { error ->
            InlineMessage(text = error, tone = MessageTone.ERROR)
        }
        state.lastMessage?.let { message ->
            InlineMessage(text = message, tone = MessageTone.SUCCESS)
        }

        if (!state.emailVerified && state.trip == null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = ErrorRed.copy(alpha = 0.10f)
                ),
                border = BorderStroke(1.dp, ErrorRed.copy(alpha = 0.35f))
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.profile_email_unverified_title),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = ErrorRed
                    )
                    Text(
                        text = stringResource(R.string.profile_email_unverified_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextGray,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    TextButton(
                        onClick = { scope.launch { viewModel.resendVerification() } }
                    ) {
                        Text(
                            text = stringResource(R.string.profile_email_resend),
                            color = PrimaryBlue,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        PassengerScanCard(
            hasActiveTrip = state.trip != null,
            isProcessing = state.isProcessing,
            onScan = viewModel::openScanner
        )

        ActiveTripCard(
            trip = state.trip,
            routeName = state.routeName
        )

        state.lastFare?.let { fare ->
            FareReceiptCard(
                totalCents = fare.totalCents,
                officialFareCents = fare.officialFareCents,
                discountCents = fare.benefit?.discountCents ?: 0
            )
        }

        if (state.trip != null) {
            LiveTripCard()
        }

        // Lo demas vive en la barra de abajo; aqui solo queda lo que no es pestaña.
        OutlinedButton(
            onClick = onBenefits,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .testTag("open_benefits"),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text(text = stringResource(R.string.home_open_benefits))
        }
    }

    if (state.scannerOpen) {
        QrScannerDialog(
            onQrDetected = viewModel::onBusQrScanned,
            onDismiss = viewModel::closeScanner,
            onPermissionDenied = viewModel::closeScanner
        )
    }

}

@Composable
private fun PassengerScanCard(
    hasActiveTrip: Boolean,
    isProcessing: Boolean,
    onScan: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = PrimaryBlue.copy(alpha = 0.08f)
        ),
        border = BorderStroke(1.dp, BorderLight)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(
                    if (hasActiveTrip) {
                        R.string.passenger_scan_out_title
                    } else {
                        R.string.passenger_scan_in_title
                    }
                ),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Text(
                text = stringResource(
                    if (hasActiveTrip) {
                        R.string.passenger_scan_out_body
                    } else {
                        R.string.passenger_scan_in_body
                    }
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = TextGray,
                textAlign = TextAlign.Center
            )
            Button(
                onClick = onScan,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .testTag("passenger_scan_bus"),
                shape = RoundedCornerShape(14.dp),
                enabled = !isProcessing
            ) {
                if (isProcessing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                } else {
                    Text(
                        stringResource(
                            if (hasActiveTrip) {
                                R.string.passenger_scan_out_action
                            } else {
                                R.string.passenger_scan_in_action
                            }
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun FareReceiptCard(
    totalCents: Int,
    officialFareCents: Int,
    discountCents: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = SuccessGreen.copy(alpha = 0.10f)
        ),
        border = BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = stringResource(R.string.passenger_receipt_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Text(
                text = stringResource(
                    R.string.passenger_receipt_total,
                    formatSoles(totalCents)
                ),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = SuccessGreen
            )
            if (officialFareCents > 0) {
                Text(
                    text = stringResource(
                        R.string.passenger_receipt_official,
                        formatSoles(officialFareCents)
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextGray
                )
            }
            if (discountCents > 0) {
                Text(
                    text = stringResource(
                        R.string.passenger_receipt_discount,
                        formatSoles(discountCents)
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = SuccessGreen
                )
            }
        }
    }
}

private fun formatSoles(cents: Int): String =
    String.format(Locale.ROOT, "S/ %.2f", cents / 100.0)

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
