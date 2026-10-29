package com.pasajeya.app.feature.trip.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.pasajeya.app.feature.trip.domain.TripPosition
import com.pasajeya.app.feature.trip.domain.model.TripOperationException
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
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
}

fun Location.toPosition(): TripPosition = TripPosition(
    latitude = latitude,
    longitude = longitude,
    accuracy = if (hasAccuracy()) accuracy.toDouble() else null
)
