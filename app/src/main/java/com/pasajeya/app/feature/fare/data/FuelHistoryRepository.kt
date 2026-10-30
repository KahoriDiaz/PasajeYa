package com.pasajeya.app.feature.fare.data

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.pasajeya.app.feature.fare.domain.FuelRecord
import kotlinx.coroutines.tasks.await

class FuelHistoryRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    suspend fun getRecentFuelHistory(limitCount: Long = 5): List<FuelRecord> {
        return try {
            val snapshot = firestore.collection("fuelHistory")
                .orderBy("recordedAt", Query.Direction.DESCENDING)
                .limit(limitCount)
                .get()
                .await()

            snapshot.documents.mapNotNull { doc ->
                val price = doc.getDouble("price") ?: 0.0
                val fuelIndex = doc.getDouble("fuelIndex") ?: 1.0
                val changePercent = doc.getDouble("changePercent") ?: 0.0
                val source = doc.getString("source") ?: ""
                val recordedAt = doc.getTimestamp("recordedAt")?.toDate()

                FuelRecord(
                    price = price,
                    fuelIndex = fuelIndex,
                    changePercent = changePercent,
                    source = source,
                    recordedAt = recordedAt
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
