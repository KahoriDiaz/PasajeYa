package com.pasajeya.app.feature.auth.domain

import com.pasajeya.app.feature.auth.domain.validation.Validators
import java.text.Normalizer
import java.time.LocalDate
import java.time.Month
import java.util.Locale

data class DniScan(val dni: String?, val fullName: String?, val birthDate: String?) {
    val isEmpty: Boolean get() = dni == null && fullName == null
}

data class LicenseScan(val licenseNumber: String?, val category: String?) {
    val isEmpty: Boolean get() = licenseNumber == null && category == null
}

data class UniversityCardScan(
    val studentCode: String?,
    val expiryDate: LocalDate?,
    /** true solo cuando se detectó una fecha y ya pasó frente a [referenceDate]. */
    val expired: Boolean?
) {
    val hasCode: Boolean get() = studentCode?.isNotBlank() == true
    val isValid: Boolean get() = hasCode && (expiryDate == null || expired != true)
}

/** Extrae datos del texto OCR de un DNI, brevete o carné universitario. Solo usa lo que realmente se leyó. */
object DocumentTextParser {
    private val dniRegex = Regex("(?<!\\d)(\\d{8})(?!\\d)")
    private val birthDateRegex = Regex("(?i)(?:FECHA\\s*DE\\s*NACIMIENTO|NACIMIENTO)\\b[:\\.\\s]*(\\d{1,2})/(\\d{1,2})/(\\d{4})")
    private val licenseNumberRegex = Regex("(?<![A-Z0-9])([A-Z])\\s?[-.]?\\s?(\\d{8})(?!\\d)")
    private val studentCodeRegex = Regex("(?i)\\b(?=[A-Za-z0-9]*\\d)[A-Za-z0-9]{6,12}\\b")
    private val expiryDateRegex = Regex("(?i)\\b(\\d{1,2})/(\\d{1,2})/(\\d{4})\\b")
    private val monthYearRegex = Regex("(?i)\\b(\\d{1,2})/(\\d{4})\\b")
    private val monthNameYearRegex = Regex("(?i)\\b(enero|febrero|marzo|abril|mayo|junio|julio|agosto|septiembre|setiembre|octubre|noviembre|diciembre|ene|feb|mar|abr|may|jun|jul|ago|sep|set|oct|nov|dic)\\s+(\\d{4})\\b")
    private val categoryAnchoredRegex = Regex("(?i)\\b(?:CLASE|CATEGORIA)\\s*A\\s*(?:-|–|\\.)?\\s*([A-Za-z0-9]+(?:\\s+[A-C])?)\\b")
    private val categoryFallbackRegex = Regex("(?<![A-Za-z0-9])A\\s*(?:-|–|\\.)?\\s*([A-Za-z0-9]+(?:\\s+[A-C])?)\\b")
    private const val ENIE = "\u0001"
    private val lettersOnly = Regex("^[A-Z\u00D1][A-Z\u00D1' -]*[A-Z\u00D1]$")
    private val surnameLabel = Regex("^(?:PRIMER |SEGUNDO )?APELLIDOS?\\b[:.\\s]*(.*)$")
    private val givenNameLabel = Regex("^(?:PRE ?)?NOMBRES?\\b[:.\\s]*(.*)$")
    private val stopWords = listOf(
        "REPUBLICA", "PERU", "DOCUMENTO", "NACIONAL", "IDENTIDAD", "DNI", "RENIEC",
        "FECHA", "NACIMIENTO", "SEXO", "ESTADO", "CIVIL", "DOMICILIO", "UBIGEO",
        "EMISION", "CADUCIDAD", "FIRMA", "HUELLA", "LICENCIA", "CONDUCIR", "BREVETE",
        "CATEGORIA", "CLASE", "MTC", "DEPARTAMENTO", "PROVINCIA", "DISTRITO"
    )

    fun parseDni(rawText: String): DniScan {
        val lines = normalizedLines(rawText)
        return DniScan(
            dni = findDni(lines),
            fullName = findFullName(lines),
            birthDate = findBirthDate(lines)
        )
    }

    fun parseLicense(rawText: String): LicenseScan {
        val lines = normalizedLines(rawText)
        val text = lines.joinToString(" ")
        val lowerText = text.lowercase(Locale.ROOT)
        val number = licenseNumberRegex.find(text)?.let { it.groupValues[1] + it.groupValues[2] }

        // Detectar "clase A" (dispara bloqueo para transporte público urbano en brevetes peruanos)
        val hasClaseA = Regex("(?i)\\bclase\\s+a\\b").find(text) != null

        var category: String? = null

        // Intento 1: Patrón anclado existente (CLASE A-X, CATEGORIA A-IIb, CAT A-IIIA, etc.)
        category = categoryAnchoredRegex.find(text)
            ?.let { it.groupValues[1] }
            ?.let(::normalizeCategory)

        // Intento 2: Si hay "clase A", detectar categoría por palabras clave en "categoría X"
        // o palabras sueltas (uno, 1, i, dos a, 2a, etc.) cuando vienen en líneas separadas.
        if (category == null && hasClaseA) {
            category = detectCategoryFromKeywords(lowerText)
        }

        // Intento 3: Fallback regex existente
        if (category == null) {
            category = categoryFallbackRegex.find(text)
                ?.let { it.groupValues[1] }
                ?.let(::normalizeCategory)
        }

        return LicenseScan(licenseNumber = number, category = category)
    }

    /** Detecta la categoría de licencia a partir de palabras clave como "categoria uno", "dos a", etc. */
    private fun detectCategoryFromKeywords(text: String): String? {
        val patterns = listOf(
            Regex("(?i)\\bcategoria\\s*(?:de\\s*)?(?:uno|\\b1\\b|i)\\b") to "A-I",
            Regex("(?i)\\bcategoria\\s*(?:de\\s*)?(?:dos\\s*a|2a|iia)\\b") to "A-IIa",
            Regex("(?i)\\bcategoria\\s*(?:de\\s*)?(?:dos\\s*b|2b|iib)\\b") to "A-IIb",
            Regex("(?i)\\bcategoria\\s*(?:de\\s*)?(?:tres\\s*a|3a|iiia)\\b") to "A-IIIa",
            // Palabras sueltas sin la palabra "categoria"
            Regex("(?i)\\b(?:uno|\\b1\\b|i)\\b") to "A-I",
            Regex("(?i)\\b(?:dos\\s*a|2a|iia)\\b") to "A-IIa",
            Regex("(?i)\\b(?:dos\\s*b|2b|iib)\\b") to "A-IIb",
            Regex("(?i)\\b(?:tres\\s*a|3a|iiia)\\b") to "A-IIIa",
        )
        return patterns.firstOrNull { (regex, _) -> regex.find(text) != null }?.second
    }

    fun parseUniversityCard(rawText: String, referenceDate: LocalDate = LocalDate.now()): UniversityCardScan {
        val lines = normalizedLines(rawText)
        val code = findStudentCode(lines)
        val expiryDate = findExpiryDate(lines)
        val expired = expiryDate?.let { it < referenceDate }
        return UniversityCardScan(code, expiryDate, expired)
    }

    private fun findStudentCode(lines: List<String>): String? {
        val text = lines.joinToString(" ")
        val candidates = studentCodeRegex.findAll(text)
            .map { it.value }
            .filter { it.isNotBlank() }
            .filter { !Validators.isValidDni(it) }
        return candidates.firstOrNull()
    }

    private fun findExpiryDate(lines: List<String>): LocalDate? {
        val text = lines.joinToString(" ")

        val fullDate = expiryDateRegex.find(text)?.let { match ->
            LocalDate.of(
                match.groupValues[3].toInt(),
                match.groupValues[2].toInt(),
                match.groupValues[1].toInt()
            )
        }
        if (fullDate != null) return fullDate

        val monthYear = monthYearRegex.find(text)?.let { match ->
            LocalDate.of(match.groupValues[2].toInt(), match.groupValues[1].toInt(), 1)
        }
        if (monthYear != null) return monthYear

        return monthNameYearRegex.find(text)?.let { match ->
            val month = MONTHS_BY_NAME[match.groupValues[1].lowercase(Locale.ROOT)]
                ?: return@let null
            LocalDate.of(match.groupValues[2].toInt(), month, 1)
        }
    }

    private fun findDni(lines: List<String>): String? {
        val candidates = lines.flatMap { line ->
            dniRegex.findAll(line).map { line to it.groupValues[1] }.toList()
        }.filter { Validators.isValidDni(it.second) }
        val labelled = candidates.firstOrNull { (line, _) ->
            "DNI" in line || "DOCUMENTO" in line
        }
        return (labelled ?: candidates.firstOrNull())?.second
    }

    private fun findBirthDate(lines: List<String>): String? {
        val text = lines.joinToString(" ")
        val match = birthDateRegex.find(text)
        return match?.let {
            val day = it.groupValues[1].padStart(2, '0')
            val month = it.groupValues[2].padStart(2, '0')
            val year = it.groupValues[3]
            "$day/$month/$year"
        }
    }

    private fun findFullName(lines: List<String>): String? {
        val surnames = labelledValues(lines, surnameLabel)
        val givenNames = labelledValues(lines, givenNameLabel)
        val labelled = (givenNames + surnames).joinToString(" ").trim()
        if (labelled.isNotEmpty()) return titleCase(labelled)

        val guess = lines
            .filter { line -> isNameLike(line) && line.contains(' ') }
            .take(2)
            .joinToString(" ")
        return guess.takeIf { it.isNotBlank() }?.let(::titleCase)
    }

    private fun labelledValues(lines: List<String>, label: Regex): List<String> {
        val values = mutableListOf<String>()
        lines.forEachIndexed { index, line ->
            val match = label.matchEntire(line) ?: return@forEachIndexed
            val inline = match.groupValues[1].trim()
            val value = if (inline.isNotEmpty()) inline else lines.getOrNull(index + 1).orEmpty()
            if (isNameLike(value) && surnameLabel.matchEntire(value) == null &&
                givenNameLabel.matchEntire(value) == null
            ) {
                values += value
            }
        }
        return values
    }

    private fun isNameLike(line: String): Boolean =
        lettersOnly.matches(line) && stopWords.none { word ->
            line.split(' ', '-').contains(word)
        }

    private fun normalizedLines(raw: String): List<String> =
        Normalizer.normalize(raw.uppercase(Locale.ROOT).replace("\u00D1", ENIE), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace(ENIE, "\u00D1")
            .lines()
            .map { it.trim().replace(Regex("\\s+"), " ") }
            .filter { it.isNotEmpty() }

    private fun normalizeCategory(token: String): String? {
        val key = token.lowercase(Locale.ROOT)
            .replace(Regex("[-\\u2013.]+"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
        return when (key) {
            "ai", "a-i", "uno", "1", "i" -> "A-I"
            "aiia", "a-iia", "dos a", "2a", "iia" -> "A-IIa"
            "aiib", "a-iib", "dos b", "2b", "iib" -> "A-IIb"
            "aiiia", "a-iiia", "tres a", "3a", "iiia" -> "A-IIIa"
            else -> null
        }
    }

    private val MONTHS_BY_NAME: Map<String, Month> = mapOf(
        "enero" to Month.JANUARY, "ene" to Month.JANUARY,
        "febrero" to Month.FEBRUARY, "feb" to Month.FEBRUARY,
        "marzo" to Month.MARCH, "mar" to Month.MARCH,
        "abril" to Month.APRIL, "abr" to Month.APRIL,
        "mayo" to Month.MAY, "may" to Month.MAY,
        "junio" to Month.JUNE, "jun" to Month.JUNE,
        "julio" to Month.JULY, "jul" to Month.JULY,
        "agosto" to Month.AUGUST, "ago" to Month.AUGUST,
        "septiembre" to Month.SEPTEMBER, "setiembre" to Month.SEPTEMBER, "sep" to Month.SEPTEMBER, "set" to Month.SEPTEMBER,
        "octubre" to Month.OCTOBER, "oct" to Month.OCTOBER,
        "noviembre" to Month.NOVEMBER, "nov" to Month.NOVEMBER,
        "diciembre" to Month.DECEMBER, "dic" to Month.DECEMBER
    )

    private fun titleCase(value: String): String =
        value.lowercase(Locale("es", "PE")).split(' ')
            .filter { it.isNotEmpty() }
            .joinToString(" ") { word ->
                word.replaceFirstChar { it.titlecase(Locale("es", "PE")) }
            }
}
