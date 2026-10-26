package com.pasajeya.app.feature.auth.domain.validation

object OperatorConfig {
    val plateRegex = Regex("^[A-Z0-9]{6}$")
    val licenseNumberRegex = Regex("^[A-Z][0-9]{8}$")

    val licenseClasses = listOf(
        "A-I",
        "A-IIa",
        "A-IIb",
        "A-IIIa",
        "A-IIIb",
        "A-IIIc",
        "B-I",
        "B-IIa",
        "B-IIb",
        "B-IIc"
    )

    fun normalizePlate(value: String): String =
        value.trim().uppercase().replace("-", "")

    fun normalizeLicenseNumber(value: String): String =
        value.trim().uppercase()
}
