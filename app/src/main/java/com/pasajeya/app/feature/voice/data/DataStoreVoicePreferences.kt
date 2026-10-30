package com.pasajeya.app.feature.voice.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.pasajeya.app.feature.voice.domain.VoicePreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.voiceDataStore by preferencesDataStore(name = "voice_preferences")

@Singleton
class DataStoreVoicePreferences @Inject constructor(
    @ApplicationContext private val context: Context
) : VoicePreferences {

    override val voiceEnabled: Flow<Boolean> = context.voiceDataStore.data
        .map { preferences ->
            preferences[VOICE_ENABLED] ?: true
        }

    override suspend fun setVoiceEnabled(enabled: Boolean) {
        context.voiceDataStore.edit { preferences ->
            preferences[VOICE_ENABLED] = enabled
        }
    }

    private companion object {
        val VOICE_ENABLED = booleanPreferencesKey("voice_enabled")
    }
}
