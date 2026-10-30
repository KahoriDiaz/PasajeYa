package com.pasajeya.app.feature.wallet.domain

import kotlin.math.min

/** Reglas puras de la billetera demo: recarga, retención (hold) y cobro (charge). */
object WalletLogic {
    const val MAX_BALANCE_CENTS = 20_000
    const val MAX_TRANSACTIONS = 50

    /** Montos de recarga demo permitidos, en céntimos. */
    val RECHARGE_OPTIONS_CENTS = listOf(500, 1_000, 2_000, 5_000)

    fun recharge(wallet: DemoWallet, amountCents: Int, nowMillis: Long): WalletResult {
        if (amountCents <= 0) return WalletResult.Failure(WalletError.INVALID_AMOUNT)
        if (wallet.balanceCents + amountCents > MAX_BALANCE_CENTS) {
            return WalletResult.Failure(WalletError.LIMIT_EXCEEDED)
        }
        return WalletResult.Success(
            wallet.copy(
                balanceCents = wallet.balanceCents + amountCents,
                transactions = wallet.record(
                    WalletTransaction(nowMillis, WalletTransactionType.RECHARGE, amountCents)
                )
            )
        )
    }

    /** Reserva [amountCents] del saldo disponible hasta que se cobre o se libere. */
    fun hold(wallet: DemoWallet, amountCents: Int, nowMillis: Long): WalletResult {
        if (amountCents <= 0) return WalletResult.Failure(WalletError.INVALID_AMOUNT)
        if (wallet.hasActiveHold) return WalletResult.Failure(WalletError.HOLD_ACTIVE)
        if (wallet.availableCents < amountCents) {
            return WalletResult.Failure(WalletError.INSUFFICIENT_FUNDS)
        }
        return WalletResult.Success(
            wallet.copy(
                holdCents = amountCents,
                transactions = wallet.record(
                    WalletTransaction(nowMillis, WalletTransactionType.HOLD, amountCents)
                )
            )
        )
    }

    /**
     * Cobra el importe final del viaje contra la retención. Nunca cobra más de lo retenido;
     * el resto vuelve a quedar disponible.
     */
    fun charge(wallet: DemoWallet, finalCents: Int, nowMillis: Long): WalletResult {
        if (finalCents < 0) return WalletResult.Failure(WalletError.INVALID_AMOUNT)
        if (!wallet.hasActiveHold) return WalletResult.Failure(WalletError.NO_HOLD)
        val charged = min(finalCents, wallet.holdCents)
        return WalletResult.Success(
            wallet.copy(
                balanceCents = wallet.balanceCents - charged,
                holdCents = 0,
                transactions = wallet.record(
                    WalletTransaction(nowMillis, WalletTransactionType.CHARGE, charged)
                )
            )
        )
    }

    fun release(wallet: DemoWallet, nowMillis: Long): WalletResult {
        if (!wallet.hasActiveHold) return WalletResult.Failure(WalletError.NO_HOLD)
        return WalletResult.Success(
            wallet.copy(
                holdCents = 0,
                transactions = wallet.record(
                    WalletTransaction(
                        nowMillis,
                        WalletTransactionType.RELEASE,
                        wallet.holdCents
                    )
                )
            )
        )
    }

    private fun DemoWallet.record(transaction: WalletTransaction): List<WalletTransaction> =
        (listOf(transaction) + transactions).take(MAX_TRANSACTIONS)
}
