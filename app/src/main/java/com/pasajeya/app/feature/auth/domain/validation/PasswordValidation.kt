package com.pasajeya.app.feature.auth.domain.validation

enum class PasswordRequirement {
    LENGTH,
    UPPERCASE,
    LOWERCASE,
    DIGIT,
    SYMBOL,
    NO_WHITESPACE,
    NOT_EMAIL_LOCAL_PART,
    NOT_COMMON
}

enum class PasswordStrength {
    WEAK,
    FAIR,
    GOOD,
    STRONG
}

data class PasswordValidation(
    val valid: Boolean,
    val unmetRequirements: Set<PasswordRequirement>,
    val strength: PasswordStrength
) {
    fun meets(requirement: PasswordRequirement): Boolean =
        requirement !in unmetRequirements
}
