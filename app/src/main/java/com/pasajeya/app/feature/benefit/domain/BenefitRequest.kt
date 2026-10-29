package com.pasajeya.app.feature.benefit.domain

data class BenefitRequest(
    val uid: String,
    val stage: String, // "carne_ok" | "code_sent"
    val carneCodeHash: String?, // HMAC del DNI, nunca en claro
    val emailHash: String?, // HMAC del correo
    val codeHash: String?, // HMAC del código recibido
    val expiresAt: Long, // milisegundos
    val attempts: Int,
    val updatedAt: Long
)