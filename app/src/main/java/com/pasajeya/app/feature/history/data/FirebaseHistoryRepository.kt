package com.pasajeya.app.feature.history.data

import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.Source
import com.pasajeya.app.feature.history.domain.HistoryCursor
import com.pasajeya.app.feature.history.domain.HistoryDateRange
import com.pasajeya.app.feature.history.domain.HistoryDateRanges
import com.pasajeya.app.feature.history.domain.HistoryFilter
import com.pasajeya.app.feature.history.domain.HistoryPage
import com.pasajeya.app.feature.history.domain.HistoryRepository
import com.pasajeya.app.feature.history.domain.HistoryTrip
import com.pasajeya.app.feature.history.domain.OperatorShiftSummary
import com.pasajeya.app.feature.trip.data.toShift
import com.pasajeya.app.feature.trip.data.toTrip
import com.pasajeya.app.feature.trip.domain.model.Trip
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

@Singleton
class FirebaseHistoryRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) : HistoryRepository {

    override suspend fun loadPage(
        passengerUid: String,
        filter: HistoryFilter,
        cursor: HistoryCursor?
    ): HistoryPage {
        // Solo una igualdad en Firestore: asi no hace falta ningun indice compuesto.
        val query = firestore.collection("trips")
            .whereEqualTo("passengerUid", passengerUid)

        val range = HistoryDateRanges.range(filter)
        val snapshot = snapshotOf(query)
        var trips = snapshot.documents.mapNotNull { document ->
            document.toTrip()?.let { trip ->
                HistoryTrip(trip = trip, routeName = trip.routeId)
            }
        }.filter { item ->
            item.trip.status in PASSENGER_STATUSES && item.trip.inRange(range)
        }
        // Firestore no exige índice compuesto: se ordena en memoria por createdAt descendente.
        trips = trips.sortedByDescending { it.trip.startedAtMillis ?: 0L }
        // Aplicar paginación por cursor en memoria (sin orderBy en Firestore).
        var offset = 0
        if (cursor != null) {
            offset = trips.indexOfFirst { it.trip.id == cursor.tripId }
            if (offset >= 0) offset += 1
        }
        val pageTrips = trips.drop(offset).take(PAGE_SIZE)
        val lastTrip = pageTrips.lastOrNull()
        val routeNames = loadRouteNames(pageTrips.map { it.trip.routeId }.toSet())
        return HistoryPage(
            trips = pageTrips.map { item ->
                item.copy(routeName = routeNames[item.trip.routeId] ?: item.routeName)
            },
            nextCursor = if (
                pageTrips.size == PAGE_SIZE && lastTrip?.trip?.startedAtMillis != null
            ) {
                HistoryCursor(
                    startedAtMillis = lastTrip.trip.startedAtMillis,
                    tripId = lastTrip.trip.id
                )
            } else {
                null
            },
            isFromCache = snapshot.metadata.isFromCache
        )
    }

    override suspend fun loadSpentCents(
        passengerUid: String,
        filter: HistoryFilter
    ): Long {
        val query = firestore.collection("trips")
            .whereEqualTo("passengerUid", passengerUid)

        val range = HistoryDateRanges.range(filter)
        return snapshotOf(query).documents
            .mapNotNull { it.toTrip() }
            .filter { it.status == "closed" && it.inRange(range) }
            .sumOf { (it.fareBreakdown?.totalCents ?: 0).toLong() }
    }

    override suspend fun loadLatestOperatorShiftSummary(
        operatorUid: String
    ): OperatorShiftSummary? {
        val shiftQuery = firestore.collection("shifts")
            .whereEqualTo("operatorUid", operatorUid)
        val shift = snapshotOf(shiftQuery).documents
            .mapNotNull { it.toShift() }
            .maxByOrNull { it.startedAtMillis ?: 0L }
            ?: return null

        val tripsQuery = firestore.collection("trips")
            .whereEqualTo("operatorUid", operatorUid)
            .whereEqualTo("shiftId", shift.id)
        val trips = snapshotOf(tripsQuery).documents.mapNotNull { it.toTrip() }
            .sortedByDescending { it.startedAtMillis ?: 0L }
        val closed = trips.filter { it.status == "closed" }

        return OperatorShiftSummary(
            shift = shift,
            closedTrips = closed.size,
            reviewTrips = trips.count { it.status == "requires_review" },
            totalCents = closed.sumOf { it.fareBreakdown?.totalCents ?: 0 }
                .toLong()
        )
    }

    private suspend fun loadRouteNames(routeIds: Set<String>): Map<String, String> {
        if (routeIds.isEmpty()) return emptyMap()
        return runCatching {
            firestore.collection("routes")
                .whereIn(FieldPath.documentId(), routeIds.toList())
                .get()
                .await()
                .documents
                .associate { document ->
                    document.id to (document.getString("name") ?: document.id)
                }
        }.getOrDefault(emptyMap())
    }

    private suspend fun snapshotOf(query: Query): QuerySnapshot = try {
        query.get().await()
    } catch (error: FirebaseFirestoreException) {
        if (error.code != FirebaseFirestoreException.Code.UNAVAILABLE) {
            throw error
        }
        query.get(Source.CACHE).await()
    }

    /** El rango se compara en memoria para no exigir indices compuestos. */
    private fun Trip.inRange(range: HistoryDateRange?): Boolean {
        if (range == null) return true
        val startedAt = startedAtMillis ?: return false
        return startedAt >= range.startInclusiveMillis && startedAt < range.endExclusiveMillis
    }

    private companion object {
        const val PAGE_SIZE = 20

        val PASSENGER_STATUSES = listOf("closed", "requires_review")
    }
}
