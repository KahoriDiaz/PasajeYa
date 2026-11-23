package com.pasajeya.app.feature.auth.data

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
import com.google.firebase.functions.FirebaseFunctions
import com.pasajeya.app.core.util.ageFromBirthDate
import com.pasajeya.app.feature.auth.domain.AccountRepository
import com.pasajeya.app.feature.auth.domain.model.AccountException
import com.pasajeya.app.feature.auth.domain.model.AccountFailure
import com.pasajeya.app.feature.auth.domain.model.DeviceSession
import com.pasajeya.app.feature.auth.domain.model.OperatorRegistration
import com.pasajeya.app.feature.auth.domain.model.RemoteSession
import com.pasajeya.app.feature.auth.domain.model.RouteOption
import com.pasajeya.app.feature.auth.domain.model.UserProfile
import com.pasajeya.app.feature.auth.domain.model.UserRole
import java.io.IOException
import java.security.MessageDigest
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await

@Singleton
class FirebaseAccountRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val functions: FirebaseFunctions,
    private val deviceIdStore: DeviceIdStore
) : AccountRepository {
    private val sessionMutex = Mutex()
    private val _deviceSession = MutableStateFlow<DeviceSession?>(null)

    override val deviceSession: StateFlow<DeviceSession?> = _deviceSession.asStateFlow()

    override suspend fun ensureSession(uid: String): DeviceSession = sessionMutex.withLock {
        requireCurrentUid(uid)
        // Una sesion cacheada sin rol esta a medio registrar: conviene volver a leerla.
        _deviceSession.value
            ?.takeIf { it.uid == uid && it.role != null }
            ?.let { return@withLock it }

        val deviceId = deviceIdStore.getOrCreate()
        // Sesion local: el rol sale de users/{uid}. Si no se puede leer, no se adivina el rol.
        val userDoc = try {
            firestore.collection("users").document(uid).get().await()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            throw AccountException(AccountFailure.Network)
        }

        val role = UserRole.fromWire(userDoc.get("role"))
        // La empresa necesita su ficha en companies/{uid}; el conductor, placa y ruta.
        val roleDataComplete = when (role) {
            UserRole.COMPANY -> runCatching {
                firestore.collection("companies").document(uid).get().await().exists()
            }.getOrDefault(false)

            UserRole.DRIVER -> {
                val plate = userDoc.getString("plate")
                    ?: userDoc.getString("vehiclePlate")
                val route = userDoc.getString("assignedRouteId")
                if (!plate.isNullOrBlank() && !route.isNullOrBlank()) {
                    true
                } else {
                    runCatching {
                        firestore.collection("operators").document(uid).get().await().exists()
                    }.getOrDefault(false)
                }
            }

            else -> true
        }
        val session = DeviceSession(
            uid = uid,
            sessionId = "local-session-$deviceId",
            profileComplete = userDoc.exists() && role != null,
            emailVerified = auth.currentUser?.isEmailVerified ?: true,
            role = role,
            operatorComplete = roleDataComplete
        )
        _deviceSession.value = session
        session
    }
    override fun clearSession() {
        _deviceSession.value = null
    }

    override fun observeUserProfile(uid: String): Flow<UserProfile?> = callbackFlow {
        val registration = firestore.collection("users").document(uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snapshot?.toUserProfile())
            }

        awaitClose { registration.remove() }
    }

    override fun observeRemoteSession(uid: String): Flow<RemoteSession> = callbackFlow {
        val registration = firestore.collection("sessions").document(uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(
                    RemoteSession(
                        sessionId = snapshot?.getString("sessionId"),
                        fromCache = snapshot?.metadata?.isFromCache ?: false
                    )
                )
            }

        awaitClose { registration.remove() }
    }

    override suspend fun loadActiveRoutes(): List<RouteOption> {
        return try {
            firestore.collection("routes")
                .whereEqualTo("active", true)
                .get()
                .await()
                .documents
                .mapNotNull { document ->
                    val name = document.getString("name") ?: return@mapNotNull null
                    RouteOption(
                        id = document.id,
                        name = name,
                        stopsCount = (document.get("stops") as? List<*>)?.size ?: 0
                    )
                }
                .sortedBy { it.name.lowercase(Locale.ROOT) }
        } catch (error: FirebaseNetworkException) {
            throw AccountException(AccountFailure.Network)
        } catch (error: FirebaseFirestoreException) {
            throw AccountException(
                if (error.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                    AccountFailure.NotAuthenticated
                } else {
                    AccountFailure.Unknown
                }
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            throw AccountException(AccountFailure.Unknown)
        }
    }

    override suspend fun completeProfile(
        role: UserRole,
        firstName: String,
        lastName: String,
        dni: String,
        birthDate: String,
        consentVersion: String
    ) {
        val session = requireActiveSession()
        val uid = session.uid
        val userRef = firestore.collection("users").document(uid)
        val indexRef = firestore.collection("dniIndex").document(hashedDni(dni))

        try {
            // Un DNI solo puede pertenecer a una cuenta.
            val index = indexRef.get().await()
            if (index.exists() && index.getString("uid") != uid) {
                throw AccountException(AccountFailure.DniAlreadyRegistered)
            }

            val existing = userRef.get().await()
            if (existing.exists() && UserRole.fromWire(existing.get("role")) != null) {
                _deviceSession.update { current ->
                    if (current?.uid == uid) current.copy(profileComplete = true) else current
                }
                return
            }

            val now = FieldValue.serverTimestamp()
            // Si la fecha no se puede leer, mejor no guardar edad que guardar 0:
            // el anuncio de voz diria "0 anos".
            val age = ageFromBirthDate(birthDate)
            userRef.set(
                mapOf(
                    "role" to role.wireValue,
                    "firstName" to firstName,
                    "lastName" to lastName,
                    "fullName" to listOf(firstName, lastName).joinToString(" ").trim(),
                    "birthDate" to birthDate,
                    "createdAt" to now,
                    "privacyConsent" to mapOf(
                        "version" to consentVersion,
                        "acceptedAt" to now
                    ),
                    "benefit" to mapOf("status" to "none")
                ) + (if (age != null) mapOf("age" to age) else emptyMap<String, Any>()),
                SetOptions.merge()
            ).await()
            firestore.collection("users_private").document(uid)
                .set(mapOf("dni" to dni), SetOptions.merge())
                .await()
            indexRef.set(mapOf("uid" to uid)).await()
        } catch (error: CancellationException) {
            throw error
        } catch (error: AccountException) {
            throw error
        } catch (error: Exception) {
            throw AccountException(error.toAccountFailure())
        }

        _deviceSession.update { current ->
            if (current?.uid == uid) {
                current.copy(profileComplete = true, role = role)
            } else {
                current
            }
        }
    }

    override suspend fun registerOperator(
        licenseNumber: String,
        licenseClass: String,
        plate: String,
        routeIds: List<String>
    ): OperatorRegistration {
        val session = requireActiveSession()
        val uid = session.uid
        if (routeIds.isEmpty()) {
            throw AccountException(AccountFailure.RouteUnavailable)
        }
        val vehicleRef = firestore.collection("vehicles").document(plate)
        var status = OPERATOR_STATUS_UNVERIFIED

        try {
            // Una placa solo puede pertenecer a un conductor.
            val vehicle = vehicleRef.get().await()
            if (vehicle.exists() && vehicle.getString("ownerUid") != uid) {
                throw AccountException(AccountFailure.PlateAlreadyRegistered)
            }

            // El conductor avalado por una empresa queda verificado; el independiente, en revision.
            val companyId = firestore.collection("users").document(uid).get().await()
                .getString("companyId")
                .orEmpty()
            if (companyId.isNotBlank()) {
                status = OPERATOR_STATUS_VERIFIED_BY_COMPANY
            }

            firestore.collection("operators").document(uid).set(
                mapOf(
                    "licenseNumber" to licenseNumber,
                    "licenseClass" to licenseClass,
                    "plate" to plate,
                    "routeIds" to routeIds,
                    "status" to status,
                    "companyId" to companyId,
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            ).await()
            vehicleRef.set(mapOf("ownerUid" to uid), SetOptions.merge()).await()
            // Espejo en users/{uid} para el panel del operador y el anuncio de voz.
            firestore.collection("users").document(uid).set(
                mapOf(
                    "plate" to plate,
                    "vehiclePlate" to plate,
                    "assignedRouteId" to routeIds.first(),
                    "licenseClass" to licenseClass,
                    "driverStatus" to status
                ),
                SetOptions.merge()
            ).await()
        } catch (error: CancellationException) {
            throw error
        } catch (error: AccountException) {
            throw error
        } catch (error: Exception) {
            throw AccountException(error.toAccountFailure())
        }

        _deviceSession.update { current ->
            if (current?.uid == uid) current.copy(operatorComplete = true) else current
        }

        return OperatorRegistration(plate = plate, routeIds = routeIds, status = status)
    }

    private suspend fun requireActiveSession(): DeviceSession {
        val uid = auth.currentUser?.uid
            ?: throw AccountException(AccountFailure.NotAuthenticated)
        val session = _deviceSession.value
            ?: throw AccountException(AccountFailure.SessionInactive)

        if (session.uid != uid) {
            throw AccountException(AccountFailure.NotAuthenticated)
        }

        return session
    }

    private fun requireCurrentUid(uid: String) {
        if (auth.currentUser?.uid != uid) {
            throw AccountException(AccountFailure.NotAuthenticated)
        }
    }

    private fun hashedDni(dni: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(dni.trim().toByteArray())
        return digest.joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun Throwable.toAccountFailure(): AccountFailure = when {
        this is FirebaseNetworkException || this is IOException -> AccountFailure.Network
        this is FirebaseFirestoreException &&
            code == FirebaseFirestoreException.Code.PERMISSION_DENIED ->
            AccountFailure.NotAuthenticated
        this is FirebaseFirestoreException &&
            code == FirebaseFirestoreException.Code.UNAVAILABLE -> AccountFailure.Network
        else -> AccountFailure.Unknown
    }

    private fun DocumentSnapshot.toUserProfile(): UserProfile? {
        val role = UserRole.fromWire(get("role")) ?: return null
        val consent = get("privacyConsent") as? Map<*, *>
        return UserProfile(
            role = role,
            firstName = getString("firstName").orEmpty(),
            lastName = getString("lastName").orEmpty(),
            privacyConsentVersion = consent?.get("version") as? String ?: ""
        )
    }

    companion object {
        /** Conductor independiente: solo con documentos, pendiente de revision. */
        const val OPERATOR_STATUS_UNVERIFIED = "unverified"

        /** Conductor avalado por una empresa formal. */
        const val OPERATOR_STATUS_VERIFIED_BY_COMPANY = "verified_by_company"
    }
}