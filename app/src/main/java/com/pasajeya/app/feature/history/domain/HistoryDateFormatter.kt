package com.pasajeya.app.feature.history.domain

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object HistoryDateFormatter {
    private val limaZone = ZoneId.of("America/Lima")
    private val spanishPeru = Locale.forLanguageTag("es-PE")
    private val formatter = DateTimeFormatter
        .ofPattern("EEE d MMM · HH:mm", spanishPeru)
        .withZone(limaZone)

    fun format(millis: Long): String {
        val text = formatter.format(Instant.ofEpochMilli(millis))
            .replace(".", "")
        return text.replaceFirstChar { character ->
            if (character.isLowerCase()) {
                character.titlecase(spanishPeru)
            } else {
                character.toString()
            }
        }
    }
}
