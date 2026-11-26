package com.pasajeya.app.feature.trip.domain

import java.nio.charset.StandardCharsets

/**
 * Lo que se dicen el celular del conductor (lector) y el del pasajero (tarjeta emulada) por NFC.
 *
 * Antes el servicio del pasajero comparaba el AID con el texto "A0000000010102" en ASCII
 * (14 bytes), mientras el conductor enviaba los 7 bytes binarios A0 00 00 00 01 01 02. El
 * pasajero siempre respondia "error" (6A 82), el conductor tomaba esos dos bytes como si
 * fueran un uid y abria un viaje para un pasajero que no existe, con el nombre del conductor.
 * Las dos puntas usan ahora este mismo objeto, y una respuesta que no sea una credencial
 * valida no se toma como pasajero.
 */
object NfcCredentialProtocol {

    /** AID propio de PasajeYa, el mismo de res/xml/apduservice.xml. */
    val AID: ByteArray = byteArrayOf(0xA0.toByte(), 0x00, 0x00, 0x00, 0x01, 0x01, 0x02)

    private val SW_OK = byteArrayOf(0x90.toByte(), 0x00)
    val SW_NOT_FOUND: ByteArray = byteArrayOf(0x6A, 0x82.toByte())

    /** SELECT por nombre de nuestro AID, con Le = 00. */
    fun selectApdu(): ByteArray =
        byteArrayOf(0x00, 0xA4.toByte(), 0x04, 0x00, AID.size.toByte()) + AID + byteArrayOf(0x00)

    /** true si el comando es un SELECT por nombre de nuestro AID (con o sin Le al final). */
    fun isSelectOfOurAid(apdu: ByteArray): Boolean {
        if (apdu.size < 5 + AID.size) return false
        if (apdu[0] != 0x00.toByte() || apdu[1] != 0xA4.toByte() || apdu[2] != 0x04.toByte()) return false
        val lc = apdu[4].toInt() and 0xFF
        if (lc != AID.size) return false
        return apdu.copyOfRange(5, 5 + lc).contentEquals(AID)
    }

    /** La respuesta del pasajero: su credencial (la misma del QR) seguida de 90 00. */
    fun responseFor(passengerUid: String): ByteArray =
        PassengerQrCodec.credentialFor(passengerUid).toByteArray(StandardCharsets.UTF_8) + SW_OK

    /**
     * El uid del pasajero a partir de la respuesta, o null si la respuesta no es una credencial
     * de PasajeYa (un error, otra tarjeta, otra app). Nunca devuelve basura como uid.
     */
    /** Explicacion para el conductor cuando la lectura no trae un pasajero. */
    fun problemFor(response: ByteArray?): String {
        if (response == null || response.isEmpty()) {
            return "El celular del pasajero no respondio. Acercalo de nuevo y mantenlo juntos un segundo."
        }
        val n = response.size
        val code = if (n >= 2) "%02X%02X".format(response[n - 2], response[n - 1]) else "%02X".format(response[0])
        return if (code == "6A82") {
            "El celular del pasajero respondio sin su cuenta (codigo 6A82). Revisa que tenga PasajeYa actualizada y la sesion abierta."
        } else {
            "Eso no es un pasajero de PasajeYa (codigo $code). Prueba de nuevo o usa el QR."
        }
    }

    fun passengerUidFrom(response: ByteArray?): String? {
        if (response == null || response.size <= 2) return null
        val n = response.size
        if (response[n - 2] != SW_OK[0] || response[n - 1] != SW_OK[1]) return null
        val text = String(response.copyOfRange(0, n - 2), StandardCharsets.UTF_8).trim()
        if (!text.startsWith(PassengerQrCodec.PREFIX)) return null
        return PassengerQrCodec.passengerUid(text)
    }
}
