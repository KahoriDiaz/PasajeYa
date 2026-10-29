package com.pasajeya.app.feature.history.domain

interface HistoryRepository {
    suspend fun loadPage(
        passengerUid: String,
        filter: HistoryFilter,
        cursor: HistoryCursor?
    ): HistoryPage

    suspend fun loadSpentCents(
        passengerUid: String,
        filter: HistoryFilter
    ): Long

    suspend fun loadLatestOperatorShiftSummary(
        operatorUid: String
    ): OperatorShiftSummary?
}
