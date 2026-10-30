package com.pasajeya.app.feature.voice.data

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import com.pasajeya.app.feature.voice.domain.SpeechAnnouncer
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class AndroidSpeechAnnouncer @Inject constructor(
    @ApplicationContext private val context: Context
) : SpeechAnnouncer {

    private var tts: TextToSpeech? = null
    private var initialized = false
    override var isAvailable = false
        private set

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                setupLanguage()
            } else {
                Log.e("SpeechAnnouncer", "TTS initialization failed")
            }
        }
    }

    private fun setupLanguage() {
        val localePe = Locale("es", "PE")
        val result = tts?.setLanguage(localePe) ?: TextToSpeech.LANG_NOT_SUPPORTED
        
        isAvailable = when (result) {
            TextToSpeech.LANG_AVAILABLE,
            TextToSpeech.LANG_COUNTRY_AVAILABLE,
            TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE -> {
                initialized = true
                true
            }
            else -> {
                // Intento con español neutro o US si es-PE no está
                val fallback = tts?.setLanguage(Locale("es"))
                if (fallback != TextToSpeech.LANG_MISSING_DATA && fallback != TextToSpeech.LANG_NOT_SUPPORTED) {
                    initialized = true
                    true
                } else {
                    Log.w("SpeechAnnouncer", "Spanish language not supported for TTS")
                    false
                }
            }
        }
    }

    override suspend fun speak(text: String): Boolean = withContext(Dispatchers.Main) {
        if (!isAvailable || tts == null || text.isBlank()) return@withContext false

        val result = tts?.speak(text, TextToSpeech.QUEUE_ADD, null, "msg_${System.currentTimeMillis()}")
        result == TextToSpeech.SUCCESS
    }

    override fun stop() {
        tts?.stop()
    }

    override fun release() {
        tts?.shutdown()
        tts = null
        initialized = false
        isAvailable = false
    }
}
