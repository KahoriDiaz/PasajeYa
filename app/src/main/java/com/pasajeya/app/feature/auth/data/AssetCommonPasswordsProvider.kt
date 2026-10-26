package com.pasajeya.app.feature.auth.data

import android.content.Context
import com.pasajeya.app.feature.auth.domain.CommonPasswordsProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

@Singleton
class AssetCommonPasswordsProvider @Inject constructor(
    @ApplicationContext private val context: Context
) : CommonPasswordsProvider {
    @Volatile
    private var cached: Set<String>? = null

    override suspend fun get(): Set<String> {
        cached?.let { return it }

        return withContext(Dispatchers.IO) {
            cached ?: load().also { cached = it }
        }
    }

    private fun load(): Set<String> {
        val contents = context.assets.open(COMMON_PASSWORDS_ASSET).bufferedReader().use {
            it.readText()
        }
        val values = Json.decodeFromString<List<String>>(contents)
        check(values.size >= 100) {
            "La lista de contraseñas comunes está incompleta."
        }
        return values.mapTo(LinkedHashSet()) { it.lowercase(Locale.ROOT) }
    }

    private companion object {
        const val COMMON_PASSWORDS_ASSET = "common-passwords.json"
    }
}
