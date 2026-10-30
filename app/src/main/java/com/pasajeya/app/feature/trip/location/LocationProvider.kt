package com.pasajeya.app.feature.trip.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.pasajeya.app.feature.trip.domain.GpsFix
import com.pasajeya.app.feature.trip.domain.TripPosition
import com.pasajeya.app.feature.trip.domain.model.TripOperationException
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

@Singleton
class LocationProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val client = LocationServices.getFusedLocationProviderClient(context)

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    suspend fun currentPosition(): TripPosition {
        if (!hasPermission()) {
            throw TripOperationException(
                "Activa el permiso de ubicación para registrar el viaje."
            )
        }
        val location = withTimeoutOrNull(12_000) {
            client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).await()
        } ?: throw TripOperationException(
            "No pudimos obtener tu ubicación. Sal al aire libre e inténtalo de nuevo."
        )
        return location.toPosition()
    }

    /**
     * Lecturas GPS continuas (FusedLocationProvider) para el viaje activo.
     * El flujo falla con [TripOperationException] si falta el permiso.
     */
    @SuppressLint("MissingPermission")
    fun locationUpdates(intervalMillis: Long = 3_000L): Flow<GpsFix> = callbackFlow {
        if (!hasPermission()) {
            close(
                TripOperationException(
                    "Activa el permiso de ubicación para ver el costo en vivo."
                )
            )
            return@callbackFlow
        }
        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            intervalMillis
        )
            .setMinUpdateIntervalMillis(intervalMillis / 2)
            .build()
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.locations.forEach { location ->
                    trySend(
                        GpsFix(
                            lat = location.latitude,
                            lng = location.longitude,
                            accuracyMeters = if (location.hasAccuracy()) {
                                location.accuracy.toDouble()
                            } else {
                                null
                            },
                            timeMillis = location.time
                        )
                    )
                }
            }
        }
        client.requestLocationUpdates(request, callback, Looper.getMainLooper())
        awaitClose { client.removeLocationUpdates(callback) }
    }
}

fun Location.toPosition(): TripPosition = TripPosition(
    latitude = latitude,
    longitude = longitude,
    accuracy = if (hasAccuracy()) accuracy.toDouble() else null
)
