package com.pasajeya.app.feature.benefit.domain

import java.text.Normalizer

/**
 * Verificaci�n asistida del carn� universitario.
 * Valida presencia de palabras institucionales o t�rminos est�ndar de carn� (SUNEDU / UCSM / UNIVERSIDAD).
 * La validaci�n de fondo se consolida con el correo institucional @ucsm.edu.pe (OTP).
 */
object CarneTextCheck {
    private val KEYWORDS = listOf(
        "UCSM",
        "CATOLICA",
        "SANTA MARIA",
        "SUNEDU"
    )

    fun normalize(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(Regex("[\\u0300-\\u036f]"), "")
            .uppercase()
            .replace(Regex("[^A-Z0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    fun looksLikeUniversityCard(recognizedText: String): Boolean {
        val normalized = normalize(recognizedText)
        if (normalized.isBlank()) return false
        return KEYWORDS.any { normalized.contains(it) }
    }
}
