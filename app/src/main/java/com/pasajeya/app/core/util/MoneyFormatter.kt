package com.pasajeya.app.core.util

import java.text.NumberFormat
import java.util.Locale

fun formatCents(cents: Long): String {
    val locale = Locale("es", "PE")
    val formatter = NumberFormat.getCurrencyInstance(locale)
    formatter.maximumFractionDigits = 2
    formatter.minimumFractionDigits = 2
    val value = cents / 100.0
    val formatted = formatter.format(value)
    return if (formatted.contains("S/")) formatted else "S/ ${String.format(locale, "%.2f", value)}"
}

fun formatCentsInt(cents: Int): String = formatCents(cents.toLong())
