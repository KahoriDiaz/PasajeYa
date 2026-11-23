package com.pasajeya.app.core.util

import java.time.LocalDate
import java.time.Period
import java.time.format.DateTimeFormatter

private val DAY_FIRST = DateTimeFormatter.ofPattern("dd/MM/yyyy")

/** Acepta ISO (1999-05-20) y el formato del registro (20/05/1999). */
fun parseBirthDate(text: String): LocalDate? {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return null
    return runCatching { LocalDate.parse(trimmed) }.getOrNull()
        ?: runCatching { LocalDate.parse(trimmed, DAY_FIRST) }.getOrNull()
}

/** Edad en años cumplidos, o null si la fecha no es usable. */
fun ageFromBirthDate(text: String): Int? {
    val birthDate = parseBirthDate(text) ?: return null
    return Period.between(birthDate, LocalDate.now()).years.takeIf { it in 0..120 }
}
