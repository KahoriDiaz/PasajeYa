package com.pasajeya.app.feature.auth.domain.validation

import java.time.LocalDate
import java.time.Period

object Validators {
    private val emailRegex = Regex(
        "^[a-z0-9._%+-]+@[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?" +
            "(?:\\.[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?)+$"
    )
    private val nameRegex = Regex("^[\\p{L}]+(?:[ '\\-][\\p{L}]+)*$")
    private val dniRegex = Regex("^[0-9]{8}$")

    fun isValidEmail(value: String): Boolean {
        if (value.length !in 6..100 || !emailRegex.matches(value)) return false
        val localPart = value.substringBefore('@')
        return !localPart.startsWith(".") &&
            !localPart.endsWith(".") &&
            !localPart.contains("..")
    }

    fun validatePassword(
        value: String,
        email: String,
        commonPasswords: Set<String>
    ): PasswordValidation {
        val localPart = email.substringBefore('@').lowercase()
        val unmet = buildSet {
            if (value.length !in 10..64) add(PasswordRequirement.LENGTH)
            if (value.none(Char::isUpperCase)) add(PasswordRequirement.UPPERCASE)
            if (value.none(Char::isLowerCase)) add(PasswordRequirement.LOWERCASE)
            if (value.none(Char::isDigit)) add(PasswordRequirement.DIGIT)
            if (value.none { !it.isLetterOrDigit() && !it.isWhitespace() }) {
                add(PasswordRequirement.SYMBOL)
            }
            if (value.any(Char::isWhitespace)) add(PasswordRequirement.NO_WHITESPACE)
            if (localPart.isNotEmpty() && value.lowercase().contains(localPart)) {
                add(PasswordRequirement.NOT_EMAIL_LOCAL_PART)
            }
            if (value.lowercase() in commonPasswords) add(PasswordRequirement.NOT_COMMON)
        }

        val metCount = PasswordRequirement.entries.count { it !in unmet }
        val strength = when (metCount) {
            in 0..2 -> PasswordStrength.WEAK
            in 3..4 -> PasswordStrength.FAIR
            in 5..6 -> PasswordStrength.GOOD
            else -> PasswordStrength.STRONG
        }

        return PasswordValidation(
            valid = unmet.isEmpty(),
            unmetRequirements = unmet,
            strength = strength
        )
    }

    fun isValidDni(value: String): Boolean =
        dniRegex.matches(value) && value.toSet().size > 1

    fun isValidPersonName(value: String): Boolean =
        value.length in 2..40 && nameRegex.matches(value)

    fun isValidBirthDate(value: String, today: LocalDate = LocalDate.now()): Boolean {
        val birthDate = runCatching { LocalDate.parse(value) }.getOrNull() ?: return false
        if (birthDate.isAfter(today)) return false
        return Period.between(birthDate, today).years in 12..110
    }

    fun normalizePlate(value: String): String =
        OperatorConfig.normalizePlate(value)

    fun isValidPlate(value: String): Boolean =
        OperatorConfig.plateRegex.matches(normalizePlate(value))

    fun normalizeLicenseNumber(value: String): String =
        OperatorConfig.normalizeLicenseNumber(value)

    fun isValidLicenseNumber(value: String): Boolean =
        OperatorConfig.licenseNumberRegex.matches(normalizeLicenseNumber(value))

    fun isValidLicenseClass(value: String): Boolean =
        value in OperatorConfig.licenseClasses
}
