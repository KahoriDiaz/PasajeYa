package com.pasajeya.app.feature.trip.data

import android.location.Location
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.pasajeya.app.feature.fare.domain.FareEstimator
import com.pasajeya.app.feature.fare.domain.RouteFareContext
import com.pasajeya.app.feature.trip.domain.BusQrCodec
import com.pasajeya.app.feature.trip.domain.FareCalculator
import com.pasajeya.app.feature.trip.domain.FareVersionInput
import com.pasajeya.app.feature.trip.domain.RoutePath
import com.pasajeya.app.feature.trip.domain.TripPosition
import com.pasajeya.app.feature.trip.domain.TripRepository
import com.pasajeya.app.feature.trip.domain.model.EndShiftResult
import com.pasajeya.app.feature.trip.domain.model.FareBenefit
import com.pasajeya.app.feature.trip.domain.model.FareBreakdown
import com.pasajeya.app.feature.trip.domain.model.OperatorContext
import com.pasajeya.app.feature.trip.domain.model.QrToken
import com.pasajeya.app.feature.trip.domain.model.Shift
import com.pasajeya.app.feature.trip.domain.model.TapInResult
import com.pasajeya.app.feature.trip.domain.model.TapOutResult
import com.pasajeya.app.feature.trip.domain.model.Trip
import com.pasajeya.app.feature.trip.domain.model.TripOperationException
import com.pasajeya.app.feature.wallet.domain.WalletRepository
import com.pasajeya.app.feature.wallet.domain.WalletResult
import java.time.LocalDate
import java.time.Period
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

@Singleton
class FirebaseTripRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val functions: FirebaseFunctions,
    private val auth: FirebaseAuth,
    private val routeCatalog: RouteCatalog,
    internal val walletRepository: WalletRepository
) : TripRepository {

    override suspend fun issueQrToken(sessionId: String): QrToken {
        return try {
            val data = call("issueQrToken", mapOf("sessionId" to sessionId))
            QrToken(
                token = data.string("qrToken"),
                expiresAtMillis = data.long("expiresAt")
            )
        } catch (e: Exception) {
            QrToken(
                token = "DEMO_QR_${auth.currentUser?.uid ?: "user"}",
                expiresAtMillis = System.currentTimeMillis() + (10 * 60 * 1000)
            )
        }
    }

    override fun observeActiveTrip(passengerUid: String): Flow<Trip?> = callbackFlow {
        val registration = firestore.collection("trips")
            .whereEqualTo("passengerUid", passengerUid)
            .whereEqualTo("status", "open")
            .limit(1)
            .addSnapshotListener(MetadataChanges.EXCLUDE) { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snapshot?.documents?.firstOrNull()?.toTrip())
            }
        awaitClose { registration.remove() }
    }

    override fun observeTrip(tripId: String): Flow<Trip?> = callbackFlow {
        val registration = firestore.collection("trips")
            .document(tripId)
            .addSnapshotListener(MetadataChanges.EXCLUDE) { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snapshot?.toTrip())
            }
        awaitClose { registration.remove() }
    }

    override suspend fun loadRouteName(routeId: String): String? =
        loadRoutePath(routeId)?.name ?: runCatching {
            val doc = firestore.collection("routes").document(routeId).get().await()
            doc.getString("name")
        }.getOrNull()

    override suspend fun loadRoutePath(routeId: String): RoutePath? {
        routeCatalog.find(routeId)?.let { return it }
        return runCatching {
            val doc = firestore.collection("routes").document(routeId).get().await()
            if (!doc.exists()) return@runCatching null
            val stops = (doc.get("stops") as? List<*>).orEmpty().mapNotNull { raw ->
                val map = raw as? Map<*, *> ?: return@mapNotNull null
                val lat = (map["lat"] as? Number)?.toDouble() ?: return@mapNotNull null
                val lng = (map["lng"] as? Number)?.toDouble() ?: return@mapNotNull null
                com.pasajeya.app.feature.fare.domain.RouteStop(
                    name = map["name"] as? String ?: "",
                    lat = lat,
                    lng = lng
                )
            }
            if (stops.isEmpty()) return@runCatching null
            RoutePath(
                id = doc.id,
                name = doc.getString("name").orEmpty(),
                stops = stops,
                officialFareCents = doc.getLong("officialFareCents")?.toInt() ?: 130,
                officialUniversityFareCents =
                    doc.getLong("officialUniversityFareCents")?.toInt() ?: 65
            )
        }.getOrNull()
    }

    override suspend fun loadShift(shiftId: String): Shift? =
        awaitDocument("shifts", shiftId)?.toShift()

    override suspend fun findOpenTripForPassenger(
        passengerUid: String
    ): Trip? {
        return firestore.collection("trips")
            .whereEqualTo("passengerUid", passengerUid)
            .whereEqualTo("status", "open")
            .limit(1)
            .get()
            .await()
            .documents
            .firstOrNull()
            ?.toTrip()
    }

    override fun observeShiftTrips(shiftId: String): Flow<List<Trip>> = callbackFlow {
        val registration = firestore.collection("trips")
            .whereEqualTo("shiftId", shiftId)
            .addSnapshotListener(MetadataChanges.EXCLUDE) { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(
                    snapshot?.documents
                        .orEmpty()
                        .mapNotNull { it.toTrip() }
                )
            }
        awaitClose { registration.remove() }
    }

    override suspend fun updateSimulation(
        shiftId: String,
        routeId: String,
        stopIndex: Int,
        position: TripPosition,
        enabled: Boolean
    ) {
        val route = loadRoutePath(routeId)
        val stopName = route?.stops?.getOrNull(stopIndex)?.name
        firestore.collection("shifts").document(shiftId).update(
            mapOf(
                "simulationEnabled" to enabled,
                "simulatedStopIndex" to stopIndex,
                "simulatedLat" to position.latitude,
                "simulatedLng" to position.longitude,
                "simulatedStopName" to stopName,
                "simulatedUpdatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
    }

    override suspend fun loadOperatorContext(sessionId: String): OperatorContext {
        return try {
            val data = call("getOperatorContext", mapOf("sessionId" to sessionId))
            OperatorContext(
                plate = data.string("plate"),
                routeIds = (data["routeIds"] as? List<*>)
                    .orEmpty()
                    .filterIsInstance<String>(),
                status = data.string("status").ifBlank { "unverified" }
            )
        } catch (e: Exception) {
            val currentUid = auth.currentUser?.uid
            val userDoc = if (currentUid != null) {
                runCatching { firestore.collection("users").document(currentUid).get().await() }.getOrNull()
            } else null

            val userPlate = userDoc?.getString("plate") ?: "V1A-824"
            val routesList = (userDoc?.get("assignedRoutes") as? List<*>)
                ?.filterIsInstance<String>()
                ?.takeIf { it.isNotEmpty() }
                ?: listOf("R-CAY-01-IDA", "R-CAY-01-VUELTA", "R-GRAU-C-IDA", "R-GRAU-C-VUELTA")

            OperatorContext(
                plate = userPlate,
                routeIds = routesList,
                status = "verified"
            )
        }
    }

    override fun observeActiveShift(operatorUid: String): Flow<Shift?> = callbackFlow {
        val registration = firestore.collection("shifts")
            .whereEqualTo("operatorUid", operatorUid)
            .whereEqualTo("status", "active")
            .limit(1)
            .addSnapshotListener(MetadataChanges.EXCLUDE) { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snapshot?.documents?.firstOrNull()?.toShift())
            }
        awaitClose { registration.remove() }
    }

    override fun observeOpenTrips(shiftId: String): Flow<List<Trip>> = callbackFlow {
        val registration = firestore.collection("trips")
            .whereEqualTo("shiftId", shiftId)
            .whereEqualTo("status", "open")
            .addSnapshotListener(MetadataChanges.EXCLUDE) { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val trips = snapshot?.documents
                    .orEmpty()
                    .mapNotNull { it.toTrip() }
                    .sortedBy { it.startedAtMillis ?: Long.MAX_VALUE }
                trySend(trips)
            }
        awaitClose { registration.remove() }
    }

    override fun observeShift(shiftId: String): Flow<Shift?> = callbackFlow {
        val registration = firestore.collection("shifts")
            .document(shiftId)
            .addSnapshotListener(MetadataChanges.EXCLUDE) { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snapshot?.toShift())
            }
        awaitClose { registration.remove() }
    }

    override suspend fun startShift(sessionId: String, routeId: String): Shift {
        return try {
            val data = call(
                "startShift",
                mapOf("sessionId" to sessionId, "routeId" to routeId)
            )
            val shiftId = data.string("shiftId")
            awaitDocument("shifts", shiftId)?.toShift()
                ?: Shift(
                    id = shiftId,
                    operatorUid = auth.currentUser?.uid ?: "",
                    plate = "V1A-824",
                    routeId = routeId,
                    startedAtMillis = data.longOrNull("startedAt"),
                    endedAtMillis = null,
                    status = "active"
                )
        } catch (e: Exception) {
            val shiftId = "shift_${System.currentTimeMillis()}"
            val currentUid = auth.currentUser?.uid ?: ""
            val plate = runCatching {
                firestore.collection("operators").document(currentUid).get().await()
                    .getString("plate")
            }.getOrNull() ?: "V1A-824"
            val firstStop = loadRoutePath(routeId)?.stops?.firstOrNull()
            firestore.collection("shifts").document(shiftId).set(
                mapOf(
                    "operatorUid" to currentUid,
                    "plate" to plate,
                    "routeId" to routeId,
                    "startedAt" to FieldValue.serverTimestamp(),
                    "status" to "active",
                    "simulationEnabled" to true,
                    "simulatedStopIndex" to 0,
                    "simulatedLat" to firstStop?.lat,
                    "simulatedLng" to firstStop?.lng,
                    "simulatedStopName" to firstStop?.name,
                    "simulatedUpdatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Shift(
                id = shiftId,
                operatorUid = currentUid,
                plate = plate,
                routeId = routeId,
                startedAtMillis = System.currentTimeMillis(),
                endedAtMillis = null,
                status = "active",
                simulationEnabled = true,
                simulatedStopIndex = 0,
                simulatedLatitude = firstStop?.lat,
                simulatedLongitude = firstStop?.lng,
                simulatedStopName = firstStop?.name,
                simulatedUpdatedAtMillis = System.currentTimeMillis()
            )
        }
    }

    override suspend fun endShift(sessionId: String, shiftId: String): EndShiftResult {
        return try {
            val data = call(
                "endShift",
                mapOf("sessionId" to sessionId, "shiftId" to shiftId)
            )
            EndShiftResult(
                shiftId = shiftId,
                endedAtMillis = data.longOrNull("endedAt"),
                reviewCount = (data.longOrNull("tripsRequiresReview") ?: 0L).toInt()
            )
        } catch (e: Exception) {
            firestore.collection("shifts").document(shiftId).update(
                mapOf(
                    "endedAt" to FieldValue.serverTimestamp(),
                    "status" to "completed"
                )
            ).await()
            EndShiftResult(
                shiftId = shiftId,
                endedAtMillis = System.currentTimeMillis(),
                reviewCount = 0
            )
        }
    }

    override suspend fun tapIn(
        sessionId: String,
        shiftId: String,
        qrToken: String,
        position: TripPosition
    ): TapInResult {
        return try {
            val data = call(
                "tapIn",
                mapOf(
                    "sessionId" to sessionId,
                    "shiftId" to shiftId,
                    "qrToken" to qrToken,
                    "lat" to position.latitude,
                    "lng" to position.longitude,
                    "accuracy" to position.accuracy
                )
            )
            TapInResult(
                tripId = data.string("tripId"),
                passengerFirstName = data.string("firstName"),
                passengerAge = data.longOrNull("age")?.toInt()
            )
        } catch (e: Exception) {
            val currentUid = auth.currentUser?.uid ?: "passenger"
            val tripId = "trip_${System.currentTimeMillis()}"

            firestore.collection("trips").document(tripId).set(
                mapOf(
                    "passengerUid" to currentUid,
                    "operatorUid" to (auth.currentUser?.uid ?: ""),
                    "shiftId" to shiftId,
                    "routeId" to "R-CAY-01-IDA",
                    "status" to "open",
                    "startedAt" to FieldValue.serverTimestamp(),
                    "originLat" to position.latitude,
                    "originLng" to position.longitude,
                    "passengerFirstName" to (auth.currentUser?.displayName ?: "Estudiante")
                )
            ).await()

            TapInResult(
                tripId = tripId,
                passengerFirstName = auth.currentUser?.displayName ?: "Estudiante",
                passengerAge = null
            )
        }
    }

    override suspend fun tapOut(
        sessionId: String,
        tripId: String,
        position: TripPosition
    ): TapOutResult {
        return try {
            val data = call(
                "tapOut",
                mapOf(
                    "sessionId" to sessionId,
                    "tripId" to tripId,
                    "lat" to position.latitude,
                    "lng" to position.longitude,
                    "accuracy" to position.accuracy
                )
            )
            val fareBreakdownData = data["fareBreakdown"] as? Map<*, *>
            TapOutResult(
                tripId = data.string("tripId"),
                status = data.string("status"),
                distanceMeters = data.doubleOrNull("distanceMeters") ?: 0.0,
                requiresReview = data["requiresReview"] == true,
                fareBreakdown = fareBreakdownData?.let {
                    @Suppress("UNCHECKED_CAST")
                    (it as Map<String, Any?>).toFareBreakdown()
                }
            )
        } catch (e: Exception) {
            val currentUid = auth.currentUser?.uid ?: "passenger"
            val tripDoc = firestore.collection("trips").document(tripId).get().await()

            val originLat = tripDoc.getDouble("originLat") ?: position.latitude
            val originLng = tripDoc.getDouble("originLng") ?: position.longitude

            val locResults = FloatArray(1)
            Location.distanceBetween(originLat, originLng, position.latitude, position.longitude, locResults)
            val distanceMeters = locResults[0].toDouble().coerceAtLeast(3500.0)

            val userDoc = firestore.collection("users").document(currentUid).get().await()
            val benefitMap = userDoc.get("benefit") as? Map<*, *>
            val isUniversity = benefitMap?.get("status")?.toString()?.uppercase() == "APPROVED"

            val fareVersion = FareVersionInput(
                baseFarePerKmCents = 8,
                minFareCents = 130,
                maxFareCents = 200,
                fuelIndex = 1.13,
                roundingStepCents = 10
            )
            val result = FareCalculator.computeFare(
                distanceMeters = distanceMeters,
                version = fareVersion,
                hasUniversityBenefit = isUniversity
            )

            val fareBenefit = if (isUniversity) {
                FareBenefit(
                    kind = "university",
                    percent = 50,
                    officialUniversityFareCents = result.totalCents,
                    discountCents = result.regularCents - result.totalCents
                )
            } else null

            val fareBreakdown = FareBreakdown(
                distanceMeters = distanceMeters,
                baseFarePerKmCents = fareVersion.baseFarePerKmCents,
                fuelIndex = fareVersion.fuelIndex,
                calculatedCents = result.rawCalculatedCents.toInt(),
                officialFareCents = result.totalCents,
                regularCents = result.regularCents,
                benefit = fareBenefit,
                totalCents = result.totalCents,
                capped = result.capped,
                minApplied = result.minApplied,
                fareVersionId = "v1-arequipa-continuous"
            )

            firestore.collection("trips").document(tripId).update(
                mapOf(
                    "status" to "closed",
                    "endedAt" to FieldValue.serverTimestamp(),
                    "distanceMeters" to distanceMeters,
                    "fareBreakdown" to mapOf(
                        "distanceMeters" to distanceMeters,
                        "calculatedCents" to result.rawCalculatedCents.toInt(),
                        "officialFareCents" to result.totalCents,
                        "regularCents" to result.regularCents,
                        "totalCents" to result.totalCents,
                        "capped" to result.capped,
                        "minApplied" to result.minApplied,
                        "fareVersionId" to "v1-arequipa-continuous"
                    )
                )
            ).await()

            TapOutResult(
                tripId = tripId,
                status = "closed",
                distanceMeters = distanceMeters,
                requiresReview = false,
                fareBreakdown = fareBreakdown
            )
        }
    }

    override suspend fun passengerTapIn(
        sessionId: String,
        busQr: String,
        position: TripPosition
    ): TapInResult {
        val uid = auth.currentUser?.uid
            ?: throw TripOperationException("No hay una cuenta activa.")
        val ticket = BusQrCodec.parse(busQr)
            ?: throw TripOperationException("El QR no corresponde a una unidad de PasajeYa.")
        val shift = loadShift(ticket.shiftId)
            ?: throw TripOperationException("La unidad ya no tiene un turno activo.")
        if (!shift.isActive) throw TripOperationException("La unidad ya terminó su turno.")

        val existing = firestore.collection("trips")
            .whereEqualTo("passengerUid", uid)
            .whereEqualTo("shiftId", shift.id)
            .whereEqualTo("status", "open")
            .limit(1)
            .get()
            .await()
            .documents
            .firstOrNull()
            ?.toTrip()
        if (existing != null) {
            return TapInResult(
                tripId = existing.id,
                passengerFirstName = existing.passengerFirstName,
                passengerAge = existing.passengerAge,
                fareType = existing.fareType
            )
        }

        val route = loadRoutePath(shift.routeId)
            ?: throw TripOperationException("No pudimos cargar la ruta de la unidad.")
        val userDoc = runCatching {
            firestore.collection("users").document(uid).get().await()
        }.getOrNull()
        val isUniversity = userDoc?.isUniversityEligible() == true
        val firstName = userDoc?.getString("firstName")
            ?.takeIf { it.isNotBlank() }
            ?: auth.currentUser?.displayName
            ?.takeIf { it.isNotBlank() }
            ?: "Pasajero"
        val age = userDoc?.getString("birthDate")?.ageFromBirthDate()
        val boardingIndex = shift.simulatedStopIndex
            .takeIf { it in route.stops.indices }
            ?: nearestStopIndex(route, position)
        val boardingPosition = route.stops.getOrNull(boardingIndex)
        val tripId = "trip_${System.currentTimeMillis()}"
        val fareType = if (isUniversity) "university" else "regular"

        firestore.collection("trips").document(tripId).set(
            mapOf(
                "passengerUid" to uid,
                "operatorUid" to shift.operatorUid,
                "shiftId" to shift.id,
                "routeId" to route.id,
                "status" to "open",
                "startedAt" to FieldValue.serverTimestamp(),
                "startLocation" to mapOf(
                    "lat" to (boardingPosition?.lat ?: position.latitude),
                    "lng" to (boardingPosition?.lng ?: position.longitude)
                ),
                "originLat" to (boardingPosition?.lat ?: position.latitude),
                "originLng" to (boardingPosition?.lng ?: position.longitude),
                "boardingStopIndex" to boardingIndex,
                "boardingStopName" to boardingPosition?.name,
                "fareType" to fareType,
                "passengerFirstName" to firstName,
                "passengerAge" to age,
                "heldAmountCents" to FareEstimator.SAFETY_CEILING_CENTS,
                "fareVersionId" to "demo-distance-v1",
                "routeFareSnapshot" to mapOf(
                    "officialFareCents" to route.officialFareCents,
                    "officialUniversityFareCents" to route.officialUniversityFareCents,
                    "concessionaireName" to route.concessionaireName
                )
            )
        ).await()

        prepareWalletHold(uid)
        return TapInResult(
            tripId = tripId,
            passengerFirstName = firstName,
            passengerAge = age,
            fareType = fareType
        )
    }

    override suspend fun passengerTapOut(
        sessionId: String,
        busQr: String,
        position: TripPosition
    ): TapOutResult {
        val uid = auth.currentUser?.uid
            ?: throw TripOperationException("No hay una cuenta activa.")
        val ticket = BusQrCodec.parse(busQr)
            ?: throw TripOperationException("El QR no corresponde a una unidad de PasajeYa.")
        val shift = loadShift(ticket.shiftId)
            ?: throw TripOperationException("La unidad ya no tiene un turno activo.")

        val tripDoc = firestore.collection("trips")
            .whereEqualTo("passengerUid", uid)
            .whereEqualTo("shiftId", shift.id)
            .whereEqualTo("status", "open")
            .limit(1)
            .get()
            .await()
            .documents
            .firstOrNull()
            ?: throw TripOperationException("No hay un viaje activo para esta unidad.")
        val trip = tripDoc.toTrip() ?: throw TripOperationException("No pudimos leer el viaje.")
        val route = loadRoutePath(trip.routeId)
            ?: throw TripOperationException("No pudimos cargar la ruta del viaje.")
        val boardingIndex = tripDoc.getLong("boardingStopIndex")?.toInt()
            ?.takeIf { it in route.stops.indices }
            ?: nearestStopIndex(route, position)
        val alightingIndex = shift.simulatedStopIndex
            .takeIf { it in route.stops.indices }
            ?: nearestStopIndex(route, position)
        val alightingPosition = route.stops[alightingIndex]
        val distanceMeters = FareEstimator.routeDistanceMeters(
            route.stops,
            boardingIndex,
            alightingIndex
        )
        val userDoc = runCatching {
            firestore.collection("users").document(uid).get().await()
        }.getOrNull()
        val isUniversity = userDoc?.isUniversityEligible() == true ||
            trip.fareType == "university"
        val context = RouteFareContext(
            route = route.toRouteGeo(),
            version = demoFareVersion()
        )
        val result = FareEstimator.fareForDistance(context, distanceMeters, isUniversity)
        val breakdown = result.toBreakdown(route, isUniversity)

        // Liquidación atómica en Firestore: liberar hold + descontar tarifa definitiva.
        val heldAmountCents = tripDoc.getLong("heldAmountCents")?.toInt() ?: 0
        val finalFareCents = breakdown.totalCents
        firestore.runTransaction { transaction ->
            val userSnapshot = transaction.get(firestore.collection("users").document(uid))
            val currentBalance = userSnapshot.getLong("walletBalanceCents") ?: 0L
            val currentHold = userSnapshot.getLong("walletHeldCents") ?: 0L
            val newBalance = currentBalance - finalFareCents
            val newHold = (currentHold - heldAmountCents).coerceAtLeast(0)
            if (newBalance < 0) {
                throw Exception("Saldo insuficiente para liquidar el viaje.")
            }
            transaction.update(userSnapshot.reference, mapOf(
                "walletBalanceCents" to newBalance,
                "walletHeldCents" to newHold
            ))
            null
        }.await()

        firestore.collection("trips").document(trip.id).update(
            mapOf(
                "status" to "closed",
                "endedAt" to FieldValue.serverTimestamp(),
                "endLocation" to mapOf(
                    "lat" to alightingPosition.lat,
                    "lng" to alightingPosition.lng
                ),
                "alightingStopIndex" to alightingIndex,
                "alightingStopName" to alightingPosition.name,
                "distanceMeters" to distanceMeters,
                "usedPoints" to (alightingIndex - boardingIndex).coerceAtLeast(0),
                "coverage" to "simulated",
                "fareBreakdown" to breakdown.toFirestoreMap()
            )
        ).await()

        chargeWallet(uid, breakdown.totalCents)

        return TapOutResult(
            tripId = trip.id,
            status = "closed",
            distanceMeters = distanceMeters,
            requiresReview = false,
            fareBreakdown = breakdown
        )
    }

    override suspend fun estimateFare(
        sessionId: String,
        routeId: String,
        boardingStopIndex: Int,
        alightingStopIndex: Int
    ): FareBreakdown {
        return try {
            val data = call(
                "estimateFare",
                mapOf(
                    "sessionId" to sessionId,
                    "routeId" to routeId,
                    "boardingStopIndex" to boardingStopIndex,
                    "alightingStopIndex" to alightingStopIndex
                )
            )
            (data as Map<String, Any?>).toFareBreakdown()
                ?: throw TripOperationException("No se pudo obtener la estimación.")
        } catch (e: Exception) {
            val stopsCount = kotlin.math.abs(alightingStopIndex - boardingStopIndex).coerceAtLeast(1)
            val distanceMeters = stopsCount * 1200.0

            val currentUid = auth.currentUser?.uid ?: ""
            val userDoc = runCatching { firestore.collection("users").document(currentUid).get().await() }.getOrNull()
            val isUniversity = userDoc?.isUniversityEligible() == true

            val fareVersion = FareVersionInput(minFareCents = 100, maxFareCents = 160, fuelIndex = 1.13)
            val result = FareCalculator.computeFare(distanceMeters, fareVersion, isUniversity)

            FareBreakdown(
                distanceMeters = distanceMeters,
                baseFarePerKmCents = fareVersion.baseFarePerKmCents,
                fuelIndex = fareVersion.fuelIndex,
                calculatedCents = result.rawCalculatedCents.toInt(),
                officialFareCents = result.totalCents,
                regularCents = result.regularCents,
                benefit = if (isUniversity) {
                    FareBenefit("university", 50, result.totalCents, result.regularCents - result.totalCents)
                } else null,
                totalCents = result.totalCents,
                capped = result.capped,
                minApplied = result.minApplied,
                fareVersionId = "v1-arequipa-continuous"
            )
        }
    }

    private suspend fun call(
        name: String,
        payload: Map<String, Any?>
    ): Map<String, Any?> {
        val callable = functions.getHttpsCallable(name)
        return try {
            @Suppress("UNCHECKED_CAST")
            val result = callable.call(payload).await().getData() as? Map<String, Any?>
            result ?: throw TripOperationException("Respuesta inválida del servidor.")
        } catch (error: FirebaseFunctionsException) {
            val message = when (error.code) {
                FirebaseFunctionsException.Code.UNAUTHENTICATED ->
                    "Tu sesión ya no es válida. Vuelve a iniciar sesión."
                FirebaseFunctionsException.Code.UNAVAILABLE ->
                    "No hay conexión con el servidor. Revisa tu internet."
                else -> error.message?.takeIf { it.isNotBlank() }
            }
            throw TripOperationException(
                message ?: "No se pudo completar la operación.",
                error
            )
        }
    }

    private suspend fun awaitDocument(
        collection: String,
        id: String
    ): DocumentSnapshot? = runCatching {
        firestore.collection(collection).document(id).get().await()
    }.getOrNull()
}

/** El descuento universitario aplica si el perfil tiene beneficio verificado, sin depender de sesiones remotas. */
private fun DocumentSnapshot.isUniversityEligible(): Boolean {
    val benefitType = getString("benefitType") ?: ""
    val status = getString("universityStatus") ?: ""
    val cardCode = getString("universityCardCode") ?: ""

    if (benefitType == "university" && status == "VERIFIED" && cardCode.isNotBlank()) {
        val expiry = get("universityCardExpiryDate") as? Timestamp
        if (expiry != null && expiry.toDate().time <= System.currentTimeMillis()) {
            return false
        }
        return true
    }

    val benefit = get("benefit") as? Map<*, *>
    val legacyStatus = benefit?.get("status")?.toString()?.lowercase()
    val legacyKind = benefit?.get("kind")?.toString()?.lowercase()
    val validUntil = (benefit?.get("validUntil") as? Timestamp)?.toDate()?.time
    return legacyStatus == "approved" &&
        legacyKind == "university" &&
        validUntil != null &&
        validUntil > System.currentTimeMillis()
}

private fun String.ageFromBirthDate(): Int? = runCatching {
    val birthDate = LocalDate.parse(this)
    Period.between(birthDate, LocalDate.now()).years.takeIf { it in 0..120 }
}.getOrNull()

private fun nearestStopIndex(route: RoutePath, position: TripPosition): Int {
    if (route.stops.isEmpty()) return 0
    return route.stops.indices.minByOrNull { index ->
        val stop = route.stops[index]
        com.pasajeya.app.core.util.Haversine.distanceMeters(
            position.latitude,
            position.longitude,
            stop.lat,
            stop.lng
        )
    } ?: 0
}

private fun demoFareVersion(): FareVersionInput = FareVersionInput(
    baseFarePerKmCents = 12,
    minFareCents = 50,
    maxFareCents = 0,
    fuelIndex = 1.0,
    roundingStepCents = 5
)

private fun com.pasajeya.app.feature.trip.domain.FareResult.toBreakdown(
    route: RoutePath,
    isUniversity: Boolean
): FareBreakdown {
    val modelBenefit = benefit?.let { benefit ->
        FareBenefit(
            kind = "university",
            percent = benefit.percent,
            officialUniversityFareCents = route.officialUniversityFareCents,
            discountCents = benefit.discountCents,
            isStudent = true
        )
    }
    return FareBreakdown(
        distanceMeters = distanceMeters,
        baseFarePerKmCents = demoFareVersion().baseFarePerKmCents,
        fuelIndex = demoFareVersion().fuelIndex,
        calculatedCents = rawCalculatedCents.toInt(),
        officialFareCents = route.officialFareCents,
        regularCents = regularCents,
        benefit = if (isUniversity) modelBenefit else null,
        totalCents = totalCents,
        capped = capped,
        minApplied = minApplied,
        fareVersionId = "demo-distance-v1"
    )
}

private fun FareBreakdown.toFirestoreMap(): Map<String, Any?> = mapOf(
    "distanceMeters" to distanceMeters,
    "baseFarePerKmCents" to baseFarePerKmCents,
    "fuelIndex" to fuelIndex,
    "calculatedCents" to calculatedCents,
    "officialFareCents" to officialFareCents,
    "regularCents" to regularCents,
    "benefit" to benefit?.let {
        mapOf(
            "kind" to it.kind,
            "percent" to it.percent,
            "officialUniversityFareCents" to it.officialUniversityFareCents,
            "discountCents" to it.discountCents
        )
    },
    "totalCents" to totalCents,
    "capped" to capped,
    "minApplied" to minApplied,
    "fareVersionId" to fareVersionId
)

private suspend fun FirebaseTripRepository.prepareWalletHold(uid: String) {
    walletRepository.release(uid)
    walletRepository.hold(uid, FareEstimator.SAFETY_CEILING_CENTS)
}

private suspend fun FirebaseTripRepository.chargeWallet(uid: String, finalCents: Int) {
    when (walletRepository.charge(uid, finalCents)) {
        is WalletResult.Success -> Unit
        is WalletResult.Failure -> {
            val held = walletRepository.hold(uid, finalCents)
            if (held is WalletResult.Success) {
                walletRepository.charge(uid, finalCents)
            }
        }
    }
}

internal fun DocumentSnapshot.toTrip(): Trip? {
    if (!exists()) return null
    val fareBreakdownData = get("fareBreakdown") as? Map<*, *>
    return Trip(
        id = id,
        passengerUid = getString("passengerUid").orEmpty(),
        operatorUid = getString("operatorUid").orEmpty(),
        shiftId = getString("shiftId").orEmpty(),
        routeId = getString("routeId").orEmpty(),
        status = getString("status").orEmpty(),
        reviewReason = getString("reviewReason"),
        startedAtMillis = getTimestamp("startedAt")?.toDate()?.time,
        endedAtMillis = getTimestamp("endedAt")?.toDate()?.time,
        distanceMeters = getDouble("distanceMeters") ?: 0.0,
        passengerFirstName = getString("passengerFirstName").orEmpty(),
        passengerAge = getLong("passengerAge")?.toInt(),
        fareType = getString("fareType") ?: "regular",
        boardingStopIndex = getLong("boardingStopIndex")?.toInt(),
        alightingStopIndex = getLong("alightingStopIndex")?.toInt(),
        heldAmountCents = getLong("heldAmountCents")?.toInt() ?: 0,
        fareBreakdown = fareBreakdownData?.let {
            @Suppress("UNCHECKED_CAST")
            (it as Map<String, Any?>).toFareBreakdown()
        }
    )
}

private fun Map<String, Any?>.toFareBreakdown(): FareBreakdown? {
    val benefitData = this["benefit"] as? Map<*, *>
    return FareBreakdown(
        distanceMeters = doubleOrNull("distanceMeters") ?: 0.0,
        baseFarePerKmCents = longOrNull("baseFarePerKmCents")?.toInt()?.takeIf { it > 0 } ?: 8,
        fuelIndex = doubleOrNull("fuelIndex") ?: 1.0,
        calculatedCents = longOrNull("calculatedCents")?.toInt() ?: 0,
        officialFareCents = longOrNull("officialFareCents")?.toInt() ?: 0,
        regularCents = longOrNull("regularCents")?.toInt() ?: 0,
        benefit = benefitData?.let {
            @Suppress("UNCHECKED_CAST")
            val b = it as Map<String, Any?>
            FareBenefit(
                kind = b.string("kind"),
                percent = b.longOrNull("percent")?.toInt() ?: 0,
                officialUniversityFareCents = b.longOrNull("officialUniversityFareCents")?.toInt() ?: 0,
                discountCents = b.longOrNull("discountCents")?.toInt() ?: 0
            )
        },
        totalCents = longOrNull("totalCents")?.toInt() ?: 0,
        capped = this["capped"] == true,
        minApplied = this["minApplied"] == true,
        fareVersionId = string("fareVersionId")
    )
}

internal fun DocumentSnapshot.toShift(): Shift? {
    if (!exists()) return null
    return Shift(
        id = id,
        operatorUid = getString("operatorUid").orEmpty(),
        plate = getString("plate").orEmpty(),
        routeId = getString("routeId").orEmpty(),
        startedAtMillis = getTimestamp("startedAt")?.toDate()?.time,
        endedAtMillis = getTimestamp("endedAt")?.toDate()?.time,
        status = getString("status").orEmpty(),
        simulationEnabled = getBoolean("simulationEnabled") ?: false,
        simulatedStopIndex = getLong("simulatedStopIndex")?.toInt() ?: 0,
        simulatedLatitude = getDouble("simulatedLat"),
        simulatedLongitude = getDouble("simulatedLng"),
        simulatedStopName = getString("simulatedStopName"),
        simulatedUpdatedAtMillis = getTimestamp("simulatedUpdatedAt")?.toDate()?.time
    )
}

private fun Map<String, Any?>.string(key: String): String =
    (this[key] as? String).orEmpty()

private fun Map<String, Any?>.long(key: String): Long =
    longOrNull(key) ?: 0L

private fun Map<String, Any?>.longOrNull(key: String): Long? = when (val value = this[key]) {
    is Number -> value.toLong()
    is String -> value.toLongOrNull()
    else -> null
}

private fun Map<String, Any?>.doubleOrNull(key: String): Double? = when (val value = this[key]) {
    is Number -> value.toDouble()
    is String -> value.toDoubleOrNull()
    else -> null
}

