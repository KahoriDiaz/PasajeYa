package com.pasajeya.app.core.util

import java.security.MessageDigest

/**
 * El DNI nunca se usa como identificador visible: se guarda su huella SHA-256 para
 * poder comprobar que no esté repetido sin exponerlo.
 */
fun hashedDni(dni: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(dni.trim().toByteArray())
    return digest.joinToString("") { byte -> "%02x".format(byte) }
}
