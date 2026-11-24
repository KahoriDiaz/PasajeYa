package com.pasajeya.app.feature.claims.domain

/** Motivos de reclamo que el pasajero puede elegir. */
enum class ClaimCategory(val wire: String) {
    OVERCHARGE("cobro_incorrecto"),
    BENEFIT("medio_pasaje"),
    REFUSED("no_me_recogio"),
    TREATMENT("trato_del_conductor"),
    SAFETY("manejo_inseguro"),
    VEHICLE("estado_de_la_unidad"),
    OTHER("otro");

    companion object {
        fun fromWire(value: String?): ClaimCategory =
            entries.firstOrNull { it.wire == value } ?: OTHER
    }
}
