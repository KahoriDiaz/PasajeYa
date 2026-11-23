package com.pasajeya.app.feature.nfc

import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import com.google.firebase.auth.FirebaseAuth
import java.nio.charset.StandardCharsets

/** Servicio HCE del pasajero. Responde al SELECT AID con el identificador del usuario autenticado. */
class ApduHceService : HostApduService() {

    private companion object {
        private const val CLA_SELECT = 0x00
        private const val INS_SELECT = 0xA4
        private const val P1_SELECT_BY_NAME = 0x04
        private const val SW_SUCCESS_1 = 0x90
        private const val SW_SUCCESS_2 = 0x00
        private const val SW_ERROR_1 = 0x6A
        private const val SW_ERROR_2 = 0x82

        /** Mismo AID declarado en res/xml/apduservice.xml. */
        val AID = "A0000000010102".toByteArray(StandardCharsets.US_ASCII)
    }

    override fun processCommandApdu(apdu: ByteArray, extras: Bundle): ByteArray {
        if (apdu.size < 5) return errorResponse()
        if (apdu[0] != CLA_SELECT.toByte() ||
            apdu[1] != INS_SELECT.toByte() ||
            apdu[2] != P1_SELECT_BY_NAME.toByte()
        ) {
            return errorResponse()
        }

        val lc = apdu[3].toInt() and 0xFF
        if (lc != Companion.AID.size || apdu.size < 4 + lc) return errorResponse()

        val selectedAid = apdu.copyOfRange(4, 4 + lc)
        if (!selectedAid.contentEquals(Companion.AID)) return errorResponse()

        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid.isNullOrBlank()) return errorResponse()

        val payload = ("DEMO_QR_" + uid).toByteArray(StandardCharsets.UTF_8)
        return payload + byteArrayOf(SW_SUCCESS_1.toByte(), SW_SUCCESS_2.toByte())
    }

    override fun onDeactivated(reason: Int) {
        // No se requiere limpieza adicional.
    }

    private fun errorResponse(): ByteArray = byteArrayOf(0x6A.toByte(), 0x82.toByte())
}
