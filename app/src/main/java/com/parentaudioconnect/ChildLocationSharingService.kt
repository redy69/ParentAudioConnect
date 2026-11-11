package com.santethp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Explicitly enabled, user-visible location sharing for the paired child device.
 * The service first tries the last known fix, then requests a fresh high-accuracy
 * fix and continues balanced-power updates while its notification remains visible.
 * Location is never collected before opt-in.
 */
class ChildLocationSharingService : Service() {
    private val fusedLocation by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private val firestore by lazy { FirebaseFirestore.getInstance() }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let(::publishLocation)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSharing()
            return START_NOT_STICKY
        }

        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val code = prefs.getString("pair_code", null)
        if (prefs.getString("pair_role", null) != "child" || code.isNullOrBlank()) {
            stopSelf()
            return START_NOT_STICKY
        }
        val fine = ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) {
            prefs.edit().putBoolean("child_location_sharing_enabled", false).apply()
            stopSelf()
            return START_NOT_STICKY
        }

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        prefs.edit().putBoolean("child_location_sharing_enabled", true).apply()

        // Publish the user's explicit sharing consent immediately. Do not wait
        // for GPS to produce a coordinate, otherwise the parent UI can wrongly
        // report that sharing is disabled while this foreground service is active.
        firestore.collection("pairings").document(code)
            .update("childLocationSharingEnabled", true)
            .addOnSuccessListener {
                Log.i(TAG, "Location sharing consent published")
            }
            .addOnFailureListener { error ->
                Log.w(TAG, "Unable to publish location sharing consent", error)
            }

        val request = LocationRequest.Builder(
            Priority.PRIORITY_BALANCED_POWER_ACCURACY,
            UPDATE_INTERVAL_MS
        )
            .setMinUpdateIntervalMillis(MIN_UPDATE_INTERVAL_MS)
            .setMaxUpdateDelayMillis(MAX_UPDATE_DELAY_MS)
            .setWaitForAccurateLocation(false)
            .build()

        try {
            // Prevent duplicate callbacks if Android restarts this sticky service.
            fusedLocation.removeLocationUpdates(locationCallback)
            fusedLocation.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
                .addOnSuccessListener {
                    Log.i(TAG, "Location updates registered")
                }
                .addOnFailureListener { error ->
                    Log.w(TAG, "Unable to request location updates", error)
                }

            // Show a last-known position quickly while waiting for a fresh GPS/network fix.
            fusedLocation.lastLocation.addOnSuccessListener { location ->
                if (location != null) publishLocation(location)
            }.addOnFailureListener { error ->
                Log.w(TAG, "Unable to read last known location", error)
            }

            // Ask for a fresh, more accurate first fix; later updates use balanced power.
            fusedLocation.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                CancellationTokenSource().token
            ).addOnSuccessListener { location ->
                if (location != null) {
                    publishLocation(location)
                } else {
                    Log.w(TAG, "Initial location is not available yet")
                }
            }.addOnFailureListener { error ->
                Log.w(TAG, "Unable to get initial location", error)
            }
        } catch (error: SecurityException) {
            Log.w(TAG, "Location permission was revoked", error)
            prefs.edit().putBoolean("child_location_sharing_enabled", false).apply()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
        return START_STICKY
    }

    private fun publishLocation(location: Location) {
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val code = prefs.getString("pair_code", null)
        if (prefs.getString("pair_role", null) != "child" || code.isNullOrBlank()) return
        // Ignore delayed location callbacks after the user has stopped sharing.
        if (!prefs.getBoolean("child_location_sharing_enabled", false)) return

        val payload = mapOf(
            "childLatitude" to location.latitude,
            "childLongitude" to location.longitude,
            "childLocationAccuracyM" to location.accuracy.toDouble(),
            "childLocationAt" to FieldValue.serverTimestamp(),
            "childLocationSharingEnabled" to true
        )
        firestore.collection("pairings").document(code)
            .update(payload)
            .addOnFailureListener { error ->
                Log.w(TAG, "Unable to publish child location", error)
            }
    }

    private fun stopSharing() {
        try {
            fusedLocation.removeLocationUpdates(locationCallback)
        } catch (_: Exception) { }
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val code = prefs.getString("pair_code", null)
        prefs.edit().putBoolean("child_location_sharing_enabled", false).apply()
        if (prefs.getString("pair_role", null) == "child" && !code.isNullOrBlank()) {
            firestore.collection("pairings").document(code)
                .update(
                    mapOf(
                        "childLocationSharingEnabled" to false,
                        "childLocationStoppedAt" to FieldValue.serverTimestamp()
                    ))
                .addOnFailureListener { error ->
                    Log.w(TAG, "Unable to publish location sharing stop", error)
                }
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Berbagi lokasi SantetHP",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Pemberitahuan yang terlihat saat lokasi perangkat dibagikan"
                }
            )
        }
    }

    private fun buildNotification(): Notification {
        val stopIntent = PendingIntent.getService(
            this,
            2408,
            Intent(this, ChildLocationSharingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val openAppIntent = PendingIntent.getActivity(
            this,
            2409,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("SantetHP • Berbagi lokasi aktif")
            .setContentText("Lokasi perangkat ini dibagikan ke HP orang tua.")
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(android.R.drawable.ic_media_pause, "Hentikan", stopIntent)
            .build()
    }

    override fun onDestroy() {
        try {
            fusedLocation.removeLocationUpdates(locationCallback)
        } catch (_: Exception) { }
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.santethp.action.START_LOCATION_SHARING"
        const val ACTION_STOP = "com.santethp.action.STOP_LOCATION_SHARING"
        private const val TAG = "ChildLocationSharing"
        private const val PREFS = "parent_audio"
        private const val CHANNEL_ID = "santethp_location_sharing"
        private const val NOTIFICATION_ID = 2407
        private const val UPDATE_INTERVAL_MS = 2 * 60 * 1000L
        private const val MIN_UPDATE_INTERVAL_MS = 60 * 1000L
        private const val MAX_UPDATE_DELAY_MS = 3 * 60 * 1000L
    }
}
