package com.pasajeya.app.feature.auth.domain.validation

import java.time.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class ValidationVectorsTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val vectors = json.decodeFromString<ValidationVectors>(readShared("validation-vectors.json"))
    private val commonPasswords = json
        .decodeFromString<List<String>>(readShared("common-passwords.json"))
        .toSet()

    @Test
    fun commonPasswordListHasAtLeastOneHundredEntries() {
        assertEquals(true, commonPasswords.size >= 100)
    }

    @Test
    fun emailVectorsMatch() {
        vectors.email.forEach { case ->
            assertEquals(case.reason, case.valid, Validators.isValidEmail(case.value))
        }
    }

    @Test
    fun passwordVectorsMatch() {
        vectors.password.forEach { case ->
            val result = Validators.validatePassword(case.value, case.email, commonPasswords)
            assertEquals(case.reason, case.valid, result.valid)
        }
    }

    @Test
    fun dniVectorsMatch() {
        vectors.dni.forEach { case ->
            assertEquals(case.reason, case.valid, Validators.isValidDni(case.value))
        }
    }

    @Test
    fun personNameVectorsMatch() {
        vectors.personName.forEach { case ->
            assertEquals(case.reason, case.valid, Validators.isValidPersonName(case.value))
        }
    }

    @Test
    fun birthDateVectorsMatch() {
        val referenceDate = LocalDate.parse(vectors.referenceDate)
        vectors.birthDate.forEach { case ->
            assertEquals(
                case.reason,
                case.valid,
                Validators.isValidBirthDate(case.value, referenceDate)
            )
        }
    }

    @Test
    fun plateVectorsMatch() {
        vectors.plate.forEach { case ->
            assertEquals(case.reason, case.valid, Validators.isValidPlate(case.value))
            case.normalized?.let {
                assertEquals(case.reason, it, Validators.normalizePlate(case.value))
            }
        }
    }

    @Test
    fun licenseNumberVectorsMatch() {
        vectors.licenseNumber.forEach { case ->
            assertEquals(case.reason, case.valid, Validators.isValidLicenseNumber(case.value))
            case.normalized?.let {
                assertEquals(case.reason, it, Validators.normalizeLicenseNumber(case.value))
            }
        }
    }

    @Test
    fun licenseClassVectorsMatch() {
        vectors.licenseClass.forEach { case ->
            assertEquals(case.reason, case.valid, Validators.isValidLicenseClass(case.value))
        }
    }

    @Test
    fun passwordRequirementsAreReportedIndividually() {
        val result = Validators.validatePassword(
            value = "abcdefgh",
            email = "pasajero@example.com",
            commonPasswords = commonPasswords
        )

        assertEquals(false, result.valid)
        assertEquals(
            setOf(
                PasswordRequirement.LENGTH,
                PasswordRequirement.UPPERCASE,
                PasswordRequirement.DIGIT,
                PasswordRequirement.SYMBOL
            ),
            result.unmetRequirements
        )
    }

    private fun readShared(name: String): String =
        checkNotNull(javaClass.classLoader?.getResource(name)) {
            "No se encontró el recurso compartido $name"
        }.readText()
}

@Serializable
private data class ValidationVectors(
    val referenceDate: String,
    val email: List<EmailCase>,
    val password: List<PasswordCase>,
    val dni: List<Case>,
    val personName: List<Case>,
    val birthDate: List<Case>,
    val plate: List<Case>,
    val licenseNumber: List<Case>,
    val licenseClass: List<Case>
)

@Serializable
private data class EmailCase(
    val value: String,
    val valid: Boolean,
    val reason: String
)

@Serializable
private data class PasswordCase(
    val value: String,
    val email: String,
    val valid: Boolean,
    val reason: String
)

@Serializable
private data class Case(
    val value: String,
    val valid: Boolean,
    val reason: String,
    val normalized: String? = null
)
