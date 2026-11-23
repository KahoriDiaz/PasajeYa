package com.pasajeya.app.feature.trip.domain

import com.pasajeya.app.feature.fare.domain.RouteGeo
import com.pasajeya.app.feature.fare.domain.RouteStop

/** Ruta cargada desde el catálogo local para la simulación de la demo. */
data class RoutePath(
    val id: String,
    val name: String,
    val stops: List<RouteStop>,
    val officialFareCents: Int = 130,
    val officialUniversityFareCents: Int = 65,
    val concessionaireName: String = ""
) {
    fun toRouteGeo(): RouteGeo = RouteGeo(
        id = id,
        name = name,
        stops = stops,
        officialFareCents = officialFareCents,
        officialUniversityFareCents = officialUniversityFareCents
    )
}

/** Ticket leído del QR estático que muestra la unidad del operador. */
data class BusQrTicket(
    val shiftId: String,
    val routeId: String? = null,
    val plate: String? = null
)

object BusQrCodec {
    private const val PREFIX = "PASAJEYA-BUS"
    private const val VERSION = "1"
    private const val SEPARATOR = "|"

    fun encode(shiftId: String, routeId: String, plate: String): String =
        listOf(PREFIX, VERSION, shiftId, routeId, plate).joinToString(SEPARATOR)

    fun parse(raw: String): BusQrTicket? {
        val parts = raw.trim().split(SEPARATOR)
        if (parts.size < 3 || parts[0] != PREFIX || parts[1] != VERSION) return null
        val shiftId = parts.getOrNull(2)?.takeIf { it.isNotBlank() } ?: return null
        return BusQrTicket(
            shiftId = shiftId,
            routeId = parts.getOrNull(3)?.takeIf { it.isNotBlank() },
            plate = parts.getOrNull(4)?.takeIf { it.isNotBlank() }
        )
    }
}
