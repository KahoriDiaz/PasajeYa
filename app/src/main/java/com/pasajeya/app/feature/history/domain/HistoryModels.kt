package com.pasajeya.app.feature.history.domain

import com.pasajeya.app.feature.trip.domain.model.Shift
import com.pasajeya.app.feature.trip.domain.model.Trip

enum class HistoryFilter {
    WEEK,
    MONTH,
    ALL
}

data class HistoryCursor(
    val startedAtMillis: Long,
    val tripId: String
)

data class HistoryTrip(
    val trip: Trip,
    val routeName: String
)

data class HistoryPage(
    val trips: List<HistoryTrip>,
    val nextCursor: HistoryCursor?,
    val isFromCache: Boolean
)

data class OperatorShiftSummary(
    val shift: Shift,
    val closedTrips: Int,
    val reviewTrips: Int,
    val totalCents: Long
)
