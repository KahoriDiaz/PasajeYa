package com.pasajeya.app.feature.trip

import com.pasajeya.app.feature.trip.domain.PassengerQrCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * La credencial del pasajero es la misma por QR y por NFC, asi que leerla tiene que dar el
 * mismo uid por los dos caminos: con eso la segunda lectura hace Tap Out en ambos.
 */
class PassengerQrCodecTest {

    @Test
    fun `el qr y el nfc dan el mismo uid`() {
        val qr = PassengerQrCodec.credentialFor("abc123")
        // Lo que emite el servicio NFC del pasajero es ese mismo texto.
        val nfc = "DEMO_QR_abc123"
        assertEquals("abc123", PassengerQrCodec.passengerUid(qr))
        assertEquals("abc123", PassengerQrCodec.passengerUid(nfc))
    }

    @Test
    fun `un uid sin prefijo se acepta igual`() {
        assertEquals("abc123", PassengerQrCodec.passengerUid("abc123"))
    }

    @Test
    fun `los espacios no cuentan`() {
        assertEquals("abc123", PassengerQrCodec.passengerUid("  DEMO_QR_abc123  "))
    }

    @Test
    fun `una lectura vacia no es un pasajero`() {
        assertNull(PassengerQrCodec.passengerUid(null))
        assertNull(PassengerQrCodec.passengerUid("   "))
        assertNull(PassengerQrCodec.passengerUid("DEMO_QR_"))
    }
}
