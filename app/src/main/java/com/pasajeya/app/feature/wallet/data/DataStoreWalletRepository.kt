package com.pasajeya.app.feature.wallet.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.pasajeya.app.feature.wallet.domain.DemoWallet
import com.pasajeya.app.feature.wallet.domain.WalletError
import com.pasajeya.app.feature.wallet.domain.WalletLogic
import com.pasajeya.app.feature.wallet.domain.WalletRepository
import com.pasajeya.app.feature.wallet.domain.WalletResult
import com.pasajeya.app.feature.wallet.domain.WalletTransaction
import com.pasajeya.app.feature.wallet.domain.WalletTransactionType
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

private val Context.walletDataStore by preferencesDataStore(name = "demo_wallet")

/** Billetera demo persistida por cuenta en DataStore. No es dinero real ni viaja al servidor. */
@Singleton
class DataStoreWalletRepository @Inject constructor(
    @ApplicationContext private val context: Context
) : WalletRepository {

    override fun observe(uid: String): Flow<DemoWallet> =
        context.walletDataStore.data.map { it.readWallet(uid) }

    override suspend fun recharge(uid: String, amountCents: Int): WalletResult =
        mutate(uid) { wallet -> WalletLogic.recharge(wallet, amountCents, now()) }

    override suspend fun hold(uid: String, amountCents: Int): WalletResult =
        mutate(uid) { wallet -> WalletLogic.hold(wallet, amountCents, now()) }

    override suspend fun charge(uid: String, finalCents: Int): WalletResult =
        mutate(uid) { wallet -> WalletLogic.charge(wallet, finalCents, now()) }

    override suspend fun release(uid: String): WalletResult =
        mutate(uid) { wallet -> WalletLogic.release(wallet, now()) }

    private suspend fun mutate(
        uid: String,
        operation: (DemoWallet) -> WalletResult
    ): WalletResult {
        var outcome: WalletResult = WalletResult.Failure(WalletError.INVALID_AMOUNT)
        context.walletDataStore.edit { preferences ->
            val result = operation(preferences.readWallet(uid))
            outcome = result
            if (result is WalletResult.Success) {
                preferences[keyFor(uid)] = encode(result.wallet)
            }
        }
        return outcome
    }

    private fun now(): Long = System.currentTimeMillis()

    private fun Preferences.readWallet(uid: String): DemoWallet =
        this[keyFor(uid)]?.let(::decode) ?: DemoWallet()

    private fun keyFor(uid: String) = stringPreferencesKey("wallet_$uid")

    private fun encode(wallet: DemoWallet): String = JSONObject().apply {
        put("balance", wallet.balanceCents)
        put("hold", wallet.holdCents)
        put(
            "tx",
            JSONArray().also { array ->
                wallet.transactions.forEach { tx ->
                    array.put(
                        JSONObject()
                            .put("t", tx.timeMillis)
                            .put("k", tx.type.name)
                            .put("a", tx.amountCents)
                    )
                }
            }
        )
    }.toString()

    private fun decode(raw: String): DemoWallet = try {
        val json = JSONObject(raw)
        val array = json.optJSONArray("tx") ?: JSONArray()
        val transactions = (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val type = runCatching {
                WalletTransactionType.valueOf(item.getString("k"))
            }.getOrNull() ?: return@mapNotNull null
            WalletTransaction(
                timeMillis = item.getLong("t"),
                type = type,
                amountCents = item.getInt("a")
            )
        }
        DemoWallet(
            balanceCents = json.optInt("balance", 0),
            holdCents = json.optInt("hold", 0),
            transactions = transactions
        )
    } catch (_: JSONException) {
        DemoWallet()
    }
}
