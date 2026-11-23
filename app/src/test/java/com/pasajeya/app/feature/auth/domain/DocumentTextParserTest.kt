package com.pasajeya.app.feature.auth.domain

import com.pasajeya.app.feature.auth.domain.validation.Validators
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentTextParserTest {
    @Test
    fun dniReadsNumberAndLabelledNames() {
        val scan = DocumentTextParser.parseDni(
            """
            REPUBLICA DEL PERU
            DOCUMENTO NACIONAL DE IDENTIDAD
            DNI 45678912
            APELLIDOS
            QUISPE MAMANI
            NOMBRES
            JOSÉ LUIS
            """.trimIndent()
        )

        assertEquals("45678912", scan.dni)
        assertEquals("Jose Luis Quispe Mamani", scan.fullName)
    }

    @Test
    fun dniAcceptsInlineLabelsAndKeepsEnie() {
        val scan = DocumentTextParser.parseDni(
            "DNI 71234567-3\nApellidos: Peña Rojas\nPre Nombres: María"
        )

        assertEquals("71234567", scan.dni)
        assertEquals("Maria Peña Rojas", scan.fullName)
    }

    @Test
    fun dniRejectsRepeatedDigitsAndShortNumbers() {
        assertNull(DocumentTextParser.parseDni("DNI 11111111").dni)
        assertNull(DocumentTextParser.parseDni("DNI 1234567").dni)
        assertTrue(DocumentTextParser.parseDni("nada util aqui 12").isEmpty)
    }

    @Test
    fun licenseReadsNumberAndCategory() {
        val scan = DocumentTextParser.parseLicense(
            "LICENCIA DE CONDUCIR\nNo. Q45678912\nCLASE Y CATEGORIA A-IIb\nDNI 45678912"
        )

        assertEquals("Q45678912", scan.licenseNumber)
        assertEquals("A-IIb", scan.category)
    }

    @Test
    fun licenseDetectsBlockedCategoryAndLooseFormatting() {
        val scan = DocumentTextParser.parseLicense("CATEGORIA: A I\nLICENCIA B 12345678")

        assertEquals("B12345678", scan.licenseNumber)
        assertEquals("A-I", scan.category)
    }

    @Test
    fun licenseHandlesThirdClassAndUnknownCategories() {
        assertEquals("A-IIIa", DocumentTextParser.parseLicense("CAT A-IIIA").category)
        assertNull(DocumentTextParser.parseLicense("CAT A-IIIC").category)
        assertTrue(DocumentTextParser.parseLicense("texto cualquiera").isEmpty)
    }

    @Test
    fun onlyProfessionalCategoriesAreAllowed() {
        assertFalse(RegistrationConfig.isLicenseCategoryAllowed("A-I"))
        assertTrue(RegistrationConfig.isLicenseCategoryAllowed("A-IIa"))
        assertTrue(RegistrationConfig.isLicenseCategoryAllowed("A-IIb"))
        assertTrue(RegistrationConfig.isLicenseCategoryAllowed("A-IIIa"))
        assertFalse(RegistrationConfig.isLicenseCategoryAllowed("B-I"))
    }

    @Test
    fun universityCardReadsCodeAndFutureExpiry() {
        val scan = DocumentTextParser.parseUniversityCard(
            """
            UNIVERSIDAD CATOLICA DE SANTA MARIA
            CARNET UNIVERSITARIO
            Codigo: UCSM2023001
            Caducidad: 02/2028
            """.trimIndent(),
            LocalDate.of(2026, 11, 4)
        )

        assertEquals("UCSM2023001", scan.studentCode)
        assertEquals(LocalDate.of(2028, 2, 1), scan.expiryDate)
        assertFalse(scan.expired == true)
        assertTrue(scan.isValid)
    }

    @Test
    fun universityCardReadsFullDateAndDayMonthYear() {
        val full = DocumentTextParser.parseUniversityCard(
            "MATRICULA A1234567 VIGENCIA 31/12/2030",
            LocalDate.of(2026, 11, 4)
        )
        assertEquals("A1234567", full.studentCode)
        assertEquals(LocalDate.of(2030, 12, 31), full.expiryDate)

        val monthName = DocumentTextParser.parseUniversityCard(
            "Codigo Q9876543 expira enero 2027",
            LocalDate.of(2026, 11, 4)
        )
        assertEquals("Q9876543", monthName.studentCode)
        assertEquals(LocalDate.of(2027, 1, 1), monthName.expiryDate)
    }

    @Test
    fun universityCardExpiredBeforeTodayIsNotValid() {
        val scan = DocumentTextParser.parseUniversityCard(
            "CODIGO X1234567 VENCIDO 01/01/2025",
            LocalDate.of(2026, 11, 4)
        )

        assertEquals("X1234567", scan.studentCode)
        assertEquals(LocalDate.of(2025, 1, 1), scan.expiryDate)
        assertTrue(scan.expired == true)
        assertFalse(scan.isValid)
    }

    @Test
    fun universityCardWithoutCodeIsInvalid() {
        val scan = DocumentTextParser.parseUniversityCard(
            "UNIVERSIDAD CATOLICA caducidad 02/2028",
            LocalDate.of(2026, 11, 4)
        )

        assertNull(scan.studentCode)
        assertFalse(scan.isValid)
    }

    @Test
    fun institutionalEmailAcceptsEduPeAndRejectsCommercial() {
        assertTrue(Validators.isInstitutionalEmail("alumno@ucsm.edu.pe"))
        assertTrue(Validators.isInstitutionalEmail("a.b+c@university.edu"))
        assertFalse(Validators.isInstitutionalEmail("alumno@gmail.com"))
        assertFalse(Validators.isInstitutionalEmail("alumno@ucsm.com"))
    }

    @Test
    fun licenseCategoryMapsWordAndNumericVariants() {
        assertEquals("A-I", DocumentTextParser.parseLicense("CLASE A UNO").category)
        assertEquals("A-I", DocumentTextParser.parseLicense("CLASE A 1").category)
        assertEquals("A-I", DocumentTextParser.parseLicense("CLASE A I").category)
        assertEquals("A-IIa", DocumentTextParser.parseLicense("CLASE A DOS A").category)
        assertEquals("A-IIa", DocumentTextParser.parseLicense("CLASE A 2A").category)
        assertEquals("A-IIa", DocumentTextParser.parseLicense("CLASE A IIA").category)
        assertEquals("A-IIb", DocumentTextParser.parseLicense("CLASE A DOS B").category)
        assertEquals("A-IIb", DocumentTextParser.parseLicense("CLASE A 2B").category)
        assertEquals("A-IIb", DocumentTextParser.parseLicense("CLASE A IIB").category)
        assertEquals("A-IIIa", DocumentTextParser.parseLicense("CLASE A TRES A").category)
        assertEquals("A-IIIa", DocumentTextParser.parseLicense("CLASE A 3A").category)
        assertEquals("A-IIIa", DocumentTextParser.parseLicense("CLASE A IIIA").category)
        assertEquals("A-IIIa", DocumentTextParser.parseLicense("CAT A-IIIA").category)
    }

    @Test
    fun licenseCategoryRejectsUnsupportedVariants() {
        assertNull(DocumentTextParser.parseLicense("CLASE A TRES B").category)
        assertNull(DocumentTextParser.parseLicense("CLASE A CUATRO").category)
        assertNull(DocumentTextParser.parseLicense("CLASE A X").category)
    }

    @Test
    fun licenseDetectsClaseAAndCategoriaUnoOnSeparateLines() {
        val scan = DocumentTextParser.parseLicense(
            "LICENCIA DE CONDUCIR\nCLASE A\nCATEGORIA UNO\nNo. H72458433"
        )
        assertEquals("H72458433", scan.licenseNumber)
        assertEquals("A-I", scan.category)
    }

    @Test
    fun licenseDetectsClaseAAndCategoriaDosAOnSeparateLines() {
        val scan = DocumentTextParser.parseLicense(
            "CLASE A\nCATEGORIA DOS A\nLICENCIA B12345678"
        )
        assertEquals("B12345678", scan.licenseNumber)
        assertEquals("A-IIa", scan.category)
    }

    @Test
    fun licenseDetectsClaseAAndCategoriaTresAOnSeparateLines() {
        val scan = DocumentTextParser.parseLicense(
            "No. T98765432\nCLASE A\nCATEGORIA TRES A"
        )
        assertEquals("T98765432", scan.licenseNumber)
        assertEquals("A-IIIa", scan.category)
    }

    @Test
    fun licenseDetectsClaseAAndCategoria1OnSeparateLines() {
        val scan = DocumentTextParser.parseLicense(
            "CLASE A\nCATEGORIA 1\nLICENCIA H11111111"
        )
        assertEquals("H11111111", scan.licenseNumber)
        assertEquals("A-I", scan.category)
    }

    @Test
    fun licenseDetectsClaseAAndCategoria2bOnSeparateLines() {
        val scan = DocumentTextParser.parseLicense(
            "CLASE A\nCATEGORIA DOS B\nLICENCIA K22222222"
        )
        assertEquals("K22222222", scan.licenseNumber)
        assertEquals("A-IIb", scan.category)
    }
}
