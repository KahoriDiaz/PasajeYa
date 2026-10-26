package com.pasajeya.app.feature.auth.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

private val Context.authPreferences by preferencesDataStore(name = "auth_preferences")

@Singleton
class DeviceIdStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend fun getOrCreate(): String {
        val existing = context.authPreferences.data.first()[DEVICE_ID]
        if (existing != null) return existing

        return context.authPreferences.edit { preferences ->
            preferences[DEVICE_ID] = preferences[DEVICE_ID] ?: UUID.randomUUID().toString()
        }.let { preferences ->
            checkNotNull(preferences[DEVICE_ID])
        }
    }

    private companion object {
        val DEVICE_ID = stringPreferencesKey("device_id")
    }
}
