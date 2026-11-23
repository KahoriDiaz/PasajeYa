package com.pasajeya.app.feature.voice.domain

import java.util.Locale
import javax.inject.Inject

class VoiceAnnouncementFormatter @Inject constructor() {
    fun format(firstName: String, age: Int): String? {
        val cleanName = cleanFirstName(firstName)
        if (cleanName.isEmpty()) return null

        val ageLabel = if (age == 1) "año" else "años"
        return "$cleanName, $age $ageLabel"
    }

    fun formatWelcome(firstName: String, fareType: String): String? {
        val cleanName = cleanFirstName(firstName)
        val typeLabel = if (fareType == "university") {
            "pasajero universitario"
        } else {
            "pasajero regular"
        }
        val name = if (cleanName.isEmpty()) typeLabel else "$cleanName, $typeLabel"
        return "Bienvenido, $name"
    }

    fun formatFarewell(firstName: String, fareType: String, totalCents: Int): String? {
        val cleanName = cleanFirstName(firstName)
        if (cleanName.isEmpty()) return null
        val typeLabel = if (fareType == "university") {
            "tarifa universitaria"
        } else {
            "tarifa regular"
        }
        val amount = String.format(Locale.ROOT, "%.2f", totalCents / 100.0)
        return "Hasta luego, $cleanName, viaje completado, $typeLabel, total cobrado $amount soles"
    }

    private fun cleanFirstName(raw: String): String {
        val firstWord = raw
            .filter { it.isLetter() || it.isWhitespace() }
            .trim()
            .split(WHITESPACE)
            .firstOrNull()
            .orEmpty()
        if (firstWord.isEmpty()) return ""

        return firstWord
            .lowercase(Locale.ROOT)
            .replaceFirstChar { it.titlecase(Locale.ROOT) }
            .take(MAX_NAME_LENGTH)
    }

    private companion object {
        const val MAX_NAME_LENGTH = 20
        val WHITESPACE = Regex("\\s+")
    }
}
