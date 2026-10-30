package com.pasajeya.app.feature.wallet

import com.pasajeya.app.feature.wallet.domain.DemoWallet
import com.pasajeya.app.feature.wallet.domain.WalletError
import com.pasajeya.app.feature.wallet.domain.WalletLogic
import com.pasajeya.app.feature.wallet.domain.WalletResult
import com.pasajeya.app.feature.wallet.domain.WalletTransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WalletLogicTest {

    private fun success(result: WalletResult): DemoWallet =
        (result as WalletResult.Success).wallet

    private fun failure(result: WalletResult): WalletError =
        (result as WalletResult.Failure).error

    @Test
    fun `recargar suma al saldo y registra el movimiento`() {
        val wallet = success(WalletLogic.recharge(DemoWallet(), 1_000, 1L))
        assertEquals(1_000, wallet.balanceCents)
        assertEquals(WalletTransactionType.RECHARGE, wallet.transactions.first().type)
    }

    @Test
    fun `recargar rechaza montos invalidos y el tope de saldo`() {
        assertEquals(WalletError.INVALID_AMOUNT, failure(WalletLogic.recharge(DemoWallet(), 0, 1L)))
        assertEquals(
            WalletError.LIMIT_EXCEEDED,
            failure(WalletLogic.recharge(DemoWallet(balanceCents = 19_900), 500, 1L))
        )
    }

    @Test
    fun `retener reserva el saldo disponible`() {
        val wallet = success(WalletLogic.hold(DemoWallet(balanceCents = 1_000), 330, 1L))
        assertEquals(1_000, wallet.balanceCents)
        assertEquals(330, wallet.holdCents)
        assertEquals(670, wallet.availableCents)
    }

    @Test
    fun `retener falla sin fondos o con otra retencion activa`() {
        assertEquals(
            WalletError.INSUFFICIENT_FUNDS,
            failure(WalletLogic.hold(DemoWallet(balanceCents = 100), 330, 1L))
        )
        assertEquals(
            WalletError.HOLD_ACTIVE,
            failure(WalletLogic.hold(DemoWallet(balanceCents = 1_000, holdCents = 330), 330, 1L))
        )
    }

    @Test
    fun `cobrar descuenta el importe final y libera el resto`() {
        val held = DemoWallet(balanceCents = 1_000, holdCents = 330)
        val wallet = success(WalletLogic.charge(held, 135, 1L))
        assertEquals(865, wallet.balanceCents)
        assertEquals(0, wallet.holdCents)
        assertEquals(865, wallet.availableCents)
        assertEquals(135, wallet.transactions.first().amountCents)
    }

    @Test
    fun `cobrar nunca supera lo retenido`() {
        val held = DemoWallet(balanceCents = 1_000, holdCents = 330)
        val wallet = success(WalletLogic.charge(held, 900, 1L))
        assertEquals(670, wallet.balanceCents)
    }

    @Test
    fun `cobrar sin retencion falla`() {
        assertEquals(
            WalletError.NO_HOLD,
            failure(WalletLogic.charge(DemoWallet(balanceCents = 1_000), 100, 1L))
        )
    }

    @Test
    fun `liberar devuelve el saldo disponible`() {
        val wallet = success(
            WalletLogic.release(DemoWallet(balanceCents = 1_000, holdCents = 330), 1L)
        )
        assertEquals(0, wallet.holdCents)
        assertEquals(1_000, wallet.availableCents)
        assertEquals(WalletTransactionType.RELEASE, wallet.transactions.first().type)
    }

    @Test
    fun `solo se conservan los ultimos movimientos`() {
        var wallet = DemoWallet()
        repeat(WalletLogic.MAX_TRANSACTIONS + 10) { index ->
            wallet = success(WalletLogic.recharge(wallet.copy(balanceCents = 0), 100, index.toLong()))
        }
        assertEquals(WalletLogic.MAX_TRANSACTIONS, wallet.transactions.size)
        assertTrue(wallet.transactions.first().timeMillis > wallet.transactions.last().timeMillis)
    }
}
