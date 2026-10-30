package com.pasajeya.app.feature.auth.data

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
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
        _deviceSession.value
            ?.takeIf { it.uid == uid }
            ?.let { return@withLock it }

        val deviceId = deviceIdStore.getOrCreate()
        val userDoc = try {
            firestore.collection("users").document(uid).get().await()
        } catch (_: Exception) {
            null
        }

        val session = DeviceSession(
            uid = uid,
            sessionId = "local-session-$deviceId",
            profileComplete = userDoc?.exists() == true,
            emailVerified = auth.currentUser?.isEmailVerified ?: true,
            role = UserRole.fromWire(userDoc?.get("role")) ?: UserRole.PASSENGER,
            operatorComplete = true
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
        callFunction(
            "completeProfile",
            mapOf(
                "sessionId" to session.sessionId,
                "firstName" to firstName,
                "lastName" to lastName,
                "dni" to dni,
                "birthDate" to birthDate,
                "role" to role.wireValue,
                "consentAccepted" to true,
                "consentVersion" to consentVersion
            )
        )

        _deviceSession.update { current ->
            if (current?.sessionId == session.sessionId) {
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
        val response = callFunction(
            "registerOperator",
            mapOf(
                "sessionId" to session.sessionId,
                "licenseNumber" to licenseNumber,
                "licenseClass" to licenseClass,
                "plate" to plate,
                "routeIds" to routeIds
            )
        )

        _deviceSession.update { current ->
            if (current?.sessionId == session.sessionId) {
                current.copy(operatorComplete = true)
            } else {
                current
            }
        }

        return OperatorRegistration(
            plate = response.string("plate") ?: plate,
            routeIds = (response["routeIds"] as? List<*>)
                ?.mapNotNull { it as? String }
                ?: routeIds,
            status = response.string("status").orEmpty()
        )
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

    private suspend fun callFunction(
        name: String,
        data: Map<String, Any>
    ): Map<*, *> {
        return try {
            val result = functions.getHttpsCallable(name).call(data).await()
            result.getData() as? Map<*, *> ?: throw AccountException(AccountFailure.Unknown)
        } catch (error: CancellationException) {
            throw error
        } catch (error: AccountException) {
            throw error
        } catch (error: FirebaseFunctionsException) {
            throw AccountException(error.toAccountFailure())
        } catch (error: IOException) {
            throw AccountException(AccountFailure.Network)
        } catch (error: Exception) {
            throw AccountException(AccountFailure.Unknown)
        }
    }

    private fun FirebaseFunctionsException.toAccountFailure(): AccountFailure {
        val lowerMessage = message.orEmpty().lowercase(Locale.ROOT)
        return when (code) {
            FirebaseFunctionsException.Code.ALREADY_EXISTS ->
                if ("placa" in lowerMessage) {
                    AccountFailure.PlateAlreadyRegistered
                } else {
                    AccountFailure.DniAlreadyRegistered
                }
            FirebaseFunctionsException.Code.PERMISSION_DENIED ->
                AccountFailure.SessionInactive
            FirebaseFunctionsException.Code.FAILED_PRECONDITION ->
                when {
                    "correo" in lowerMessage -> AccountFailure.EmailNotVerified
                    "perfil ya" in lowerMessage -> AccountFailure.ProfileAlreadyComplete
                    else -> AccountFailure.InvalidData
                }
            FirebaseFunctionsException.Code.INVALID_ARGUMENT ->
                if ("ruta" in lowerMessage) {
                    AccountFailure.RouteUnavailable
                } else {
                    AccountFailure.InvalidData
                }
            FirebaseFunctionsException.Code.UNAUTHENTICATED ->
                AccountFailure.NotAuthenticated
            FirebaseFunctionsException.Code.UNAVAILABLE,
            FirebaseFunctionsException.Code.DEADLINE_EXCEEDED ->
                AccountFailure.Network
            else -> AccountFailure.Unknown
        }
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

    private fun Map<*, *>.string(key: String): String? = this[key] as? String

    private fun Map<*, *>.boolean(key: String): Boolean = this[key] as? Boolean ?: false
}