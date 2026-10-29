package com.pasajeya.app.feature.trip.location

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.pasajeya.app.MainActivity
import com.pasajeya.app.R
import dagger.hilt.android.AndroidEntryPoint
import java.util.Date
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await

@AndroidEntryPoint
class ShiftLocationService : Service() {

    @Inject
    lateinit var firestore: FirebaseFirestore

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val buffer = mutableListOf<Map<String, Any>>()
    private lateinit var client: FusedLocationProviderClient
    private var activeShiftId: String? = null
    private var lastAccepted: Location? = null
    private var isFlushing = false
    private var lastFlushAt = 0L

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let(::onLocation)
        }
    }

    override fun onCreate() {
        super.onCreate()
        client = LocationServices.getFusedLocationProviderClient(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val shiftId = intent?.getStringExtra(EXTRA_SHIFT_ID)
        if (shiftId.isNullOrBlank()) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!hasLocationPermission()) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (activeShiftId != null && activeShiftId != shiftId) {
            flushBlocking()
        }
        activeShiftId = shiftId
        startInForeground()
        requestUpdates()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        client.removeLocationUpdates(callback)
        flushBlocking()
        scope.cancel()
        super.onDestroy()
    }

    private fun startInForeground() {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.operator_shift_title),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.operator_battery_body)
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }

        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.operator_shift_active))
            .setContentText(getString(R.string.operator_battery_body))
            .setContentIntent(openApp)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
    }

    private fun requestUpdates() {
        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            INTERVAL_MS
        )
            .setMinUpdateIntervalMillis(INTERVAL_MS)
            .setMaxUpdateDelayMillis(INTERVAL_MS * 2)
            .build()
        try {
            client.requestLocationUpdates(request, callback, mainLooper)
        } catch (error: SecurityException) {
            stopSelf()
        }
    }

    private fun onLocation(location: Location) {
        val now = location.time
        val previous = lastAccepted
        if (previous != null) {
            val elapsed = now - previous.time
            if (elapsed < INTERVAL_MS) return
            val isStationary = location.hasSpeed() && location.speed < 0.5f
            if (isStationary && elapsed < STATIONARY_INTERVAL_MS) return
        }
        lastAccepted = location
        buffer += mapOf(
            "lat" to location.latitude,
            "lng" to location.longitude,
            "acc" to if (location.hasAccuracy()) location.accuracy.toDouble() else 0.0,
            "t" to Timestamp(Date(now))
        )
        if (buffer.size >= MAX_POINTS || now - lastFlushAt >= FLUSH_INTERVAL_MS) {
            flush()
        }
    }

    private fun flush() {
        if (isFlushing || buffer.isEmpty()) return
        val shiftId = activeShiftId ?: return
        isFlushing = true
        val chunk = buffer.toList()
        val from = chunk.first()["t"] as Timestamp
        val to = chunk.last()["t"] as Timestamp
        lastFlushAt = System.currentTimeMillis()
        scope.launch {
            try {
                firestore.collection("shifts")
                    .document(shiftId)
                    .collection("chunks")
                    .add(
                        mapOf(
                            "points" to chunk,
                            "from" to from,
                            "to" to to
                        )
                    )
                    .await()
                buffer.removeAll(chunk)
            } catch (_: Exception) {
                lastFlushAt = 0L
            } finally {
                isFlushing = false
            }
        }
    }

    private fun flushBlocking() {
        if (buffer.isEmpty()) return
        val shiftId = activeShiftId ?: return
        val chunk = buffer.toList()
        val from = chunk.first()["t"] as Timestamp
        val to = chunk.last()["t"] as Timestamp
        runBlocking(Dispatchers.IO) {
            runCatching {
                firestore.collection("shifts")
                    .document(shiftId)
                    .collection("chunks")
                    .add(
                        mapOf(
                            "points" to chunk,
                            "from" to from,
                            "to" to to
                        )
                    )
                    .await()
            }.onSuccess {
                buffer.removeAll(chunk)
            }
        }
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

    companion object {
        private const val CHANNEL_ID = "shift_location"
        private const val NOTIFICATION_ID = 4102
        private const val EXTRA_SHIFT_ID = "shiftId"
        private const val INTERVAL_MS = 5_000L
        private const val STATIONARY_INTERVAL_MS = 15_000L
        private const val FLUSH_INTERVAL_MS = 30_000L
        private const val MAX_POINTS = 30

        fun start(context: Context, shiftId: String) {
            val intent = Intent(context, ShiftLocationService::class.java)
                .putExtra(EXTRA_SHIFT_ID, shiftId)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ShiftLocationService::class.java))
        }
    }
}
