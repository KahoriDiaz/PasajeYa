package com.pasajeya.app.feature.wallet.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pasajeya.app.R
import com.pasajeya.app.core.theme.BorderLight
import com.pasajeya.app.core.theme.PrimaryBlue
import com.pasajeya.app.core.theme.ReviewAmber
import com.pasajeya.app.core.theme.SuccessGreen
import com.pasajeya.app.core.theme.TextGray
import com.pasajeya.app.core.theme.TextPrimary
import com.pasajeya.app.core.ui.AuthScreen
import com.pasajeya.app.core.ui.InlineMessage
import com.pasajeya.app.core.ui.MessageTone
import com.pasajeya.app.core.util.formatCentsInt
import com.pasajeya.app.feature.history.domain.HistoryDateFormatter
import com.pasajeya.app.feature.wallet.domain.DemoWallet
import com.pasajeya.app.feature.wallet.domain.WalletError
import com.pasajeya.app.feature.wallet.domain.WalletLogic
import com.pasajeya.app.feature.wallet.domain.WalletTransaction
import com.pasajeya.app.feature.wallet.domain.WalletTransactionType

@Composable
fun WalletScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WalletViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    AuthScreen(
        title = stringResource(R.string.wallet_title),
        onBack = onBack,
        modifier = modifier.testTag("wallet_screen")
    ) {
        // Aviso obligatorio: el saldo no es dinero real.
        InlineMessage(
            text = stringResource(R.string.wallet_demo_notice),
            tone = MessageTone.WARNING
        )

        when {
            !state.hasAccount -> InlineMessage(
                text = stringResource(R.string.wallet_no_account),
                tone = MessageTone.ERROR
            )
            state.isLoading -> CircularProgressIndicator(
                modifier = Modifier
                    .size(32.dp)
                    .align(Alignment.CenterHorizontally)
            )
            else -> {
                if (state.loadFailed) {
                    InlineMessage(
                        text = stringResource(R.string.wallet_load_failed),
                        tone = MessageTone.ERROR
                    )
                }
                state.error?.let {
                    InlineMessage(text = walletErrorText(it), tone = MessageTone.ERROR)
                }
                BalanceCard(state.wallet)
                RechargeCard(onRecharge = viewModel::recharge)
                TripPaymentCard(
                    wallet = state.wallet,
                    lastTripCents = state.lastTripCents,
                    lastTripRouteName = state.lastTripRouteName,
                    onHold = viewModel::holdMaxFare,
                    onCharge = viewModel::chargeLastTrip,
                    onRelease = viewModel::releaseHold
                )
                MovementsCard(state.wallet.transactions)
            }
        }
    }
}

@Composable
private fun WalletCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, BorderLight)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            content()
        }
    }
}

@Composable
private fun BalanceCard(wallet: DemoWallet) {
    WalletCard {
        Text(
            text = stringResource(R.string.wallet_balance),
            style = MaterialTheme.typography.bodyMedium,
            color = TextGray
        )
        Text(
            text = formatCentsInt(wallet.availableCents),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = PrimaryBlue,
            modifier = Modifier.testTag("wallet_balance")
        )
        if (wallet.hasActiveHold) {
            Text(
                text = stringResource(
                    R.string.wallet_hold_label,
                    formatCentsInt(wallet.holdCents)
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = ReviewAmber,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun RechargeCard(onRecharge: (Int) -> Unit) {
    WalletCard {
        Text(
            text = stringResource(R.string.wallet_recharge_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            WalletLogic.RECHARGE_OPTIONS_CENTS.forEach { cents ->
                OutlinedButton(
                    onClick = { onRecharge(cents) },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("wallet_recharge_$cents"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        text = stringResource(
                            R.string.wallet_recharge_option,
                            formatCentsInt(cents)
                        ),
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun TripPaymentCard(
    wallet: DemoWallet,
    lastTripCents: Int?,
    lastTripRouteName: String?,
    onHold: () -> Unit,
    onCharge: () -> Unit,
    onRelease: () -> Unit
) {
    WalletCard {
        Text(
            text = stringResource(R.string.wallet_simulate_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary
        )
        if (wallet.hasActiveHold) {
            Text(
                text = stringResource(R.string.wallet_hold_active),
                style = MaterialTheme.typography.bodyMedium,
                color = TextGray
            )
        }
        Button(
            onClick = onHold,
            enabled = !wallet.hasActiveHold,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .testTag("wallet_hold"),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text(
                stringResource(
                    R.string.wallet_hold_action,
                    formatCentsInt(
                        com.pasajeya.app.feature.fare.domain.FareEstimator.SAFETY_CEILING_CENTS
                    )
                )
            )
        }
        if (lastTripCents == null) {
            Text(
                text = stringResource(R.string.wallet_charge_no_trip),
                style = MaterialTheme.typography.bodySmall,
                color = TextGray
            )
        } else if (lastTripRouteName != null) {
            Text(
                text = lastTripRouteName,
                style = MaterialTheme.typography.bodySmall,
                color = TextGray
            )
        }
        Button(
            onClick = onCharge,
            enabled = wallet.hasActiveHold && lastTripCents != null,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .testTag("wallet_charge"),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text(
                stringResource(
                    R.string.wallet_charge_action,
                    formatCentsInt(lastTripCents ?: 0)
                )
            )
        }
        OutlinedButton(
            onClick = onRelease,
            enabled = wallet.hasActiveHold,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .testTag("wallet_release"),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text(stringResource(R.string.wallet_release_action))
        }
    }
}

@Composable
private fun MovementsCard(transactions: List<WalletTransaction>) {
    WalletCard {
        Text(
            text = stringResource(R.string.wallet_movements_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary
        )
        if (transactions.isEmpty()) {
            Text(
                text = stringResource(R.string.wallet_movements_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = TextGray
            )
        } else {
            transactions.forEach { tx -> MovementRow(tx) }
        }
    }
}

@Composable
private fun MovementRow(tx: WalletTransaction) {
    val (label, sign, color) = when (tx.type) {
        WalletTransactionType.RECHARGE ->
            Triple(stringResource(R.string.wallet_kind_recharge), "+", SuccessGreen)
        WalletTransactionType.HOLD ->
            Triple(stringResource(R.string.wallet_kind_hold), "", ReviewAmber)
        WalletTransactionType.RELEASE ->
            Triple(stringResource(R.string.wallet_kind_release), "", TextGray)
        WalletTransactionType.CHARGE ->
            Triple(stringResource(R.string.wallet_kind_charge), "-", TextPrimary)
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(text = label, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
            Text(
                text = HistoryDateFormatter.format(tx.timeMillis),
                style = MaterialTheme.typography.bodySmall,
                color = TextGray
            )
        }
        Text(
            text = sign + formatCentsInt(tx.amountCents),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = color
        )
    }
}

@Composable
private fun walletErrorText(error: WalletError): String = stringResource(
    when (error) {
        WalletError.INVALID_AMOUNT -> R.string.wallet_error_invalid_amount
        WalletError.LIMIT_EXCEEDED -> R.string.wallet_error_limit
        WalletError.INSUFFICIENT_FUNDS -> R.string.wallet_error_funds
        WalletError.HOLD_ACTIVE -> R.string.wallet_error_hold_active
        WalletError.NO_HOLD -> R.string.wallet_error_no_hold
    }
)
