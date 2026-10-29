package com.pasajeya.app.feature.voice.domain

interface SpeechAnnouncer {
    val isAvailable: Boolean

    suspend fun speak(text: String): Boolean

    fun stop()

    fun release()
}
