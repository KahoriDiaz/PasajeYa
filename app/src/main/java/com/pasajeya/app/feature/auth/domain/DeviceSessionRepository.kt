package com.pasajeya.app.feature.auth.domain

import kotlinx.coroutines.flow.Flow

/** Resultado de la consulta de sesion activa de dispositivo en Firestore. */
data class DeviceSessionInfo(
    val exists: Boolean,
    val deviceId: String?
)

/** Provee el identificador unico del dispositivo y persiste la sesion activa. */
interface DeviceSessionRepository {
    /** Devuelve el ID del dispositivo, generandolo si no existe. */
    val deviceId: String

    /** Guarda el deviceId activo en Firestore para el usuario dado. */
    suspend fun registerDevice(uid: String, deviceId: String)

    /** Observa el activeDeviceId remoto del usuario en Firestore. */
    fun observeActiveDeviceId(uid: String): Flow<String?>
}
