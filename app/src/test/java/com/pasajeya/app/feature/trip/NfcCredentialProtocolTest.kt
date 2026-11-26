package com.pasajeya.app.feature.trip

import com.pasajeya.app.feature.trip.domain.NfcCredentialProtocol
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** El lector del conductor y la tarjeta emulada del pasajero tienen que entenderse. */
class NfcCredentialProtocolTest {

    @Test
    fun `el select que envia el conductor es el que reconoce el pasajero`() {
        assertTrue(NfcCredentialProtocol.isSelectOfOurAid(NfcCredentialProtocol.selectApdu()))
    }

    @Test
    fun `el select es un apdu valido con el aid binario`() {
        val apdu = NfcCredentialProtocol.selectApdu()
        assertArrayEquals(
            byteArrayOf(0x00, 0xA4.toByte(), 0x04, 0x00, 0x07, 0xA0.toByte(), 0x00, 0x00, 0x00, 0x01, 0x01, 0x02, 0x00),
            apdu
        )
    }

    @Test
    fun `el aid en texto ya no se acepta`() {
        val ascii = "A0000000010102".toByteArray()
        val apdu = byteArrayOf(0x00, 0xA4.toByte(), 0x04, 0x00, ascii.size.toByte()) + ascii
        assertFalse(NfcCredentialProtocol.isSelectOfOurAid(apdu))
    }

    @Test
    fun `la respuesta del pasajero da su uid`() {
        val response = NfcCredentialProtocol.responseFor("hugo123")
        assertEquals("hugo123", NfcCredentialProtocol.passengerUidFrom(response))
    }

    @Test
    fun `un error de la tarjeta no se convierte en pasajero`() {
        assertNull(NfcCredentialProtocol.passengerUidFrom(NfcCredentialProtocol.SW_NOT_FOUND))
        assertNull(NfcCredentialProtocol.passengerUidFrom(byteArrayOf(0x6A, 0x82.toByte())))
        assertNull(NfcCredentialProtocol.passengerUidFrom(null))
    }

    @Test
    fun `texto sin la credencial de pasajeya no se acepta`() {
        val other = "otra-app".toByteArray() + byteArrayOf(0x90.toByte(), 0x00)
        assertNull(NfcCredentialProtocol.passengerUidFrom(other))
    }
}
