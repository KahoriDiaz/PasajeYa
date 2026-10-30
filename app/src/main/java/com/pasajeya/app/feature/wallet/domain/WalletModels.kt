package com.pasajeya.app.feature.wallet.domain

enum class WalletTransactionType { RECHARGE, HOLD, RELEASE, CHARGE }

data class WalletTransaction(
    val timeMillis: Long,
    val type: WalletTransactionType,
    val amountCents: Int
)

/**
 * Billetera de DEMOSTRACIÓN. El saldo no es dinero real y vive solo en el dispositivo
 * (ver docs/LIMITACIONES.md).
 */
data class DemoWallet(
    val balanceCents: Int = 0,
    val holdCents: Int = 0,
    val transactions: List<WalletTransaction> = emptyList()
) {
    val availableCents: Int get() = balanceCents - holdCents
    val hasActiveHold: Boolean get() = holdCents > 0
}

enum class WalletError {
    INVALID_AMOUNT,
    LIMIT_EXCEEDED,
    INSUFFICIENT_FUNDS,
    HOLD_ACTIVE,
    NO_HOLD
}

sealed interface WalletResult {
    data class Success(val wallet: DemoWallet) : WalletResult
    data class Failure(val error: WalletError) : WalletResult
}
