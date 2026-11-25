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

    /**
     * Toma la sesion para este telefono. Manda el ultimo inicio de sesion: el telefono que
     * estaba abierto se cierra. Devuelve la marca de tiempo del servidor con la que quedo
     * registrada, para poder distinguir despues un inicio posterior al nuestro.
     */
    suspend fun registerDevice(uid: String, deviceId: String): Long?

    /** Observa el activeDeviceId remoto del usuario en Firestore. */
    fun observeActiveDeviceId(uid: String): Flow<String?>
}
