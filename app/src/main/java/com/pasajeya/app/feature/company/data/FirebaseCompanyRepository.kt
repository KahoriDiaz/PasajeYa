package com.pasajeya.app.feature.company.data

import com.google.firebase.firestore.FirebaseFirestore
import com.pasajeya.app.feature.company.domain.CompanyDashboard
import com.pasajeya.app.feature.company.domain.CompanyRepository
import com.pasajeya.app.feature.company.domain.CompanyUnit
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

/** Lee la flota, los viajes del día y lo cobrado de los conductores vinculados a la empresa. */
@Singleton
class FirebaseCompanyRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) : CompanyRepository {

    override suspend fun loadDashboard(companyUid: String): CompanyDashboard {
        val company = firestore.collection("companies").document(companyUid).get().await()
        val unitPriceCents = (company.get("subscriptionPerUnitCents") as? Number)?.toLong()
            ?: DEFAULT_UNIT_MONTHLY_CENTS

        val drivers = firestore.collection("users")
            .whereEqualTo("companyId", companyUid)
            .get()
            .await()
            .documents
        val units = drivers.map { doc ->
            CompanyUnit(
                driverUid = doc.id,
                driverName = doc.getString("fullName").orEmpty(),
                plate = doc.getString("plate") ?: doc.getString("vehiclePlate").orEmpty(),
                routeId = doc.getString("assignedRouteId").orEmpty()
            )
        }

        val startOfDayMillis = LocalDate.now()
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        var tripsToday = 0
        var chargedTodayCents = 0L
        units.map { it.driverUid }.chunked(WHERE_IN_LIMIT).forEach { chunk ->
            val trips = firestore.collection("trips")
                .whereIn("operatorUid", chunk)
                .get()
                .await()
                .documents
            for (trip in trips) {
                val startedAt = trip.getTimestamp("startedAt")?.toDate()?.time ?: continue
                if (startedAt < startOfDayMillis) continue
                tripsToday += 1
                if (trip.getString("status") == "closed") {
                    val breakdown = trip.get("fareBreakdown") as? Map<*, *>
                    chargedTodayCents += (breakdown?.get("totalCents") as? Number)?.toLong() ?: 0L
                }
            }
        }

        return CompanyDashboard(
            businessName = company.getString("businessName").orEmpty(),
            ruc = company.getString("ruc").orEmpty(),
            units = units,
            tripsToday = tripsToday,
            chargedTodayCents = chargedTodayCents,
            monthlySubscriptionCents = units.size * unitPriceCents
        )
    }

    private companion object {
        const val DEFAULT_UNIT_MONTHLY_CENTS = 3_000L
        const val WHERE_IN_LIMIT = 10
    }
}
