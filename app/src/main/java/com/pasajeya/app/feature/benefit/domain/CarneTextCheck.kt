package com.pasajeya.app.feature.benefit.domain

import java.text.Normalizer

/**
 * Verificacion asistida del carne universitario.
 * Valida la presencia de palabras institucionales o terminos estandar de carne
 * (SUNEDU / UCSM / UNIVERSIDAD). La verificacion de fondo la hace el correo
 * institucional .edu.pe con el enlace de Firebase Auth.
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
