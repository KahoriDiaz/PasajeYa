package com.pasajeya.app.feature.voice.domain

import kotlinx.coroutines.flow.Flow

interface VoicePreferences {
    val voiceEnabled: Flow<Boolean>

    suspend fun setVoiceEnabled(enabled: Boolean)
}
