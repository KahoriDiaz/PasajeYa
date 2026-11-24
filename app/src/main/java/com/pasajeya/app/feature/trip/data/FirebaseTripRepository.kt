package com.pasajeya.app.feature.trip.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.pasajeya.app.feature.fare.domain.FareEstimator
import com.pasajeya.app.feature.fare.domain.FareRepository
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
    internal val walletRepository: WalletRepository,
    private val fareRepository: FareRepository
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
        loadRoutePath(routeId)?.name?.takeIf { it.isNotBlank() } ?: runCatching {
            val doc = firestore.collection("routes").document(routeId).get().await()
            doc.getString("name")?.takeIf { it.isNotBlank() }
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
                officialFareCents = doc.getLong("officialFareCents")?.toInt() ?: 0,
                officialUniversityFareCents =
                    doc.getLong("officialUniversityFareCents")?.toInt() ?: 0
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
        enabled: Boolean,
        resetOdometer: Boolean
    ) {
        val route = loadRoutePath(routeId)
        val stopName = route?.stops?.getOrNull(stopIndex)?.name
        val previousIndex = loadShift(shiftId)?.simulatedStopIndex
        val advanced = if (!resetOdometer && route != null && previousIndex != null) {
            forwardMeters(route, previousIndex, stopIndex)
        } else {
            0.0
        }
        val changes = mutableMapOf<String, Any?>(
            "simulationEnabled" to enabled,
            "simulatedStopIndex" to stopIndex,
            "simulatedLat" to position.latitude,
            "simulatedLng" to position.longitude,
            "simulatedStopName" to stopName,
            "simulatedUpdatedAt" to FieldValue.serverTimestamp()
        )
        if (resetOdometer) {
            changes["odometerMeters"] = 0.0
        } else if (advanced > 0.0) {
            changes["odometerMeters"] = FieldValue.increment(advanced)
        }
        firestore.collection("shifts").document(shiftId).update(changes).await()
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

            val operatorDoc = if (currentUid != null) {
                runCatching {
                    firestore.collection("operators").document(currentUid).get().await()
                }.getOrNull()
            } else null
            val userPlate = operatorDoc?.getString("plate")
                ?: userDoc?.getString("plate")
                ?: userDoc?.getString("vehiclePlate")
                ?: ""
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
                    plate = runCatching {
                        firestore.collection("operators")
                            .document(auth.currentUser?.uid.orEmpty())
                            .get()
                            .await()
                            .getString("plate")
                    }.getOrNull().orEmpty(),
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
            }.getOrNull() ?: runCatching {
                firestore.collection("users").document(currentUid).get().await()
                    .getString("plate")
            }.getOrNull() ?: ""
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
            if (e is kotlinx.coroutines.CancellationException) throw e
            // El servidor no respondió: abrimos el viaje con el perfil real del pasajero escaneado
            // (QR o NFC), nunca con la cuenta del conductor.
            val passengerUid = qrToken.removePrefix("DEMO_QR_").trim()
            if (passengerUid.isBlank()) {
                throw TripOperationException("No se pudo leer al pasajero. Intenta de nuevo.", e)
            }
            val shift = loadShift(shiftId)
                ?: throw TripOperationException("La unidad ya no tiene un turno activo.", e)
            if (!shift.isActive) throw TripOperationException("La unidad ya terminó su turno.", e)
            openTripForPassenger(passengerUid, shift, position)
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
            if (e is kotlinx.coroutines.CancellationException) throw e
            if (auth.currentUser == null) {
                throw TripOperationException("No hay una cuenta activa.", e)
            }
            val tripDoc = firestore.collection("trips").document(tripId).get().await()
            val trip = tripDoc.toTrip()
                ?: throw TripOperationException("No pudimos leer el viaje.", e)
            val route = loadRoutePath(trip.routeId)
                ?: throw TripOperationException("No pudimos cargar la ruta del viaje.", e)
            val shift = loadShift(trip.shiftId)

            // Misma medicion que el Tap Out del pasajero: paraderos de la ruta, nunca
            // una distancia inventada.
            val boardingIndex = trip.boardingStopIndex
                ?.takeIf { it in route.stops.indices }
                ?: nearestStopIndex(route, position)
            val alightingIndex = if (shift?.simulationEnabled == true) {
                shift.simulatedStopIndex.takeIf { it in route.stops.indices }
                    ?: nearestStopIndex(route, position)
            } else {
                nearestStopIndex(route, position)
            }
            val distanceMeters = travelledMeters(
                trip = trip,
                shift = shift,
                route = route,
                boardingIndex = boardingIndex,
                alightingIndex = alightingIndex
            )

            val userDoc = runCatching {
                firestore.collection("users").document(trip.passengerUid).get().await()
            }.getOrNull()
            val isUniversity = userDoc?.isUniversityEligible() == true ||
                trip.fareType == "university"

            val version = fareRepository.loadFareVersion()
            val context = RouteFareContext(route = route.toRouteGeo(), version = version)
            val result = FareEstimator.fareForDistance(context, distanceMeters, isUniversity)
            val fareBreakdown = result.toBreakdown(route, isUniversity, version)
            val alightingPosition = route.stops.getOrNull(alightingIndex)

            firestore.collection("trips").document(tripId).update(
                mapOf(
                    "status" to "closed",
                    "endedAt" to FieldValue.serverTimestamp(),
                    "endLocation" to mapOf(
                        "lat" to (alightingPosition?.lat ?: position.latitude),
                        "lng" to (alightingPosition?.lng ?: position.longitude)
                    ),
                    "alightingStopIndex" to alightingIndex,
                    "alightingStopName" to alightingPosition?.name,
                    "distanceMeters" to distanceMeters,
                    "coverage" to if (shift?.simulationEnabled == true) "simulated" else "gps",
                    "fareBreakdown" to fareBreakdown.toFirestoreMap()
                )
            ).await()

            chargeWallet(trip.passengerUid, fareBreakdown.totalCents)

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

        return openTripForPassenger(uid, shift, position)
    }

    /**
     * Abre el viaje leyendo el perfil real del pasajero en Firestore (nombre, edad, beneficio).
     * Lo usan el Tap In del pasajero y el del conductor (QR o NFC) cuando el servidor no responde.
     */
    private suspend fun openTripForPassenger(
        uid: String,
        shift: Shift,
        position: TripPosition
    ): TapInResult {
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
        val age = userDoc?.getLong("age")?.toInt()?.takeIf { it in 0..120 }
            ?: userDoc?.getString("birthDate")?.ageFromBirthDate()
        // Con el modo demostracion encendido el bus esta en un paradero conocido; sin el,
        // el paradero de subida es el mas cercano a la posicion real del telefono.
        val boardingIndex = if (shift.simulationEnabled) {
            shift.simulatedStopIndex.takeIf { it in route.stops.indices }
                ?: nearestStopIndex(route, position)
        } else {
            nearestStopIndex(route, position)
        }
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
                "boardingOdometerMeters" to shift.odometerMeters,
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
        val alightingIndex = if (shift.simulationEnabled) {
            shift.simulatedStopIndex.takeIf { it in route.stops.indices }
                ?: nearestStopIndex(route, position)
        } else {
            nearestStopIndex(route, position)
        }
        val alightingPosition = route.stops[alightingIndex]
        val distanceMeters = travelledMeters(
            trip = trip,
            shift = shift,
            route = route,
            boardingIndex = boardingIndex,
            alightingIndex = alightingIndex
        )
        val userDoc = runCatching {
            firestore.collection("users").document(uid).get().await()
        }.getOrNull()
        val isUniversity = userDoc?.isUniversityEligible() == true ||
            trip.fareType == "university"
        // Misma tarifa del dia que muestra la pantalla de estimacion.
        val version = fareRepository.loadFareVersion()
        val context = RouteFareContext(route = route.toRouteGeo(), version = version)
        val result = FareEstimator.fareForDistance(context, distanceMeters, isUniversity)
        val breakdown = result.toBreakdown(route, isUniversity, version)

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
                "coverage" to if (shift.simulationEnabled) "simulated" else "gps",
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
            val route = loadRoutePath(routeId)
                ?: throw TripOperationException("No pudimos cargar la ruta.", e)
            val distanceMeters = FareEstimator.routeDistanceMeters(
                route.stops,
                boardingStopIndex,
                alightingStopIndex
            )

            val currentUid = auth.currentUser?.uid ?: ""
            val userDoc = runCatching { firestore.collection("users").document(currentUid).get().await() }.getOrNull()
            val isUniversity = userDoc?.isUniversityEligible() == true

            val fareVersion = fareRepository.loadFareVersion()
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
                fareVersionId = FARE_VERSION_ID
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

private fun String.ageFromBirthDate(): Int? {
    val text = trim()
    val birthDate = runCatching { LocalDate.parse(text) }.getOrNull()
        ?: runCatching {
            LocalDate.parse(text, java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))
        }.getOrNull()
        ?: return null
    return Period.between(birthDate, LocalDate.now()).years.takeIf { it in 0..120 }
}

/**
 * Metros que la unidad avanzo al pasar del paradero [from] al [to]. Si el indice retrocede,
 * es que termino el recorrido y arranco de nuevo, asi que se suma el regreso completo.
 */
/**
 * Distancia real del viaje: la diferencia del odometro de la unidad entre subida y bajada.
 * Si el turno no llevaba odometro, se mide entre los paraderos.
 */
internal fun travelledMeters(
    trip: Trip,
    shift: Shift?,
    route: RoutePath,
    boardingIndex: Int,
    alightingIndex: Int
): Double {
    val boardingOdometer = trip.boardingOdometerMeters
    val currentOdometer = shift?.odometerMeters
    // Nadie puede andar mas que la ruta completa de ida y vuelta: evita cobrar un recorrido
    // acumulado de turnos anteriores si el odometro quedo sin reiniciar.
    val oneWayMeters = FareEstimator.routeDistanceMeters(route.stops, 0, route.stops.lastIndex)
    val maxMeters = if (oneWayMeters > 0.0) oneWayMeters * 2 else Double.MAX_VALUE
    if (boardingOdometer != null && currentOdometer != null && currentOdometer > boardingOdometer) {
        return (currentOdometer - boardingOdometer).coerceAtMost(maxMeters)
    }
    return FareEstimator.routeDistanceMeters(route.stops, boardingIndex, alightingIndex)
}

internal fun forwardMeters(route: RoutePath, from: Int, to: Int): Double {
    val stops = route.stops
    if (stops.size < 2) return 0.0
    val start = from.coerceIn(stops.indices)
    val end = to.coerceIn(stops.indices)
    if (start == end) return 0.0
    if (end > start) {
        return FareEstimator.routeDistanceMeters(stops, start, end)
    }
    // La unidad llego al final y volvio a empezar: avanzo lo que faltaba hasta el ultimo
    // paradero, el recorrido de regreso completo y lo andado desde el primero.
    return FareEstimator.routeDistanceMeters(stops, start, stops.lastIndex) +
        FareEstimator.routeDistanceMeters(stops, 0, stops.lastIndex) +
        FareEstimator.routeDistanceMeters(stops, 0, end)
}

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

/** Identificador de la version tarifaria que publica el script diario de combustible. */
private const val FARE_VERSION_ID = "v1"

private fun com.pasajeya.app.feature.trip.domain.FareResult.toBreakdown(
    route: RoutePath,
    isUniversity: Boolean,
    version: FareVersionInput
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
        baseFarePerKmCents = version.baseFarePerKmCents,
        fuelIndex = version.fuelIndex,
        calculatedCents = rawCalculatedCents.toInt(),
        officialFareCents = route.officialFareCents,
        regularCents = regularCents,
        benefit = if (isUniversity) modelBenefit else null,
        totalCents = totalCents,
        capped = capped,
        minApplied = minApplied,
        fareVersionId = FARE_VERSION_ID
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
        boardingStopName = getString("boardingStopName"),
        alightingStopName = getString("alightingStopName"),
        boardingOdometerMeters = getDouble("boardingOdometerMeters"),
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
        simulatedUpdatedAtMillis = getTimestamp("simulatedUpdatedAt")?.toDate()?.time,
        odometerMeters = getDouble("odometerMeters") ?: 0.0
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

