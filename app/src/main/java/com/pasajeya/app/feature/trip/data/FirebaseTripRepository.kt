package com.pasajeya.app.feature.trip.data

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.pasajeya.app.feature.trip.domain.TripPosition
import com.pasajeya.app.feature.trip.domain.TripRepository
import com.pasajeya.app.feature.trip.domain.model.EndShiftResult
import com.pasajeya.app.feature.trip.domain.model.OperatorContext
import com.pasajeya.app.feature.trip.domain.model.QrToken
import com.pasajeya.app.feature.trip.domain.model.Shift
import com.pasajeya.app.feature.trip.domain.model.TapInResult
import com.pasajeya.app.feature.trip.domain.model.TapOutResult
import com.pasajeya.app.feature.trip.domain.model.Trip
import com.pasajeya.app.feature.trip.domain.model.TripOperationException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

@Singleton
class FirebaseTripRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val functions: FirebaseFunctions
) : TripRepository {

    override suspend fun issueQrToken(sessionId: String): QrToken {
        val data = call("issueQrToken", mapOf("sessionId" to sessionId))
        return QrToken(
            token = data.string("qrToken"),
            expiresAtMillis = data.long("expiresAt")
        )
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

    override suspend fun loadRouteName(routeId: String): String? = runCatching {
        firestore.collection("routes").document(routeId).get().await()
            .getString("name")
    }.getOrNull()

    override suspend fun loadOperatorContext(sessionId: String): OperatorContext {
        val data = call("getOperatorContext", mapOf("sessionId" to sessionId))
        return OperatorContext(
            plate = data.string("plate"),
            routeIds = (data["routeIds"] as? List<*>)
                .orEmpty()
                .filterIsInstance<String>(),
            status = data.string("status").ifBlank { "unverified" }
        )
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

    override suspend fun startShift(sessionId: String, routeId: String): Shift {
        val data = call(
            "startShift",
            mapOf("sessionId" to sessionId, "routeId" to routeId)
        )
        val shiftId = data.string("shiftId")
        return awaitDocument("shifts", shiftId)?.toShift()
            ?: Shift(
                id = shiftId,
                operatorUid = "",
                plate = "",
                routeId = routeId,
                startedAtMillis = data.longOrNull("startedAt"),
                endedAtMillis = null,
                status = "active"
            )
    }

    override suspend fun endShift(sessionId: String, shiftId: String): EndShiftResult {
        val data = call(
            "endShift",
            mapOf("sessionId" to sessionId, "shiftId" to shiftId)
        )
        return EndShiftResult(
            shiftId = shiftId,
            endedAtMillis = data.longOrNull("endedAt"),
            reviewCount = (data.longOrNull("tripsRequiresReview") ?: 0L).toInt()
        )
    }

    override suspend fun tapIn(
        sessionId: String,
        shiftId: String,
        qrToken: String,
        position: TripPosition
    ): TapInResult {
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
        return TapInResult(
            tripId = data.string("tripId"),
            passengerFirstName = data.string("firstName"),
            passengerAge = data.longOrNull("age")?.toInt()
        )
    }

    override suspend fun tapOut(
        sessionId: String,
        tripId: String,
        position: TripPosition
    ): TapOutResult {
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
        return TapOutResult(
            tripId = data.string("tripId"),
            status = data.string("status"),
            distanceMeters = data.doubleOrNull("distanceMeters") ?: 0.0,
            requiresReview = data["requiresReview"] == true
        )
    }

    private suspend fun call(
        name: String,
        payload: Map<String, Any?>
    ): Map<String, Any?> {
        val callable = functions.getHttpsCallable(name)
        return try {
            @Suppress("UNCHECKED_CAST")
            val result = callable.call(payload).await().data as? Map<String, Any?>
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
        } catch (error: TripOperationException) {
            throw error
        } catch (error: Exception) {
            throw TripOperationException(
                "No hay conexión con el servidor. Revisa tu internet.",
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

private fun DocumentSnapshot.toTrip(): Trip? {
    if (!exists()) return null
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
        passengerAge = getLong("passengerAge")?.toInt()
    )
}

private fun DocumentSnapshot.toShift(): Shift? {
    if (!exists()) return null
    return Shift(
        id = id,
        operatorUid = getString("operatorUid").orEmpty(),
        plate = getString("plate").orEmpty(),
        routeId = getString("routeId").orEmpty(),
        startedAtMillis = getTimestamp("startedAt")?.toDate()?.time,
        endedAtMillis = getTimestamp("endedAt")?.toDate()?.time,
        status = getString("status").orEmpty()
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
