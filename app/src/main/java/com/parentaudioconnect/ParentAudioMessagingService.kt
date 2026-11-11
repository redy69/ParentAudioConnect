package com.santethp

import android.util.Log
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class ParentAudioMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)

        getSharedPreferences("parent_audio", MODE_PRIVATE)
            .edit()
            .putString("fcm_token", token)
            .apply()

        Log.i(TAG, "FCM registration token refreshed")
        registerRefreshedToken(token)
    }

    private fun registerRefreshedToken(token: String) {
        val prefs = getSharedPreferences("parent_audio", MODE_PRIVATE)
        val role = prefs.getString("pair_role", null) ?: return
        val codes = when (role) {
            "child" -> listOfNotNull(prefs.getString("pair_code", null))
            "parent" -> {
                val result = mutableListOf<String>()
                val saved = prefs.getString("parent_children", null)
                if (!saved.isNullOrBlank()) {
                    try {
                        val json = JSONObject(saved)
                        json.keys().forEach { result.add(it) }
                    } catch (error: Exception) {
                        Log.w(TAG, "Could not read saved child list", error)
                    }
                }
                if (result.isEmpty()) prefs.getString("pair_code", null)?.let { result.add(it) }
                result
            }
            else -> emptyList()
        }.filter { it.isNotBlank() }.distinct()

        if (codes.isEmpty()) return
        val user = FirebaseAuth.getInstance().currentUser ?: return
        user.getIdToken(false).addOnSuccessListener { result ->
            val idToken = result.token ?: return@addOnSuccessListener
            codes.forEach { code ->
                networkExecutor.execute {
                    registerToken(code, idToken, token)
                }
            }
        }.addOnFailureListener { error ->
            Log.w(TAG, "Could not obtain Firebase token for FCM registration", error)
        }
    }

    private fun registerToken(code: String, idToken: String, fcmToken: String) {
        val connection = URL(REGISTER_URL).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            val body = JSONObject()
                .put("idToken", idToken)
                .put("code", code)
                .put("fcmToken", fcmToken)
            connection.outputStream.use {
                it.write(body.toString().toByteArray(Charsets.UTF_8))
            }
            val status = connection.responseCode
            if (status !in 200..299) {
                val detail = connection.errorStream?.bufferedReader()?.use { it.readText() }
                Log.w(TAG, "FCM token registration failed for pairing $code: HTTP $status $detail")
            } else {
                Log.i(TAG, "FCM token registered for pairing $code")
            }
        } catch (error: Exception) {
            Log.w(TAG, "FCM token registration request failed", error)
        } finally {
            connection.disconnect()
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val command = message.data["command"]
        val code = message.data["code"]
        if (command == "SAFE_ZONE_EVENT") {
            val prefs = getSharedPreferences("parent_audio", MODE_PRIVATE)
            val allowedCodes = mutableSetOf<String>()
            prefs.getString("pair_code", null)?.let { allowedCodes.add(it) }
            val savedChildren = prefs.getString("parent_children", null)
            if (!savedChildren.isNullOrBlank()) {
                try {
                    val json = JSONObject(savedChildren)
                    json.keys().forEach { allowedCodes.add(it) }
                } catch (error: Exception) {
                    Log.w(TAG, "Could not validate safe-zone pairing", error)
                }
            }
            if (prefs.getString("pair_role", null) == "parent" &&
                !code.isNullOrBlank() && code in allowedCodes
            ) {
                showSafeZoneNotification(
                    message.data["eventId"] ?: System.currentTimeMillis().toString(),
                    message.data["zoneName"] ?: "zona aman",
                    message.data["transition"] ?: "EXIT"
                )
            }
            return
        }
        val prefs = getSharedPreferences("parent_audio", MODE_PRIVATE)
        val savedCode = prefs.getString("pair_code", null)
        val isChild = prefs.getString("pair_role", null) == "child"
        val isArmed = prefs.getBoolean("remote_camera_armed", false)

        if (command in setOf("AUDIO_START", "AUDIO_STOP") &&
            isChild && !code.isNullOrBlank() && code == savedCode
        ) {
            try {
                val audioIntent = android.content.Intent(
                    this, ChildAudioConnectionService::class.java
                ).apply {
                    action = if (command == "AUDIO_START") {
                        ChildAudioConnectionService.ACTION_CONNECT
                    } else {
                        ChildAudioConnectionService.ACTION_DISCONNECT
                    }
                }
                if (command == "AUDIO_START") {
                    ContextCompat.startForegroundService(this, audioIntent)
                } else {
                    // Stop an existing service without creating a new background service instance.
                    stopService(audioIntent)
                }
                Log.i(TAG, "Remote audio command delivered: $command")
            } catch (error: Exception) {
                Log.w(TAG, "Could not deliver remote audio command: $command", error)
            }
            return
        }

        if (command in setOf("CAMERA_START", "CAMERA_SWITCH", "CAMERA_STOP") &&
            isChild && isArmed && !code.isNullOrBlank() && code == savedCode
        ) {
            try {
                val cameraIntent = android.content.Intent(
                    this, RemoteCameraService::class.java
                ).apply {
                    action = when (command) {
                        "CAMERA_SWITCH" -> RemoteCameraService.ACTION_SWITCH_CAMERA
                        "CAMERA_STOP" -> RemoteCameraService.ACTION_STOP_CAMERA
                        else -> RemoteCameraService.ACTION_START_CAMERA
                    }
                    putExtra(RemoteCameraService.EXTRA_REQUEST_ID, message.data["requestId"])
                    putExtra("facing", message.data["facing"] ?: "back")
                }
                startService(cameraIntent)
                Log.i(TAG, "Remote camera request delivered to armed service")
            } catch (error: Exception) {
                Log.w(TAG, "Could not deliver camera request to active service", error)
            }
        } else {
            Log.i(TAG, "FCM message ignored: no matching armed camera session")
        }
    }

    private fun showSafeZoneNotification(eventId: String, zoneName: String, transition: String) {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    SAFE_ZONE_CHANNEL,
                    "Notifikasi zona aman",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply { description = "Pemberitahuan saat perangkat anak masuk atau keluar zona aman" }
            )
        }
        val entering = transition == "ENTER"
        val title = if (entering) "Masuk zona aman" else "Keluar dari zona aman"
        val body = if (entering) "Perangkat anak tiba di $zoneName." else "Perangkat anak keluar dari $zoneName."
        val openApp = PendingIntent.getActivity(
            this, 2501, android.content.Intent(this, MainActivity::class.java)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, SAFE_ZONE_CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_map)
            .setContentTitle("SantetHP • $title")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        manager.notify(eventId.hashCode(), notification)
    }

    companion object {
        private const val TAG = "ParentAudioFCM"
        private const val SAFE_ZONE_CHANNEL = "santethp_safe_zone_events"
        private const val REGISTER_URL =
            "https://parent-audio-token.yudhis-r15.workers.dev/register-device"
        private val networkExecutor = Executors.newSingleThreadExecutor()
    }
}
