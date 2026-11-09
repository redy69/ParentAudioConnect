package com.parentaudioconnect

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import io.livekit.android.LiveKit
import io.livekit.android.room.Room
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Keeps the child's LiveKit room in a foreground service while the UI is backgrounded.
 * This service is only used for the receiving (child) device.
 */
class ChildAudioConnectionService : Service() {

    companion object {
        const val ACTION_CONNECT = "com.parentaudioconnect.action.CHILD_CONNECT"
        const val EXTRA_PAIR_CODE = "child_pair_code"
        const val EXTRA_FIREBASE_ID_TOKEN = "child_firebase_id_token"

        private const val CHANNEL_ID = "child_audio_connection"
        private const val NOTIFICATION_ID = 4201
        private const val TAG = "ChildAudioService"
        private const val TOKEN_URL =
            "https://parent-audio-token.yudhis-r15.workers.dev/token"
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var room: Room? = null
    private var transferManager: Mp3TransferManager? = null
    private var connecting = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action != ACTION_CONNECT) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        val pairCode = intent.getStringExtra(EXTRA_PAIR_CODE)
        val firebaseIdToken = intent.getStringExtra(EXTRA_FIREBASE_ID_TOKEN)

        if (pairCode.isNullOrBlank() || firebaseIdToken.isNullOrBlank()) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, notification("Menghubungkan audio anak..."))

        if (room == null && !connecting) {
            connecting = true
            serviceScope.launch {
                try {
                    val response = withContext(Dispatchers.IO) {
                        requestRoomToken(firebaseIdToken, pairCode)
                    }
                    val token = response.getString("token")
                    val serverUrl = response.getString("serverUrl")
                    val role = response.getString("role")
                    require(role == "child") { "Peran perangkat bukan anak" }

                    val newRoom = LiveKit.create(applicationContext)
                    newRoom.connect(url = serverUrl, token = token)
                    newRoom.localParticipant.setMicrophoneEnabled(false)

                    room = newRoom
                    transferManager = Mp3TransferManager(
                        context = applicationContext,
                        room = newRoom,
                        scope = serviceScope,
                        onStatus = { message ->
                            updateNotification(message)
                            Log.i(TAG, message)
                        },
                        onReceived = { file ->
                            val playIntent = Intent(
                                applicationContext,
                                AudioPlaybackService::class.java
                            ).apply {
                                action = AudioPlaybackService.ACTION_PLAY
                                putExtra(
                                    AudioPlaybackService.EXTRA_FILE_PATH,
                                    file.absolutePath
                                )
                            }
                            androidx.core.content.ContextCompat.startForegroundService(
                                applicationContext,
                                playIntent
                            )
                            updateNotification("MP3 diterima • memutar pesan")
                        }
                    )

                    updateNotification("Terhubung • menunggu audio orang tua")
                } catch (error: Exception) {
                    Log.e(TAG, "Child LiveKit connection failed", error)
                    updateNotification("Koneksi audio gagal: ${error.message ?: "error"}")
                    // Keep the foreground notification visible so the user can see
                    // the failure and reopen the app to retry.
                } finally {
                    connecting = false
                }
            }
        }

        return START_NOT_STICKY
    }

    private fun requestRoomToken(idToken: String, pairCode: String): JSONObject {
        val connection = URL(TOKEN_URL).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")

            val body = JSONObject().apply {
                put("idToken", idToken)
                put("code", pairCode)
            }
            connection.outputStream.use {
                it.write(body.toString().toByteArray(Charsets.UTF_8))
            }

            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream
                else connection.errorStream
            val responseText = stream.bufferedReader().use { it.readText() }
            val response = JSONObject(responseText)

            if (status !in 200..299) {
                throw IllegalStateException(
                    response.optString("error", "Server menolak koneksi")
                )
            }
            return response
        } finally {
            connection.disconnect()
        }
    }

    private fun notification(message: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Parent Audio Connect")
            .setContentText(message)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    private fun updateNotification(message: String) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, notification(message))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Koneksi audio anak",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        serviceScope.launch {
            try {
                room?.disconnect()
                room?.release()
            } catch (error: Exception) {
                Log.w(TAG, "Error closing LiveKit room", error)
            } finally {
                room = null
                transferManager = null
            }
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
