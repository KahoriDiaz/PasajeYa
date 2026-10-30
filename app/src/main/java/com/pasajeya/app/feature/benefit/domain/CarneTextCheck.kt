package com.pasajeya.app.feature.benefit.domain

import java.text.Normalizer

/**
 * Verificación asistida (en el teléfono) del texto leído con ML Kit antes de enviar el carné.
 * Es solo una ayuda para repetir la foto a tiempo: la validación que manda es la del servidor,
 * que usa las mismas palabras clave.
 */
object CarneTextCheck {
    val UNIVERSITY_KEYWORDS = listOf("CATOLICA", "SANTA", "MARIA")

    fun normalize(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(Regex("[\\u0300-\\u036f]"), "")
            .uppercase()
            .replace(Regex("[^A-Z0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    fun looksLikeUniversityCard(recognizedText: String): Boolean {
        val normalized = normalize(recognizedText)
        return UNIVERSITY_KEYWORDS.all { normalized.contains(it) }
    }
}
