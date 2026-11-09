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
import com.google.firebase.auth.FirebaseAuth
import io.livekit.android.LiveKit
import io.livekit.android.events.RoomEvent
import io.livekit.android.room.Room
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Keeps the child's LiveKit room in a foreground service while the UI is backgrounded.
 * Reconnects after a terminal LiveKit disconnect, using a fresh Firebase and LiveKit token.
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
        private val RECONNECT_DELAYS_SECONDS = listOf(2L, 5L, 10L, 20L, 30L)
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var room: Room? = null
    private var transferManager: Mp3TransferManager? = null
    private var pairCode: String? = null
    private var connecting = false
    private var destroyed = false
    private var reconnectJob: Job? = null
    private var roomEventsJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action != ACTION_CONNECT) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        val incomingPairCode = intent.getStringExtra(EXTRA_PAIR_CODE)
        val firebaseIdToken = intent.getStringExtra(EXTRA_FIREBASE_ID_TOKEN)
        if (incomingPairCode.isNullOrBlank() || firebaseIdToken.isNullOrBlank()) {
            updateNotification("Data koneksi tidak lengkap • buka aplikasi anak")
            stopSelf(startId)
            return START_NOT_STICKY
        }

        pairCode = incomingPairCode
        startForeground(NOTIFICATION_ID, notification("Menghubungkan audio anak..."))

        if (room == null && !connecting && reconnectJob == null) {
            connecting = true
            serviceScope.launch {
                try {
                    connectNewRoom(incomingPairCode, firebaseIdToken)
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    Log.e(TAG, "Initial LiveKit connection failed", error)
                    updateNotification("Koneksi gagal: ${error.message ?: "error"} • mencoba ulang")
                    scheduleReconnect()
                } finally {
                    connecting = false
                }
            }
        }
        return START_NOT_STICKY
    }

    private suspend fun connectNewRoom(code: String, fallbackIdToken: String) {
        val freshIdToken = try {
            getFreshFirebaseIdToken()
        } catch (error: Exception) {
            Log.w(TAG, "Could not refresh Firebase token; using supplied token", error)
            fallbackIdToken
        }

        val response = withContext(Dispatchers.IO) {
            requestRoomToken(freshIdToken, code)
        }
        val token = response.getString("token")
        val serverUrl = response.getString("serverUrl")
        require(response.getString("role") == "child") {
            "Peran perangkat bukan anak"
        }

        val newRoom = LiveKit.create(applicationContext)
        roomEventsJob?.cancel()
        roomEventsJob = serviceScope.launch {
            newRoom.events.collect { event ->
                when (event) {
                    is RoomEvent.Reconnecting -> {
                        if (room === newRoom) {
                            updateNotification("Koneksi terputus sementara • LiveKit menyambung ulang")
                        }
                    }
                    is RoomEvent.Reconnected -> {
                        if (room === newRoom) {
                            updateNotification("Terhubung kembali • menunggu audio orang tua")
                        }
                    }
                    is RoomEvent.Disconnected -> {
                        if (room === newRoom && !destroyed) {
                            Log.w(TAG, "LiveKit disconnected: ${event.error} / ${event.reason}")
                            scheduleReconnect()
                        }
                    }
                    is RoomEvent.FailedToConnect -> {
                        if (room === newRoom && !destroyed) {
                            Log.e(TAG, "LiveKit failed to connect", event.error)
                            scheduleReconnect()
                        }
                    }
                    else -> Unit
                }
            }
        }

        try {
            newRoom.connect(url = serverUrl, token = token)
            newRoom.localParticipant.setMicrophoneEnabled(false)
            room = newRoom
            installMp3Receiver(newRoom)
            updateNotification("Terhubung • menunggu suara orang tua")
        } catch (error: Exception) {
            roomEventsJob?.cancel()
            try {
                newRoom.disconnect()
                newRoom.release()
            } catch (cleanupError: Exception) {
                Log.w(TAG, "Failed to clean up failed room", cleanupError)
            }
            throw error
        }
    }

    private fun installMp3Receiver(targetRoom: Room) {
        transferManager = Mp3TransferManager(
            context = applicationContext,
            room = targetRoom,
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
                    putExtra(AudioPlaybackService.EXTRA_FILE_PATH, file.absolutePath)
                }
                androidx.core.content.ContextCompat.startForegroundService(
                    applicationContext,
                    playIntent
                )
                updateNotification("MP3 diterima • memutar pesan")
            }
        )
    }

    private fun scheduleReconnect() {
        if (destroyed || reconnectJob?.isActive == true) return
        reconnectJob = serviceScope.launch {
            var attempt = 0
            while (!destroyed) {
                val waitSeconds = RECONNECT_DELAYS_SECONDS[
                    attempt.coerceAtMost(RECONNECT_DELAYS_SECONDS.lastIndex)
                ]
                updateNotification("Koneksi audio terputus • mencoba ulang dalam ${waitSeconds} dtk")
                delay(waitSeconds * 1000)
                if (destroyed) break

                val oldRoom = room
                room = null
                transferManager = null
                roomEventsJob?.cancel()
                roomEventsJob = null
                if (oldRoom != null) {
                    try {
                        oldRoom.disconnect()
                        oldRoom.release()
                    } catch (error: Exception) {
                        Log.w(TAG, "Error releasing disconnected room", error)
                    }
                }

                try {
                    connecting = true
                    val code = pairCode ?: throw IllegalStateException("Kode pasangan hilang")
                    connectNewRoom(code, "")
                    Log.i(TAG, "LiveKit reconnect succeeded")
                    return@launch
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    Log.e(TAG, "Reconnect attempt failed", error)
                    attempt++
                } finally {
                    connecting = false
                }
            }
        }
    }

    private suspend fun getFreshFirebaseIdToken(): String =
        suspendCancellableCoroutine { continuation ->
            val user = FirebaseAuth.getInstance().currentUser
            if (user == null) {
                continuation.resumeWithException(
                    IllegalStateException("Firebase user tidak tersedia")
                )
                return@suspendCancellableCoroutine
            }
            user.getIdToken(true)
                .addOnSuccessListener { result ->
                    val token = result.token
                    if (token.isNullOrBlank()) {
                        continuation.resumeWithException(
                            IllegalStateException("Firebase ID token kosong")
                        )
                    } else if (continuation.isActive) {
                        continuation.resume(token)
                    }
                }
                .addOnFailureListener { error ->
                    if (continuation.isActive) continuation.resumeWithException(error)
                }
        }

    private fun requestRoomToken(idToken: String, pairCode: String): JSONObject {
        require(idToken.isNotBlank()) { "Firebase ID token kosong" }
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
                    response.optString("error", "Server menolak koneksi (HTTP $status)")
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
        destroyed = true
        reconnectJob?.cancel()
        roomEventsJob?.cancel()
        val currentRoom = room
        room = null
        transferManager = null
        serviceScope.launch {
            try {
                currentRoom?.disconnect()
                currentRoom?.release()
            } catch (error: Exception) {
                Log.w(TAG, "Error closing LiveKit room", error)
            } finally {
                serviceScope.cancel()
            }
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
