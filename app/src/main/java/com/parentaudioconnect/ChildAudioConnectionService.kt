package com.santethp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.android.gms.tasks.Task
import io.livekit.android.AudioOptions
import io.livekit.android.AudioType
import io.livekit.android.LiveKit
import io.livekit.android.LiveKitOverrides
import io.livekit.android.events.collect
import io.livekit.android.events.RoomEvent
import io.livekit.android.room.Room
import io.livekit.android.room.track.RemoteAudioTrack
import io.livekit.android.room.track.Track
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
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
    private var mp3TransferManager: Mp3TransferManager? = null
    private val activeRemoteAudioTrackIds = ConcurrentHashMap.newKeySet<Int>()
    private var liveHeartbeatJob: Job? = null
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

                    val newRoom = LiveKit.create(
                        appContext = applicationContext,
                        overrides = LiveKitOverrides(
                            audioOptions = AudioOptions(
                                audioOutputType = AudioType.MediaAudioType()
                            )
                        )
                    )
                    roomEventsJob?.cancel()
                    roomEventsJob = serviceScope.launch {
                        newRoom.events.collect { event ->
                            when (event) {
                                is RoomEvent.TrackSubscribed -> {
                                    if (event.track is RemoteAudioTrack) {
                                        activeRemoteAudioTrackIds.add(event.track.hashCode())
                                        maximizeMediaVolume()
                                        ChildAudioActivityReporter.setLiveAudioActive(
                                            applicationContext, true
                                        )
                                        updateNotification("Audio live sedang diputar • mikrofon anak mati")
                                        startLiveAudioHeartbeat()
                                    }
                                }
                                is RoomEvent.TrackMuted -> {
                                    if (event.publication.kind == Track.Kind.AUDIO) {
                                        // Keep other subscribed audio tracks active if any remain.
                                        val mutedTrack = event.publication.track
                                        if (mutedTrack is RemoteAudioTrack) {
                                            activeRemoteAudioTrackIds.remove(mutedTrack.hashCode())
                                        } else {
                                            activeRemoteAudioTrackIds.clear()
                                        }
                                        val stillActive = activeRemoteAudioTrackIds.isNotEmpty()
                                        if (!stillActive) stopLiveAudioHeartbeat()
                                        ChildAudioActivityReporter.setLiveAudioActive(
                                            applicationContext, stillActive
                                        )
                                        updateNotification(
                                            if (stillActive) "Audio live masih diputar"
                                            else "Audio live berhenti • menunggu orang tua"
                                        )
                                    }
                                }
                                is RoomEvent.TrackUnmuted -> {
                                    val unmutedTrack = event.publication.track
                                    if (event.publication.kind == Track.Kind.AUDIO &&
                                        unmutedTrack is RemoteAudioTrack
                                    ) {
                                        activeRemoteAudioTrackIds.add(unmutedTrack.hashCode())
                                        maximizeMediaVolume()
                                        ChildAudioActivityReporter.setLiveAudioActive(
                                            applicationContext, true
                                        )
                                        updateNotification("Audio live sedang diputar • mikrofon anak mati")
                                        startLiveAudioHeartbeat()
                                    }
                                }
                                is RoomEvent.TrackUnsubscribed -> {
                                    if (event.track is RemoteAudioTrack) {
                                        activeRemoteAudioTrackIds.remove(event.track.hashCode())
                                        if (activeRemoteAudioTrackIds.isEmpty()) {
                                            stopLiveAudioHeartbeat()
                                            ChildAudioActivityReporter.setLiveAudioActive(
                                                applicationContext, false
                                            )
                                            updateNotification("Audio live berhenti • koneksi tetap aktif")
                                        }
                                        // Keep the requested maximum volume after each audio session.
                                    }
                                }
                                is RoomEvent.Disconnected -> {
                                    activeRemoteAudioTrackIds.clear()
                                    stopLiveAudioHeartbeat()
                                    ChildAudioActivityReporter.setLiveAudioActive(
                                        applicationContext, false
                                    )
                                    updateNotification("Koneksi audio terputus • mencoba kembali")
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
                    // LiveKit MediaAudioType uses Android's normal media route and STREAM_MUSIC.
                    maximizeMediaVolume()
                    room = newRoom
                    mp3TransferManager = Mp3TransferManager(
                        context = applicationContext,
                        room = newRoom,
                        scope = serviceScope,
                        onStatus = { message ->
                            // onReceived starts playback and sets its own notification.
                            // Do not overwrite that state with the generic transfer-complete text.
                            if (message != "MP3 berhasil diterima!") updateNotification(message)
                        },
                        onReceived = { file ->
                            maximizeMediaVolume()
                            val playbackIntent = Intent(applicationContext, AudioPlaybackService::class.java).apply {
                                action = AudioPlaybackService.ACTION_PLAY
                                putExtra(AudioPlaybackService.EXTRA_FILE_PATH, file.absolutePath)
                            }
                            try {
                                androidx.core.content.ContextCompat.startForegroundService(applicationContext, playbackIntent)
                                updateNotification("MP3 diterima • memutar audio")
                            } catch (error: Exception) {
                                updateNotification("MP3 diterima, gagal memutar: ${error.localizedMessage}")
                            }
                        }
                    )
                    updateNotification("Terhubung • audio live dan MP3 aktif • mikrofon mati")
                    return
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    roomEventsJob?.cancel()
                    roomEventsJob = null
                    mp3TransferManager = null
                    activeRemoteAudioTrackIds.clear()
                    stopLiveAudioHeartbeat()
                    ChildAudioActivityReporter.setLiveAudioActive(applicationContext, false)
                    attempt++
                    updateNotification("Koneksi gagal. Mencoba kembali ($attempt)…")
                    // Retry quickly after boot/network startup, then keep a short steady backoff.
                    val retryDelayMs = when {
                        attempt == 1 -> 1_000L
                        attempt == 2 -> 2_000L
                        attempt == 3 -> 4_000L
                        else -> 8_000L
                    }
                    delay(retryDelayMs)
                }
            }
        } finally {
            connecting = false
        }
    }

    /** Keep LiveKit listener output at maximum on the Android media stream. */
    private fun maximizeMediaVolume() {
        val audioManager = getSystemService(android.media.AudioManager::class.java)
        val maximum = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
        audioManager.setStreamVolume(
            android.media.AudioManager.STREAM_MUSIC,
            maximum,
            android.media.AudioManager.FLAG_SHOW_UI
        )
    }

    private fun startLiveAudioHeartbeat() {
        if (liveHeartbeatJob?.isActive == true) return
        liveHeartbeatJob = serviceScope.launch {
            while (isActive && activeRemoteAudioTrackIds.isNotEmpty()) {
                delay(10_000)
                if (activeRemoteAudioTrackIds.isNotEmpty()) {
                    ChildAudioActivityReporter.refreshLiveAudioHeartbeat(applicationContext)
                }
            }
        }
    }

    private fun stopLiveAudioHeartbeat() {
        liveHeartbeatJob?.cancel()
        liveHeartbeatJob = null
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
            .setContentTitle("SantetHP")
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
        activeRemoteAudioTrackIds.clear()
        stopLiveAudioHeartbeat()
        ChildAudioActivityReporter.setLiveAudioActive(applicationContext, false)
        mp3TransferManager = null
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
