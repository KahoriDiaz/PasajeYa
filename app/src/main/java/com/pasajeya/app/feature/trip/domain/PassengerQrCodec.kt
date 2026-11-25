package com.pasajeya.app.feature.trip.domain

/**
 * Credencial del pasajero, la misma por QR y por NFC.
 *
 * El telefono del pasajero muestra (QR) o emite (NFC) el texto `DEMO_QR_<uid>`. El conductor
 * lee ese texto y necesita el uid limpio: el viaje se guarda con el uid, no con el texto.
 * Antes el lector de QR buscaba el viaje abierto con el texto completo, nunca lo encontraba
 * y la segunda lectura no hacia el Tap Out; por NFC si, porque ahi se quitaba el prefijo.
 */
object PassengerQrCodec {

    const val PREFIX = "DEMO_QR_"

    /** El texto que el pasajero muestra o emite. */
    fun credentialFor(uid: String): String = PREFIX + uid

    /** El uid del pasajero a partir de lo leido, o null si no se puede leer. */
    fun passengerUid(raw: String?): String? =
        raw?.trim()?.removePrefix(PREFIX)?.trim()?.takeIf { it.isNotBlank() }
}
