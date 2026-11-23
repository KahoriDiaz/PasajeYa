package com.pasajeya.app.feature.auth.data

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.android.gms.tasks.OnFailureListener
import com.google.firebase.firestore.FirebaseFirestore
import com.pasajeya.app.feature.auth.domain.DeviceSessionRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
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

    override suspend fun registerDevice(uid: String, deviceId: String) {
        withContext(Dispatchers.IO) {
            firestore.collection("users").document(uid).update(
                mapOf(
                    "activeDeviceId" to deviceId,
                    "lastLoginAtMillis" to System.currentTimeMillis()
                )
            )
        }
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
                if (remoteDeviceId.isNullOrBlank()) {
                    docRef.update("activeDeviceId", deviceId)
                        .addOnFailureListener { e ->
                            Log.e("AUTH_SESSION", "Error actualizando activeDeviceId", e)
                        }
                    return@addSnapshotListener
                }
                if (remoteDeviceId != deviceId) {
                    trySend(remoteDeviceId)
                }
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
