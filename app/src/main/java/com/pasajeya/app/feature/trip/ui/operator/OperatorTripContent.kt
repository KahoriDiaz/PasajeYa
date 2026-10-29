package com.pasajeya.app.feature.trip.ui.operator

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pasajeya.app.R
import com.pasajeya.app.core.theme.BorderLight
import com.pasajeya.app.core.theme.ErrorRed
import com.pasajeya.app.core.theme.PrimaryBlue
import com.pasajeya.app.core.theme.ReviewAmber
import com.pasajeya.app.core.theme.SuccessGreen
import com.pasajeya.app.core.theme.TextGray
import com.pasajeya.app.core.theme.TextPrimary
import com.pasajeya.app.core.ui.AuthScreen
import com.pasajeya.app.core.ui.InlineMessage
import com.pasajeya.app.core.ui.MessageTone
import com.pasajeya.app.core.ui.PrimaryButton
import com.pasajeya.app.core.ui.ScreenHeading
import com.pasajeya.app.feature.auth.domain.model.RouteOption
import com.pasajeya.app.feature.history.ui.OperatorShiftSummaryCard
import com.pasajeya.app.feature.history.ui.OperatorShiftSummaryViewModel
import com.pasajeya.app.feature.trip.domain.model.Shift
import com.pasajeya.app.feature.trip.domain.model.TapInResult
import com.pasajeya.app.feature.trip.domain.model.Trip
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun OperatorTripContent(
    displayName: String,
    email: String,
    profileLoading: Boolean,
    profileError: Boolean,
    onLogout: () -> Unit,
    viewModel: OperatorTripViewModel,
    modifier: Modifier = Modifier,
    summaryViewModel: OperatorShiftSummaryViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val summaryState by summaryViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(state.shift?.id, state.openTrips.size) {
        summaryViewModel.refresh()
    }
    var pendingStart by rememberSaveable { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val fine = result[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarse = result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (!fine && !coarse) {
            pendingStart = false
            viewModel.onLocationPermissionDenied()
            return@rememberLauncherForActivityResult
        }
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            result[Manifest.permission.POST_NOTIFICATIONS] != true
        ) {
            viewModel.onNotificationsPermissionDenied()
        }
        if (pendingStart) {
            pendingStart = false
            viewModel.startShift()
        }
    }

    fun startWithPermissions() {
        val permissions = buildList {
            val fine = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            val coarse = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            if (!fine && !coarse) {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
                add(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
            if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (permissions.isEmpty()) {
            viewModel.startShift()
        } else {
            pendingStart = true
            permissionLauncher.launch(permissions.toTypedArray())
        }
    }

    AuthScreen(
        title = stringResource(R.string.app_name),
        modifier = modifier.testTag("operator_home_screen"),
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

        val notice = state.notice
        if (notice != null) {
            InlineMessage(
                text = noticeText(notice),
                tone = noticeTone(notice.kind)
            )
        }

        state.voiceWarning?.let { warning ->
            VoiceWarningCard(
                warning = warning,
                onInstall = {
                    runCatching {
                        context.startActivity(
                            Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)
                        )
                    }.onFailure {
                        Toast.makeText(
                            context,
                            R.string.operator_voice_install_error,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            )
        }

        state.lastTapIn?.let { tapIn ->
            TapInAnnouncementCard(tapIn)
        }

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
                    text = stringResource(R.string.operator_loading),
                    color = TextGray
                )
            }
        }

        if (state.loadFailed && !state.isLoading) {
            PrimaryButton(
                text = stringResource(R.string.operator_action_retry),
                onClick = viewModel::retry
            )
        }

        val operatorContext = state.context
        if (operatorContext != null && operatorContext.status != "verified") {
            InlineMessage(
                text = stringResource(R.string.operator_shift_status_unverified),
                tone = MessageTone.WARNING
            )
        }

        val shift = state.shift
        if (shift == null) {
            StartShiftCard(
                plate = operatorContext?.plate.orEmpty(),
                routes = state.routes,
                selectedRouteId = state.selectedRouteId,
                isStarting = state.isStartingShift,
                onSelectRoute = viewModel::selectRoute,
                onStart = ::startWithPermissions
            )
        } else {
            ActiveShiftCard(
                shift = shift,
                routeName = state.routeName
                    ?: state.routes.firstOrNull { it.id == shift.routeId }?.name
                    ?: shift.routeId,
                onScan = viewModel::openScanner,
                onEndShift = viewModel::requestEndShift,
                isEnding = state.isEndingShift
            )
            BatteryCard()
            OpenTripsCard(
                trips = state.openTrips,
                tapOutTripId = state.tapOutTripId,
                onTapOut = viewModel::requestTapOut
            )
        }

        OperatorShiftSummaryCard(
            state = summaryState,
            onRetry = summaryViewModel::refresh
        )

        VoiceSettingsCard(
            enabled = state.voiceEnabled,
            onEnabledChange = viewModel::setVoiceEnabled,
            onTest = viewModel::testVoice
        )
    }

    if (state.endShiftConfirm) {
        AlertDialog(
            onDismissRequest = viewModel::dismissEndShift,
            title = { Text(stringResource(R.string.operator_end_confirm_title)) },
            text = { Text(stringResource(R.string.operator_end_confirm_message)) },
            confirmButton = {
                TextButton(onClick = viewModel::confirmEndShift) {
                    Text(stringResource(R.string.operator_end_shift))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissEndShift) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    state.tapOutConfirmation?.let { trip ->
        AlertDialog(
            onDismissRequest = viewModel::dismissTapOut,
            title = { Text(stringResource(R.string.operator_tap_out)) },
            text = {
                Text(
                    stringResource(
                        R.string.operator_tap_out_confirm,
                        trip.passengerFirstName
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmTapOut) {
                    Text(stringResource(R.string.operator_tap_out))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissTapOut) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    if (state.scannerOpen) {
        QrScannerDialog(
            onQrDetected = viewModel::onQrScanned,
            onDismiss = viewModel::closeScanner,
            onPermissionDenied = viewModel::onCameraPermissionDenied
        )
    }
}

@Composable
private fun TapInAnnouncementCard(tapIn: TapInResult) {
    val age = tapIn.passengerAge
    val ageText = when (age) {
        1 -> stringResource(R.string.operator_tap_in_age_one)
        null -> stringResource(R.string.operator_age_unknown)
        else -> stringResource(R.string.operator_tap_in_age, age)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = SuccessGreen.copy(alpha = 0.12f)
        ),
        border = BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = stringResource(R.string.operator_tap_in_card_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = SuccessGreen
            )
            Text(
                text = tapIn.passengerFirstName,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Text(
                text = ageText,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
        }
    }
}

@Composable
private fun VoiceWarningCard(
    warning: VoiceWarning,
    onInstall: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = ReviewAmber.copy(alpha = 0.10f)
        ),
        border = BorderStroke(1.dp, ReviewAmber.copy(alpha = 0.40f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = when (warning) {
                    VoiceWarning.UNAVAILABLE ->
                        stringResource(R.string.operator_voice_unavailable)
                    VoiceWarning.INVALID_DATA ->
                        stringResource(R.string.operator_voice_invalid_data)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary
            )
            if (warning == VoiceWarning.UNAVAILABLE) {
                TextButton(
                    onClick = onInstall,
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text(stringResource(R.string.operator_voice_install))
                }
            }
        }
    }
}

@Composable
private fun VoiceSettingsCard(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onTest: () -> Unit
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
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = stringResource(R.string.operator_voice_settings_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Text(
                text = stringResource(R.string.operator_voice_settings_body),
                style = MaterialTheme.typography.bodyMedium,
                color = TextGray
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .toggleable(
                        value = enabled,
                        role = Role.Switch,
                        onValueChange = onEnabledChange
                    ),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.operator_voice_switch_label),
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextPrimary
                )
                Switch(
                    checked = enabled,
                    onCheckedChange = null
                )
            }
            OutlinedButton(
                onClick = onTest,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
            ) {
                Text(stringResource(R.string.operator_voice_test))
            }
        }
    }
}

@Composable
private fun StartShiftCard(
    plate: String,
    routes: List<RouteOption>,
    selectedRouteId: String?,
    isStarting: Boolean,
    onSelectRoute: (String) -> Unit,
    onStart: () -> Unit
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
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.operator_shift_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Text(
                text = stringResource(R.string.operator_shift_none),
                style = MaterialTheme.typography.bodyMedium,
                color = TextGray
            )
            if (plate.isNotBlank()) {
                Text(
                    text = stringResource(R.string.operator_shift_plate, plate),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
            }
            if (routes.isEmpty()) {
                Text(
                    text = stringResource(R.string.operator_route_unavailable),
                    style = MaterialTheme.typography.bodyMedium,
                    color = ErrorRed
                )
            } else {
                Text(
                    text = stringResource(R.string.operator_route_label),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                routes.forEach { route ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable { onSelectRoute(route.id) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = route.id == selectedRouteId,
                            onClick = { onSelectRoute(route.id) }
                        )
                        Text(
                            text = route.name,
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextPrimary
                        )
                    }
                }
            }
            PrimaryButton(
                text = stringResource(R.string.operator_start_shift),
                onClick = onStart,
                loading = isStarting,
                enabled = selectedRouteId != null
            )
        }
    }
}

@Composable
private fun ActiveShiftCard(
    shift: Shift,
    routeName: String,
    onScan: () -> Unit,
    onEndShift: () -> Unit,
    isEnding: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = SuccessGreen.copy(alpha = 0.08f)
        ),
        border = BorderStroke(1.dp, BorderLight)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = stringResource(R.string.operator_shift_active),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = SuccessGreen
            )
            Text(
                text = stringResource(R.string.passenger_trip_route, routeName),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Text(
                text = stringResource(
                    R.string.operator_shift_plate,
                    shift.plate
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = TextGray
            )
            shift.startedAtMillis?.let { startedAt ->
                Text(
                    text = stringResource(
                        R.string.passenger_trip_started,
                        formatOperatorTime(startedAt)
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextGray
                )
            }
            Button(
                onClick = onScan,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
            ) {
                Text(stringResource(R.string.operator_scan_qr))
            }
            OutlinedButton(
                onClick = onEndShift,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
                enabled = !isEnding
            ) {
                Text(
                    text = stringResource(R.string.operator_end_shift),
                    color = ErrorRed
                )
            }
        }
    }
}

@Composable
private fun BatteryCard() {
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = ReviewAmber.copy(alpha = 0.08f)
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
                text = stringResource(R.string.operator_battery_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Text(
                text = stringResource(R.string.operator_battery_body),
                style = MaterialTheme.typography.bodyMedium,
                color = TextGray
            )
            TextButton(
                onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:${context.packageName}")
                            )
                        )
                    }
                }
            ) {
                Text(stringResource(R.string.operator_battery_action))
            }
        }
    }
}

@Composable
private fun OpenTripsCard(
    trips: List<Trip>,
    tapOutTripId: String?,
    onTapOut: (Trip) -> Unit
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
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.operator_onboard_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Text(
                    text = stringResource(
                        R.string.operator_boarding_count,
                        trips.size
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = PrimaryBlue
                )
            }

            if (trips.isEmpty()) {
                Text(
                    text = stringResource(R.string.operator_onboard_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextGray
                )
            } else {
                trips.forEach { trip ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = trip.passengerFirstName,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                            Text(
                                text = trip.passengerAge?.let {
                                    stringResource(R.string.operator_age, it)
                                } ?: stringResource(R.string.operator_age_unknown),
                                style = MaterialTheme.typography.bodySmall,
                                color = TextGray
                            )
                            trip.startedAtMillis?.let { startedAt ->
                                Text(
                                    text = formatOperatorTime(startedAt),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextGray
                                )
                            }
                        }
                        if (tapOutTripId == trip.id) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            OutlinedButton(
                                onClick = { onTapOut(trip) },
                                enabled = tapOutTripId == null,
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                Text(stringResource(R.string.operator_tap_out))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun noticeText(notice: OperatorNotice): String = when (notice.kind) {
    OperatorNoticeKind.SHIFT_STARTED ->
        stringResource(R.string.operator_shift_started)
    OperatorNoticeKind.SHIFT_ENDED ->
        if (notice.reviewCount > 0) {
            stringResource(
                R.string.operator_shift_end_review,
                notice.reviewCount
            )
        } else {
            stringResource(R.string.operator_shift_ended)
        }
    OperatorNoticeKind.TAP_IN -> stringResource(
        R.string.operator_tap_in_success,
        notice.passengerName.orEmpty()
    )
    OperatorNoticeKind.TAP_OUT -> stringResource(
        R.string.operator_tap_out_success,
        notice.passengerName.orEmpty()
    )
    OperatorNoticeKind.TAP_OUT_REVIEW -> stringResource(
        R.string.operator_tap_out_review,
        notice.passengerName.orEmpty()
    )
    OperatorNoticeKind.ERROR -> notice.serverMessage
        ?: stringResource(R.string.operator_tap_in_error)
    OperatorNoticeKind.GENERIC_ERROR ->
        stringResource(R.string.operator_shift_error)
    OperatorNoticeKind.LOCATION_DENIED ->
        stringResource(R.string.operator_location_denied)
    OperatorNoticeKind.NOTIFICATIONS_DENIED ->
        stringResource(R.string.operator_notifications_denied)
    OperatorNoticeKind.CAMERA_DENIED ->
        stringResource(R.string.operator_camera_denied)
}

private fun noticeTone(kind: OperatorNoticeKind): MessageTone = when (kind) {
    OperatorNoticeKind.SHIFT_STARTED,
    OperatorNoticeKind.SHIFT_ENDED,
    OperatorNoticeKind.TAP_IN,
    OperatorNoticeKind.TAP_OUT -> MessageTone.SUCCESS
    OperatorNoticeKind.TAP_OUT_REVIEW,
    OperatorNoticeKind.NOTIFICATIONS_DENIED,
    OperatorNoticeKind.CAMERA_DENIED -> MessageTone.WARNING
    OperatorNoticeKind.ERROR,
    OperatorNoticeKind.GENERIC_ERROR,
    OperatorNoticeKind.LOCATION_DENIED -> MessageTone.ERROR
}

private fun formatOperatorTime(millis: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(millis))
