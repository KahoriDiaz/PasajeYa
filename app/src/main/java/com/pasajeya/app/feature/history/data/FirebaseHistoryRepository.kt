package com.pasajeya.app.feature.history.data

import com.google.firebase.firestore.AggregateField
import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.Source
import com.google.firebase.Timestamp
import com.pasajeya.app.feature.history.domain.HistoryCursor
import com.pasajeya.app.feature.history.domain.HistoryDateRanges
import com.pasajeya.app.feature.history.domain.HistoryFilter
import com.pasajeya.app.feature.history.domain.HistoryPage
import com.pasajeya.app.feature.history.domain.HistoryRepository
import com.pasajeya.app.feature.history.domain.HistoryTrip
import com.pasajeya.app.feature.history.domain.OperatorShiftSummary
import com.pasajeya.app.feature.trip.data.toShift
import com.pasajeya.app.feature.trip.data.toTrip
import java.util.Date
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
        var query = firestore.collection("trips")
            .whereEqualTo("passengerUid", passengerUid)
            .whereIn("status", PASSENGER_STATUSES)
            .orderBy("startedAt", Query.Direction.DESCENDING)
            .orderBy(FieldPath.documentId(), Query.Direction.DESCENDING)
            .limit(PAGE_SIZE.toLong())

        HistoryDateRanges.range(filter)?.let { range ->
            query = query
                .whereGreaterThanOrEqualTo(
                    "startedAt",
                    Timestamp(Date(range.startInclusiveMillis))
                )
                .whereLessThan(
                    "startedAt",
                    Timestamp(Date(range.endExclusiveMillis))
                )
        }

        if (cursor != null) {
            query = query.startAfter(
                Timestamp(Date(cursor.startedAtMillis)),
                cursor.tripId
            )
        }

        val snapshot = snapshotOf(query)
        val trips = snapshot.documents.mapNotNull { document ->
            document.toTrip()?.let { trip ->
                HistoryTrip(trip = trip, routeName = trip.routeId)
            }
        }
        val routeNames = loadRouteNames(trips.map { it.trip.routeId }.toSet())
        val lastDocument = snapshot.documents.lastOrNull()
        val lastTrip = lastDocument?.toTrip()
        return HistoryPage(
            trips = trips.map { item ->
                item.copy(routeName = routeNames[item.trip.routeId] ?: item.routeName)
            },
            nextCursor = if (
                snapshot.documents.size == PAGE_SIZE &&
                lastTrip?.startedAtMillis != null
            ) {
                HistoryCursor(
                    startedAtMillis = lastTrip.startedAtMillis,
                    tripId = lastDocument.id
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
        var query = firestore.collection("trips")
            .whereEqualTo("passengerUid", passengerUid)
            .whereEqualTo("status", "closed")

        HistoryDateRanges.range(filter)?.let { range ->
            query = query
                .whereGreaterThanOrEqualTo(
                    "startedAt",
                    Timestamp(Date(range.startInclusiveMillis))
                )
                .whereLessThan(
                    "startedAt",
                    Timestamp(Date(range.endExclusiveMillis))
                )
        }

        val totalField = AggregateField.sum("fareBreakdown.totalCents")
        val snapshot = query
            .aggregate(totalField)
            .get(AggregateSource.SERVER)
            .await()
        return (snapshot.get(totalField) as? Number)?.toLong() ?: 0L
    }

    override suspend fun loadLatestOperatorShiftSummary(
        operatorUid: String
    ): OperatorShiftSummary? {
        val shiftQuery = firestore.collection("shifts")
            .whereEqualTo("operatorUid", operatorUid)
            .orderBy("startedAt", Query.Direction.DESCENDING)
            .limit(1)
        val shift = snapshotOf(shiftQuery).documents
            .firstOrNull()
            ?.toShift()
            ?: return null

        val tripsQuery = firestore.collection("trips")
            .whereEqualTo("operatorUid", operatorUid)
            .whereEqualTo("shiftId", shift.id)
            .orderBy("startedAt", Query.Direction.DESCENDING)
        val trips = snapshotOf(tripsQuery).documents.mapNotNull { it.toTrip() }
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

    private companion object {
        const val PAGE_SIZE = 20

        val PASSENGER_STATUSES = listOf("closed", "requires_review")
    }
}
