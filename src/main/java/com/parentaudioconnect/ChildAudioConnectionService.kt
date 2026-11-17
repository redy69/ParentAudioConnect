package com.santethp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
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
        const val ACTION_DISCONNECT = "com.parentaudioconnect.action.DISCONNECT_CHILD_AUDIO"
        const val EXTRA_SERVER_ID = "livekit_server_id"
        private const val CHANNEL_ID = "child_audio_connection"
        private const val NOTIFICATION_ID = 4201
        private const val WORKER_URL = "https://parent-audio-token.yudhis-r15.workers.dev/token"
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var room: Room? = null
    private var roomEventsJob: Job? = null
    private var reconnectJob: Job? = null
    private var mp3TransferManager: Mp3TransferManager? = null
    private val activeRemoteAudioTrackIds = ConcurrentHashMap.newKeySet<Int>()
    private var liveHeartbeatJob: Job? = null
    private var pairingRemovalListener: ListenerRegistration? = null
    @Volatile private var connecting = false
    @Volatile private var preferredServerId: String? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_DISCONNECT) {
            updateNotification("Menghentikan koneksi audio…")
            stopSelf(startId)
            return START_NOT_STICKY
        }
        if (intent?.action != null && intent.action != ACTION_CONNECT) {
            return START_NOT_STICKY
        }
        preferredServerId = intent?.getStringExtra(EXTRA_SERVER_ID)?.takeIf { it.isNotBlank() }
        startForeground(NOTIFICATION_ID, notification("Mengaktifkan koneksi audio…"))
        watchForPairingRemoval()
        if (!connecting && room == null) {
            connecting = true
            serviceScope.launch { connectWithRetry() }
        }
        return START_STICKY
    }

    private fun watchForPairingRemoval() {
        val prefs = getSharedPreferences("parent_audio", MODE_PRIVATE)
        val code = prefs.getString("pair_code", null)
        if (code.isNullOrBlank() || prefs.getString("pair_role", null) != "child") return
        pairingRemovalListener?.remove()
        pairingRemovalListener = FirebaseFirestore.getInstance()
            .collection("pairings")
            .document(code)
            .addSnapshotListener { snapshot, error ->
                if (error == null && snapshot != null &&
                    !snapshot.metadata.isFromCache && !snapshot.exists()
                ) {
                    prefs.edit()
                        .remove("pair_role")
                        .remove("pair_code")
                        .putBoolean("remote_camera_armed", false)
                        .apply()
                    ChildDeviceStatusWorker.cancel(applicationContext)
                    stopService(Intent(this, RemoteCameraService::class.java))
                    stopService(Intent(this, AudioPlaybackService::class.java))
                    updateNotification("Hubungan dengan orang tua telah dilepaskan")
                    stopSelf()
                }
            }
    }

    private suspend fun connectWithRetry() {
        var attempt = 0
        try {
            while (true) {
                var connectionStage = "Persiapan"
                try {
                    val prefs = getSharedPreferences("parent_audio", MODE_PRIVATE)
                    val code = prefs.getString("pair_code", null)
                    val role = prefs.getString("pair_role", null)
                    if (code.isNullOrBlank() || role != "child") {
                        updateNotification("Belum ada pasangan perangkat tersimpan")
                        stopSelf()
                        return
                    }

                    connectionStage = "Autentikasi Firebase"
                    val auth = FirebaseAuth.getInstance()
                    val user = auth.currentUser ?: auth.signInAnonymously().await().user
                        ?: throw IllegalStateException("Autentikasi Firebase gagal")

                    connectionStage = "Mengambil token Firebase"
                    val idToken = user.getIdToken(false).await().token
                        ?: throw IllegalStateException("Token Firebase kosong")

                    connectionStage = "Meminta token server"
                    updateNotification("Menghubungkan ke ruang audio…")
                    val response = withContext(Dispatchers.IO) {
                        requestToken(idToken, code, preferredServerId)
                    }

                    connectionStage = "Memvalidasi akses server"
                    val returnedRole = response.optString("role")
                    if (returnedRole != "child") {
                        throw IllegalStateException("Server tidak mengembalikan akses anak")
                    }

                    connectionStage = "Menghubungkan LiveKit"
                    val newRoom = LiveKitFailover.connect(
                        response = response,
                        createRoom = {
                            LiveKit.create(
                                appContext = applicationContext,
                                overrides = LiveKitOverrides(
                                    audioOptions = AudioOptions(
                                        audioOutputType = AudioType.MediaAudioType()
                                    )
                                )
                            )
                        },
                        prepareRoom = { candidateRoom ->
                        // Track the in-flight candidate before connect so a disconnect during
                        // the handshake cannot be mistaken for a healthy connected room.
                        room = candidateRoom
                        roomEventsJob?.cancel()
                        roomEventsJob = serviceScope.launch {
                            candidateRoom.events.collect { event ->
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
                                        if (room === candidateRoom) {
                                            room = null
                                            activeRemoteAudioTrackIds.clear()
                                            stopLiveAudioHeartbeat()
                                            ChildAudioActivityReporter.setLiveAudioActive(
                                                applicationContext, false
                                            )
                                            updateNotification("Koneksi audio terputus • mencoba kembali")
                                            // Queue recovery even during the connect/finally window.
                                            // The recovery job waits until this loop has finished.
                                            scheduleAudioReconnect(candidateRoom)
                                        }
                                    }
                                    else -> Unit
                                }
                            }
                        }
    
    
                        }
                    )
                    if (room !== newRoom) {
                        // A disconnect may arrive immediately after connect() succeeds.
                        try { newRoom.disconnect() } catch (_: Exception) { }
                        try { newRoom.release() } catch (_: Exception) { }
                        throw IllegalStateException("Koneksi audio terputus saat proses penyambungan")
                    }
                    connectionStage = "Menyiapkan audio"
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
                    room = null
                    mp3TransferManager = null
                    activeRemoteAudioTrackIds.clear()
                    stopLiveAudioHeartbeat()
                    ChildAudioActivityReporter.setLiveAudioActive(applicationContext, false)
                    attempt++
                    val errorDetails = generateSequence(error as Throwable?) { it.cause }
                        .take(4)
                        .map { cause ->
                            cause.localizedMessage?.takeIf { it.isNotBlank() }
                                ?: cause.javaClass.simpleName
                        }
                        .joinToString(" <- ")
                        .take(150)
                    val diagnostic = "Gagal $connectionStage: ${error.javaClass.simpleName}: $errorDetails"
                    Log.e("SantetHP-Audio", diagnostic, error)
                    getSharedPreferences("parent_audio", MODE_PRIVATE).edit()
                        .putString("child_audio_last_connection_error", diagnostic)
                        .putLong("child_audio_last_connection_error_at", System.currentTimeMillis())
                        .apply()
                    updateNotification("$diagnostic • mencoba lagi")
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

    private fun scheduleAudioReconnect(disconnectedRoom: Room) {
        if (reconnectJob?.isActive == true) return
        reconnectJob = serviceScope.launch {
            delay(1_500)
            // A disconnect can arrive just before connectWithRetry() exits.
            // Wait for that loop to finish instead of losing the retry request.
            while (connecting && isActive) delay(500)
            // Abort if a newer candidate has already become the active room.
            if (!isActive || room != null) return@launch

            roomEventsJob?.cancel()
            roomEventsJob = null
            mp3TransferManager = null
            try {
                disconnectedRoom.disconnect()
            } catch (_: Exception) { }
            try {
                disconnectedRoom.release()
            } catch (_: Exception) { }

            if (!connecting && isActive && room == null) {
                connecting = true
                connectWithRetry()
            }
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

    private fun requestToken(idToken: String, code: String, preferredServerId: String?): JSONObject {
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
                if (!preferredServerId.isNullOrBlank()) put("preferredServerId", preferredServerId)
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
        pairingRemovalListener?.remove()
        pairingRemovalListener = null
        reconnectJob?.cancel()
        reconnectJob = null
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
