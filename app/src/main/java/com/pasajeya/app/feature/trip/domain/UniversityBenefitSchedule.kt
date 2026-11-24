package com.pasajeya.app.feature.trip.domain

import java.time.DayOfWeek
import java.time.LocalDateTime

/**
 * Horario del medio pasaje universitario: de lunes a sabado, desde las 05:00 hasta la
 * medianoche. Fuera de esa franja el estudiante paga pasaje completo, igual que en la
 * regla del transporte urbano.
 */
object UniversityBenefitSchedule {

    const val START_HOUR = 5

    fun isActiveAt(moment: LocalDateTime): Boolean {
        if (moment.dayOfWeek == DayOfWeek.SUNDAY) return false
        return moment.hour >= START_HOUR
    }

    fun isActiveNow(): Boolean = isActiveAt(LocalDateTime.now())
}
