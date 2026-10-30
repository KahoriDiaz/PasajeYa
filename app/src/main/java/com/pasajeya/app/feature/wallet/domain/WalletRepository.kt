package com.pasajeya.app.feature.wallet.domain

import kotlinx.coroutines.flow.Flow

interface WalletRepository {
    fun observe(uid: String): Flow<DemoWallet>

    suspend fun recharge(uid: String, amountCents: Int): WalletResult

    suspend fun hold(uid: String, amountCents: Int): WalletResult

    suspend fun charge(uid: String, finalCents: Int): WalletResult

    suspend fun release(uid: String): WalletResult
}
