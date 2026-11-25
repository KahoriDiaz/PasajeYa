package com.pasajeya.app.feature.auth.data

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.pasajeya.app.feature.auth.domain.DeviceSessionRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

private val Context.deviceSessionDataStore by preferencesDataStore(name = "device_session")

class DeviceSessionRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val firestore: FirebaseFirestore
) : DeviceSessionRepository {

    private val dataStore = context.deviceSessionDataStore
    private val deviceIdKey = stringPreferencesKey("device_id")

    override val deviceId: String = runBlocking {
        dataStore.data.first()[deviceIdKey]
            ?: run {
                val newId = generateDeviceId()
                dataStore.edit { it[deviceIdKey] = newId }
                newId
            }
    }

    /**
     * Marca del servidor con la que este telefono tomo la sesion. Sirve para no cerrarse a
     * si mismo: solo un inicio de sesion POSTERIOR al nuestro nos saca.
     */
    @Volatile
    private var claimedAtMillis: Long? = null

    override suspend fun registerDevice(uid: String, deviceId: String): Long? =
        withContext(Dispatchers.IO) {
            val document = firestore.collection("users").document(uid)
            document.update(
                mapOf(
                    "activeDeviceId" to deviceId,
                    "activeSessionAt" to FieldValue.serverTimestamp(),
                    "lastLoginAtMillis" to System.currentTimeMillis()
                )
            ).await()
            // Se vuelve a leer para quedarse con la hora del servidor: comparar relojes de
            // telefonos distintos no sirve, cada uno puede estar desfasado.
            val claimedAt = runCatching {
                document.get().await().getTimestamp("activeSessionAt")?.toDate()?.time
            }.getOrNull()
            claimedAtMillis = claimedAt
            claimedAt
        }

    override fun observeActiveDeviceId(uid: String) = callbackFlow<String?> {
        val docRef = firestore.collection("users").document(uid)
        var registration: com.google.firebase.firestore.ListenerRegistration? = null
        try {
            registration = docRef.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("AUTH_SESSION", "Error escuchando activeDeviceId", error)
                    return@addSnapshotListener
                }
                if (snapshot == null || !snapshot.exists()) {
                    return@addSnapshotListener
                }
                val remoteDeviceId = snapshot.getString("activeDeviceId")
                // Un telefono que solo esta mirando no se queda con la sesion: antes, si el
                // dato estaba vacio, cualquiera se la adjudicaba y el que acababa de entrar
                // se quedaba afuera. La sesion solo se toma al iniciarla.
                if (remoteDeviceId.isNullOrBlank() || remoteDeviceId == deviceId) {
                    return@addSnapshotListener
                }
                // Solo un inicio de sesion posterior al nuestro cierra este telefono. Sin
                // esto, el que acababa de entrar leia el dispositivo anterior y se cerraba
                // a si mismo antes de que su propio registro llegara.
                val remoteAt = snapshot.getTimestamp("activeSessionAt")?.toDate()?.time
                val ownAt = claimedAtMillis
                if (ownAt != null && remoteAt != null && remoteAt <= ownAt) {
                    return@addSnapshotListener
                }
                trySend(remoteDeviceId)
            }
        } catch (error: Exception) {
            Log.e("AUTH_SESSION", "Error en observeActiveDeviceId", error)
        }
        awaitClose { registration?.remove() }
    }

    private fun generateDeviceId(): String {
        val androidId = android.provider.Settings.Secure.getString(
            context.contentResolver,
            android.provider.Settings.Secure.ANDROID_ID
        )
        val randomPart = Random.nextLong().toString(36).uppercase()
        return "${androidId ?: randomPart}-$randomPart"
    }
}
