package com.pasajeya.app.feature.nfc

import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import com.google.firebase.auth.FirebaseAuth
import com.pasajeya.app.feature.trip.domain.NfcCredentialProtocol

/**
 * Servicio HCE del pasajero: el celular del pasajero se comporta como una tarjeta. Cuando el
 * celular del conductor le pide nuestro AID, responde con la credencial del usuario que tiene
 * la sesion abierta en ESTE celular (la misma que muestra su QR).
 */
class ApduHceService : HostApduService() {

    override fun processCommandApdu(apdu: ByteArray, extras: Bundle?): ByteArray {
        if (!NfcCredentialProtocol.isSelectOfOurAid(apdu)) return NfcCredentialProtocol.SW_NOT_FOUND
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid.isNullOrBlank()) return NfcCredentialProtocol.SW_NOT_FOUND
        return NfcCredentialProtocol.responseFor(uid)
    }

    override fun onDeactivated(reason: Int) {
        // No se requiere limpieza adicional.
    }
}
