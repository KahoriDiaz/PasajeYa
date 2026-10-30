package com.pasajeya.app.feature.voice.domain

interface SpeechAnnouncer {
    /**
     * Indica si el motor de voz está listo para usarse.
     */
    val isAvailable: Boolean

    /**
     * Anuncia el texto proporcionado.
     * @return true si se encoló correctamente, false de lo contrario.
     */
    suspend fun speak(text: String): Boolean

    /**
     * Detiene cualquier anuncio en curso.
     */
    fun stop()

    /**
     * Libera los recursos del motor de voz.
     */
    fun release()
}
