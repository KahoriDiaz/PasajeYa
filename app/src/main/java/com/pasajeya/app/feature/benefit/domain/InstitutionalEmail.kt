package com.pasajeya.app.feature.benefit.domain

import com.pasajeya.app.feature.auth.domain.validation.Validators

/**
 * Regla del correo institucional: cualquier dominio academico peruano (.edu.pe).
 * Es el unico dato que prueba que la persona es estudiante, porque solo la
 * universidad entrega esas cuentas.
 */
object InstitutionalEmail {

    fun normalize(email: String): String = email.trim().lowercase()

    /** Usa la misma regla que el registro, para no tener dos definiciones distintas. */
    fun isInstitutional(email: String): Boolean =
        Validators.isInstitutionalEmail(normalize(email))

    /** Nombre de la institucion a partir del dominio, para mostrarlo en el beneficio. */
    fun institutionFromEmail(email: String): String = when {
        normalize(email).endsWith("@ucsm.edu.pe") -> "Universidad Católica de Santa María"
        else -> normalize(email).substringAfter('@')
    }
}
