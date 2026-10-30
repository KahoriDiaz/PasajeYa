package com.pasajeya.app.feature.fare.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.pasajeya.app.feature.fare.domain.FareRepository
import com.pasajeya.app.feature.fare.domain.RouteFareContext
import com.pasajeya.app.feature.fare.domain.RouteGeo
import com.pasajeya.app.feature.fare.domain.RouteStop
import com.pasajeya.app.feature.trip.domain.FareVersionInput
import com.pasajeya.app.feature.trip.domain.model.TripOperationException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

@Singleton
class FirebaseFareRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) : FareRepository {

    override suspend fun loadRouteFareContext(routeId: String): RouteFareContext {
        val routeSnap = firestore.collection("routes").document(routeId).get().await()
        if (!routeSnap.exists()) {
            throw TripOperationException("La ruta no existe.")
        }
        val stops = (routeSnap.get("stops") as? List<*>)
            .orEmpty()
            .mapNotNull { raw ->
                val map = raw as? Map<*, *> ?: return@mapNotNull null
                val lat = (map["lat"] as? Number)?.toDouble() ?: return@mapNotNull null
                val lng = (map["lng"] as? Number)?.toDouble() ?: return@mapNotNull null
                RouteStop(name = map["name"] as? String ?: "", lat = lat, lng = lng)
            }
        if (stops.isEmpty()) {
            throw TripOperationException("La ruta no tiene paraderos con coordenadas.")
        }
        val route = RouteGeo(
            id = routeSnap.id,
            name = routeSnap.getString("name").orEmpty(),
            stops = stops,
            officialFareCents = routeSnap.getLong("officialFareCents")?.toInt() ?: 0,
            officialUniversityFareCents =
                routeSnap.getLong("officialUniversityFareCents")?.toInt() ?: 0
        )

        val versionId = firestore.collection("config").document("currentFare")
            .get().await().getString("versionId")
            ?: throw TripOperationException("No hay una versión tarifaria vigente.")
        val versionSnap = firestore.collection("fareVersions").document(versionId)
            .get().await()
        if (!versionSnap.exists()) {
            throw TripOperationException("No hay una versión tarifaria vigente.")
        }
        val version = FareVersionInput(
            baseFarePerKmCents = versionSnap.getLong("baseFarePerKmCents")?.toInt()
                ?: throw TripOperationException("La versión tarifaria está incompleta."),
            fuelIndex = versionSnap.getDouble("fuelIndex") ?: 1.0,
            minFareCents = versionSnap.getLong("minFareCents")?.toInt() ?: 0,
            roundingStepCents = versionSnap.getLong("roundingStepCents")?.toInt()
                ?.takeIf { it > 0 } ?: 5
        )
        return RouteFareContext(route = route, version = version)
    }

    override fun observeUniversityBenefit(uid: String): Flow<Boolean> = callbackFlow {
        val registration = firestore.collection("users").document(uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val benefit = snapshot?.get("benefit") as? Map<*, *>
                val validUntil = (benefit?.get("validUntil") as? Timestamp)?.toDate()?.time
                val active = benefit?.get("status") == "approved" &&
                    benefit["kind"] == "university" &&
                    validUntil != null &&
                    validUntil > System.currentTimeMillis()
                trySend(active)
            }
        awaitClose { registration.remove() }
    }
}
