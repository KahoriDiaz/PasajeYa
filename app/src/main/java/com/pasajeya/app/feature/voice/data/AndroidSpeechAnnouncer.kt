package com.pasajeya.app.feature.voice.data

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import com.pasajeya.app.feature.voice.domain.SpeechAnnouncer
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

@Singleton
class AndroidSpeechAnnouncer @Inject constructor(
    @ApplicationContext context: Context
) : SpeechAnnouncer {

    private val ready = CompletableDeferred<Boolean>()
    private val textToSpeech: TextToSpeech
    private var utteranceSequence = 0

    @Volatile
    override var isAvailable: Boolean = false
        private set

    @Volatile
    private var released = false

    init {
        textToSpeech = TextToSpeech(context) { status ->
            if (released) {
                textToSpeech.shutdown()
            } else {
                val available = status == TextToSpeech.SUCCESS &&
                    selectSpanishLanguage()
                if (available) {
                    textToSpeech.setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                }
                isAvailable = available
                ready.complete(available)
            }
        }
    }

    override suspend fun speak(text: String): Boolean {
        val initialized = withTimeoutOrNull(INITIALIZATION_TIMEOUT_MILLIS) {
            ready.await()
        } ?: false
        if (!initialized) return false
        if (released) return false

        utteranceSequence += 1
        return textToSpeech.speak(
            text,
            TextToSpeech.QUEUE_ADD,
            null,
            "pasajeya-$utteranceSequence"
        ) == TextToSpeech.SUCCESS
    }

    override fun stop() {
        textToSpeech.stop()
    }

    override fun release() {
        released = true
        isAvailable = false
        if (!ready.isCompleted) {
            ready.complete(false)
        }
        textToSpeech.stop()
        textToSpeech.shutdown()
    }

    private fun selectSpanishLanguage(): Boolean {
        for (locale in SPANISH_LOCALES) {
            val result = textToSpeech.setLanguage(locale)
            val available = result == TextToSpeech.LANG_AVAILABLE ||
                result == TextToSpeech.LANG_COUNTRY_AVAILABLE ||
                result == TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE
            if (available) return true
        }
        return false
    }

    private companion object {
        const val INITIALIZATION_TIMEOUT_MILLIS = 5_000L

        val SPANISH_LOCALES = listOf(
            Locale.forLanguageTag("es-PE"),
            Locale.forLanguageTag("es-US"),
            Locale.forLanguageTag("es-ES")
        )
    }
}
