package com.pasajeya.app.feature.voice.data

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import com.pasajeya.app.feature.voice.domain.SpeechAnnouncer
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Motor de voz del conductor.
 *
 * El motor se prepara solo la primera vez que se necesita y se vuelve a preparar si quedo
 * apagado: antes se apagaba al salir de la pantalla del turno y, al volver con la barra de
 * abajo, el aviso decia que no habia datos de voz cuando si los habia.
 */
@Singleton
class AndroidSpeechAnnouncer @Inject constructor(
    @ApplicationContext private val context: Context
) : SpeechAnnouncer {

    private val engineLock = Mutex()
    private var tts: TextToSpeech? = null
    private var languageMissing = false

    /** Mientras el motor se prepara no se avisa nada: solo si el idioma falta de verdad. */
    override val isAvailable: Boolean
        get() = !languageMissing

    override suspend fun speak(text: String): Boolean {
        if (text.isBlank()) return false
        val engine = engine() ?: return false
        return withContext(Dispatchers.Main) {
            engine.speak(
                text,
                TextToSpeech.QUEUE_ADD,
                null,
                "msg_${System.currentTimeMillis()}"
            ) == TextToSpeech.SUCCESS
        }
    }

    override fun stop() {
        runCatching { tts?.stop() }
    }

    override fun release() {
        runCatching { tts?.shutdown() }
        tts = null
        languageMissing = false
    }

    private suspend fun engine(): TextToSpeech? = engineLock.withLock {
        tts?.let { return@withLock it }
        val ready = CompletableDeferred<Boolean>()
        val engine = withContext(Dispatchers.Main) {
            TextToSpeech(context) { status ->
                ready.complete(status == TextToSpeech.SUCCESS)
            }
        }
        val initialized = withTimeoutOrNull(INIT_TIMEOUT_MILLIS) { ready.await() } ?: false
        if (!initialized) {
            Log.w("SpeechAnnouncer", "TTS initialization failed")
            runCatching { engine.shutdown() }
            return@withLock null
        }
        if (!applySpanish(engine)) {
            languageMissing = true
            runCatching { engine.shutdown() }
            return@withLock null
        }
        languageMissing = false
        tts = engine
        engine
    }

    /** Castellano del Peru si esta instalado; si no, castellano neutro. */
    private fun applySpanish(engine: TextToSpeech): Boolean {
        val peru = engine.setLanguage(Locale("es", "PE"))
        if (peru == TextToSpeech.LANG_AVAILABLE ||
            peru == TextToSpeech.LANG_COUNTRY_AVAILABLE ||
            peru == TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE
        ) {
            return true
        }
        val neutral = engine.setLanguage(Locale("es"))
        return neutral != TextToSpeech.LANG_MISSING_DATA &&
            neutral != TextToSpeech.LANG_NOT_SUPPORTED
    }

    private companion object {
        const val INIT_TIMEOUT_MILLIS = 5_000L
    }
}
