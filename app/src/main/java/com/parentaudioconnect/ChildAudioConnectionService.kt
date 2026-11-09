package com.parentaudioconnect

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.android.gms.tasks.Task
import io.livekit.android.LiveKit
import io.livekit.android.events.RoomEvent
import io.livekit.android.room.Room
import io.livekit.android.room.track.RemoteAudioTrack
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
 * Receive-only background connection for a paired child device.
 * The child's microphone is never enabled by this service.
 */
class ChildAudioConnectionService : Service() {

    companion object {
        const val ACTION_CONNECT = "com.parentaudioconnect.action.CONNECT_CHILD_AUDIO"
        private const val CHANNEL_ID = "child_audio_connection"
        private const val NOTIFICATION_ID = 4201
        private const val WORKER_URL = "https://parent-audio-token.yudhis-r15.workers.dev/token"
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var room: Room? = null
    private var roomEventsJob: Job? = null
    private var previousVoiceCallVolume: Int? = null
    private var activeRemoteAudioTracks = 0
    @Volatile private var connecting = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, notification("Memulihkan koneksi audio…"))
        if (!connecting && room == null) {
            connecting = true
            serviceScope.launch { connectWithRetry() }
        }
        return START_STICKY
    }

    private suspend fun connectWithRetry() {
        var attempt = 0
        try {
            while (true) {
                try {
                    val prefs = getSharedPreferences("parent_audio", MODE_PRIVATE)
                    val code = prefs.getString("pair_code", null)
                    val role = prefs.getString("pair_role", null)
                    if (code.isNullOrBlank() || role != "child") {
                        updateNotification("Belum ada pasangan perangkat tersimpan")
                        stopSelf()
                        return
                    }

                    val auth = FirebaseAuth.getInstance()
                    val user = auth.currentUser ?: auth.signInAnonymously().await().user
                        ?: throw IllegalStateException("Autentikasi Firebase gagal")
                    val idToken = user.getIdToken(false).await().token
                        ?: throw IllegalStateException("Token Firebase kosong")

                    updateNotification("Menghubungkan ke ruang audio…")
                    val response = withContext(Dispatchers.IO) {
                        requestToken(idToken, code)
                    }
                    val returnedRole = response.optString("role")
                    if (returnedRole != "child") {
                        throw IllegalStateException("Server tidak mengembalikan akses anak")
                    }

                    val newRoom = LiveKit.create(applicationContext)
                    roomEventsJob?.cancel()
                    roomEventsJob = serviceScope.launch {
                        newRoom.events.collect { event ->
                            when (event) {
                                is RoomEvent.TrackSubscribed -> {
                                    if (event.track is RemoteAudioTrack) {
                                        activeRemoteAudioTracks++
                                        maximizeLiveAudioVolume()
                                    }
                                }
                                is RoomEvent.TrackUnsubscribed -> {
                                    if (event.track is RemoteAudioTrack) {
                                        activeRemoteAudioTracks =
                                            (activeRemoteAudioTracks - 1).coerceAtLeast(0)
                                        if (activeRemoteAudioTracks == 0) {
                                            restoreLiveAudioVolume()
                                        }
                                    }
                                }
                                is RoomEvent.Disconnected -> {
                                    activeRemoteAudioTracks = 0
                                    restoreLiveAudioVolume()
                                }
                                else -> Unit
                            }
                        }
                    }

                    newRoom.connect(
                        url = response.getString("serverUrl"),
                        token = response.getString("token")
                    )
                    newRoom.localParticipant.setMicrophoneEnabled(false)
                    room = newRoom
                    updateNotification("Terhubung • penerimaan audio aktif • mikrofon mati")
                    return
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    roomEventsJob?.cancel()
                    roomEventsJob = null
                    activeRemoteAudioTracks = 0
                    restoreLiveAudioVolume()
                    attempt++
                    updateNotification("Koneksi gagal. Mencoba kembali ($attempt)…")
                    delay((attempt.coerceAtMost(6) * 5_000L))
                }
            }
        } finally {
            connecting = false
        }
    }

    /**
     * LiveKit uses Android's voice-call stream for its default communication audio mode.
     * Raise that stream only while a remote audio track is subscribed, then restore it.
     */
    private fun maximizeLiveAudioVolume() {
        val audioManager = getSystemService(AudioManager::class.java)
        if (previousVoiceCallVolume == null) {
            previousVoiceCallVolume =
                audioManager.getStreamVolume(AudioManager.STREAM_VOICE_CALL)
        }
        val maximum = audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL)
        audioManager.setStreamVolume(
            AudioManager.STREAM_VOICE_CALL,
            maximum,
            AudioManager.FLAG_SHOW_UI
        )
    }

    private fun restoreLiveAudioVolume() {
        val previous = previousVoiceCallVolume ?: return
        previousVoiceCallVolume = null
        val audioManager = getSystemService(AudioManager::class.java)
        audioManager.setStreamVolume(
            AudioManager.STREAM_VOICE_CALL,
            previous.coerceIn(
                0,
                audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL)
            ),
            0
        )
    }

    private fun requestToken(idToken: String, code: String): JSONObject {
        val connection = URL(WORKER_URL).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            val body = JSONObject().apply {
                put("idToken", idToken)
                put("code", code)
            }
            connection.outputStream.use {
                it.write(body.toString().toByteArray(Charsets.UTF_8))
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val responseText = stream?.bufferedReader()?.use { it.readText() } ?: "{}"
            val response = JSONObject(responseText)
            if (status !in 200..299) {
                throw IllegalStateException(response.optString("error", "Server menolak koneksi"))
            }
            return response
        } finally {
            connection.disconnect()
        }
    }

    private fun notification(message: String): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentTitle("Parent Audio Connect")
            .setContentText(message)
            .setContentIntent(openApp)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(message: String) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, notification(message))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Koneksi audio perangkat anak",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Menampilkan status koneksi audio yang dipulihkan otomatis"
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        roomEventsJob?.cancel()
        roomEventsJob = null
        activeRemoteAudioTracks = 0
        restoreLiveAudioVolume()
        serviceScope.cancel()
        val activeRoom = room
        room = null
        if (activeRoom != null) {
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                try {
                    activeRoom.disconnect()
                } finally {
                    activeRoom.release()
                }
            }
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

private suspend fun <T> Task<T>.await(): T =
    suspendCancellableCoroutine { continuation ->
        addOnCompleteListener { task ->
            if (!continuation.isActive) return@addOnCompleteListener
            if (task.isSuccessful) {
                continuation.resume(task.result)
            } else {
                continuation.resumeWithException(
                    task.exception ?: IllegalStateException("Operasi Firebase gagal")
                )
            }
        }
    }
