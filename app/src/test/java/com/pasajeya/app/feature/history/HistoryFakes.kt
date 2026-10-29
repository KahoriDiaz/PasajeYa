package com.pasajeya.app.feature.history

import com.pasajeya.app.feature.history.domain.HistoryCursor
import com.pasajeya.app.feature.history.domain.HistoryFilter
import com.pasajeya.app.feature.history.domain.HistoryPage
import com.pasajeya.app.feature.history.domain.HistoryRepository
import com.pasajeya.app.feature.history.domain.HistoryTrip
import com.pasajeya.app.feature.history.domain.OperatorShiftSummary
import com.pasajeya.app.feature.trip.domain.model.FareBenefit
import com.pasajeya.app.feature.trip.domain.model.FareBreakdown
import com.pasajeya.app.feature.trip.domain.model.Trip

data class HistoryPageRequest(
    val uid: String,
    val filter: HistoryFilter,
    val cursor: HistoryCursor?
)

class FakeHistoryRepository : HistoryRepository {
    var pageHandler: suspend (
        String,
        HistoryFilter,
        HistoryCursor?
    ) -> HistoryPage = { _, _, _ ->
        HistoryPage(emptyList(), null, false)
    }
    var spentHandler: suspend (String, HistoryFilter) -> Long = { _, _ -> 0L }
    var summaryHandler: suspend (String) -> OperatorShiftSummary? = { null }

    val pageRequests = mutableListOf<HistoryPageRequest>()
    val spentRequests = mutableListOf<HistoryFilter>()

    override suspend fun loadPage(
        passengerUid: String,
        filter: HistoryFilter,
        cursor: HistoryCursor?
    ): HistoryPage {
        pageRequests += HistoryPageRequest(passengerUid, filter, cursor)
        return pageHandler(passengerUid, filter, cursor)
    }

    override suspend fun loadSpentCents(
        passengerUid: String,
        filter: HistoryFilter
    ): Long {
        spentRequests += filter
        return spentHandler(passengerUid, filter)
    }

    override suspend fun loadLatestOperatorShiftSummary(
        operatorUid: String
    ): OperatorShiftSummary? = summaryHandler(operatorUid)
}

fun sampleHistoryTrip(
    id: String,
    status: String = "closed",
    totalCents: Int = 120,
    startedAtMillis: Long = 1_000L,
    percent: Int? = null
): HistoryTrip {
    val benefit = percent?.let {
        FareBenefit(
            kind = "university",
            percent = it,
            officialUniversityFareCents = 70,
            discountCents = 50
        )
    }
    return HistoryTrip(
        trip = Trip(
            id = id,
            passengerUid = "passenger-1",
            operatorUid = "operator-1",
            shiftId = "shift-1",
            routeId = "route-1",
            status = status,
            reviewReason = null,
            startedAtMillis = startedAtMillis,
            endedAtMillis = startedAtMillis + 60_000L,
            distanceMeters = 1_500.0,
            passengerFirstName = "Ana",
            passengerAge = 22,
            fareBreakdown = FareBreakdown(
                distanceMeters = 1_500.0,
                baseFarePerKmCents = 12,
                fuelIndex = 1.0,
                calculatedCents = 120,
                officialFareCents = 130,
                regularCents = 120,
                benefit = benefit,
                totalCents = totalCents,
                capped = false,
                minApplied = false,
                fareVersionId = "v1"
            )
        ),
        routeName = "Ruta 1"
    )
}
