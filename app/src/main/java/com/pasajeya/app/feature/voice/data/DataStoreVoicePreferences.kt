package com.pasajeya.app.feature.voice.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import com.pasajeya.app.feature.voice.domain.VoicePreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val Context.voicePreferences by preferencesDataStore(name = "voice_preferences")

@Singleton
class DataStoreVoicePreferences @Inject constructor(
    @param:ApplicationContext private val context: Context
) : VoicePreferences {

    override val voiceEnabled: Flow<Boolean> = context.voicePreferences.data
        .catch { error ->
            if (error is IOException) {
                emit(emptyPreferences())
            } else {
                throw error
            }
        }
        .map { preferences -> preferences[VOICE_ENABLED] ?: true }
        .distinctUntilChanged()

    override suspend fun setVoiceEnabled(enabled: Boolean) {
        context.voicePreferences.edit { preferences ->
            preferences[VOICE_ENABLED] = enabled
        }
    }

    private companion object {
        val VOICE_ENABLED = booleanPreferencesKey("voice_enabled")
    }
}
