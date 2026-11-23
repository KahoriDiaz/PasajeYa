package com.pasajeya.app.feature.company.data

import com.google.firebase.firestore.FirebaseFirestore
import com.pasajeya.app.feature.company.domain.CompanyDashboard
import com.pasajeya.app.feature.company.domain.CompanyRepository
import com.pasajeya.app.feature.company.domain.CompanyUnit
import com.pasajeya.app.feature.company.domain.SubscriptionPricing
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
                routeId = doc.getString("assignedRouteId").orEmpty(),
                verified = doc.getString("driverStatus") == DRIVER_VERIFIED,
                licenseRejected = doc.getString("licenseStatus") == LICENSE_REJECTED
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

        // Empresa con RUC = formal, asi que paga el precio con descuento.
        val isFormal = company.getString("ruc").orEmpty().isNotBlank()
        return CompanyDashboard(
            businessName = company.getString("businessName").orEmpty(),
            ruc = company.getString("ruc").orEmpty(),
            units = units,
            tripsToday = tripsToday,
            chargedTodayCents = chargedTodayCents,
            monthlySubscriptionCents = SubscriptionPricing.monthlyCents(units.size, isFormal),
            dailyPerUnitCents = SubscriptionPricing.dailyCentsFor(units.size, isFormal),
            discountPercent = if (isFormal) SubscriptionPricing.discountPercent(units.size) else 0,
            monthlySavingCents = if (isFormal) {
                SubscriptionPricing.monthlySavingCents(units.size)
            } else {
                0L
            }
        )
    }

    private companion object {
        const val WHERE_IN_LIMIT = 10
        const val DRIVER_VERIFIED = "verified_by_company"
        const val LICENSE_REJECTED = "RECHAZADO"
    }
}
