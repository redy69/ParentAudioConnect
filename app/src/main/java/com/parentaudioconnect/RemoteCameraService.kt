package com.santethp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.Context
import android.os.SystemClock
import android.content.pm.ServiceInfo
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.util.Log
import android.os.Build
import android.os.IBinder
import androidx.camera.core.Camera
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import io.livekit.android.LiveKit
import io.livekit.android.room.Room
import io.livekit.android.events.collect
import io.livekit.android.events.RoomEvent
import io.livekit.android.room.track.CameraPosition
import io.livekit.android.room.track.LocalVideoTrack
import io.livekit.android.room.track.VideoPreset169
import io.livekit.android.room.track.VideoPreset43
import io.livekit.android.room.track.VideoEncoding
import io.livekit.android.room.track.Track
import io.livekit.android.room.track.video.NoDropVideoProcessor
import livekit.org.webrtc.VideoFrame
import livekit.org.webrtc.VideoSink
import livekit.org.webrtc.getCameraX
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Camera standby is explicitly enabled from the child's visible app.
 * FCM can activate the camera only while this already-running service is armed.
 * The camera automatically turns off after ten minutes.
 */
class RemoteCameraService : Service() {

    companion object {
        const val ACTION_ARM = "com.santethp.camera.ARM"
        const val ACTION_START_CAMERA = "com.santethp.camera.START"
        const val ACTION_STOP_CAMERA = "com.santethp.camera.STOP"
        const val ACTION_SWITCH_CAMERA = "com.santethp.camera.SWITCH"
        const val ACTION_REVOKE_AUDIO = "com.santethp.camera.REVOKE_AUDIO"
        const val ACTION_DISARM = "com.santethp.camera.DISARM"
        const val EXTRA_REQUEST_ID = "request_id"
        const val EXTRA_SERVER_ID = "livekit_server_id"
        private const val CHANNEL_ID = "remote_camera"
        private const val NOTIFICATION_ID = 4301
        private const val CAMERA_URL = "https://parent-audio-token.yudhis-r15.workers.dev/camera-token"
        private const val MAX_CAMERA_SESSION_MS = 10 * 60 * 1000L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var room: Room? = null
    private var cameraTrack: LocalVideoTrack? = null
    private var cameraStopJob: Job? = null
    private var cameraSettingsJob: Job? = null
    @Volatile private var brightnessProcessor: AdaptiveBrightnessVideoProcessor? = null
    private var roomEventsJob: Job? = null
    private var reconnectJob: Job? = null
    private var pendingCameraRequestId: String? = null
    private var pendingCameraFacing: String = "back"
    @Volatile private var connecting = false
    @Volatile private var preferredServerId: String? = null
    @Volatile private var activeServerId: String? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_ARM -> armFromVisibleApp()
            ACTION_START_CAMERA -> {
                preferredServerId = intent.getStringExtra(EXTRA_SERVER_ID)?.takeIf { it.isNotBlank() }
                if (isArmed()) {
                    val requestId = intent.getStringExtra(EXTRA_REQUEST_ID)
                    val facing = intent.getStringExtra("facing") ?: "back"
                    if (room != null && preferredServerId != null && activeServerId != preferredServerId) {
                        pendingCameraRequestId = requestId
                        pendingCameraFacing = facing
                        val oldRoom = room
                        room = null
                        cameraTrack = null
                        stopCameraSettings()
                        roomEventsJob?.cancel()
                        roomEventsJob = null
                        scope.launch {
                            try { oldRoom?.localParticipant?.setCameraEnabled(false) } catch (_: Exception) { }
                            try { oldRoom?.disconnect() } catch (_: Exception) { }
                            try { oldRoom?.release() } catch (_: Exception) { }
                            activeServerId = null
                            ensureCameraRoomConnected()
                        }
                    } else if (room != null) {
                        startCamera(requestId, facing)
                    } else {
                        pendingCameraRequestId = requestId
                        pendingCameraFacing = facing
                        updateNotification("Koneksi kamera terputus • menyambungkan kembali…")
                        ensureCameraRoomConnected()
                    }
                } else {
                    updateNotification("Siaga kamera tidak aktif. Buka aplikasi anak dan aktifkan siaga kamera.")
                }
            }
            ACTION_STOP_CAMERA -> stopCamera("Kamera dihentikan")
            ACTION_SWITCH_CAMERA -> switchCamera(intent.getStringExtra("facing") ?: "back")
            ACTION_REVOKE_AUDIO -> revokeCameraAudioConsent()
            ACTION_DISARM -> disarm()
            else -> {
                if (!isArmed()) stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun armFromVisibleApp() {
        if (checkSelfPermission(android.Manifest.permission.CAMERA) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            updateNotification("Izin kamera belum diberikan")
            stopSelf()
            return
        }
        try {
            startCameraForeground()
            getSharedPreferences("parent_audio", MODE_PRIVATE).edit()
                .putBoolean("remote_camera_armed", true).apply()
        } catch (error: Exception) {
            getSharedPreferences("parent_audio", MODE_PRIVATE).edit()
                .putBoolean("remote_camera_armed", false).apply()
            stopSelf()
            return
        }
        ensureCameraRoomConnected()
    }

    private fun ensureCameraRoomConnected() {
        if (!isArmed() || room != null || connecting) return
        connecting = true
        scope.launch { connectCameraRoom() }
    }

    private fun scheduleCameraReconnect() {
        if (!isArmed() || reconnectJob?.isActive == true) return
        reconnectJob = scope.launch {
            // Keep retrying while standby remains enabled; network may take longer to return.
            while (isArmed() && room == null) {
                delay(3000)
                if (isArmed() && room == null && !connecting) {
                    connecting = true
                    connectCameraRoom()
                }
            }
        }
    }

    private fun startCameraForeground() {
        val notification = notification("Siaga kamera aktif • kamera belum menyala")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val cameraType = ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            val audioConsent = getSharedPreferences("parent_audio", MODE_PRIVATE)
                .getBoolean("remote_camera_audio_consent", false)
            val micGranted = checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
            val serviceTypes = if (audioConsent && micGranted) {
                cameraType or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            } else cameraType
            startForeground(NOTIFICATION_ID, notification, serviceTypes)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun hasCameraAudioConsent(): Boolean =
        getSharedPreferences("parent_audio", MODE_PRIVATE)
            .getBoolean("remote_camera_audio_consent", false) &&
            checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED

    private fun revokeCameraAudioConsent() {
        getSharedPreferences("parent_audio", MODE_PRIVATE).edit()
            .putBoolean("remote_camera_audio_consent", false).apply()
        scope.launch {
            try { room?.localParticipant?.setMicrophoneEnabled(false) } catch (_: Exception) { }
            val cameraActive = cameraTrack != null
            updateNotification(
                if (cameraActive) "KAMERA AKTIF • suara lingkungan dimatikan dari HP anak"
                else "Izin suara kamera dimatikan • kamera tetap siaga"
            )
        }
    }

    private suspend fun connectCameraRoom() {
        try {
            val prefs = getSharedPreferences("parent_audio", MODE_PRIVATE)
            val code = prefs.getString("pair_code", null)
                ?: throw IllegalStateException("Kode pasangan tidak ditemukan")
            if (prefs.getString("pair_role", null) != "child") {
                throw IllegalStateException("Layanan kamera hanya untuk HP anak")
            }
            val user = FirebaseAuth.getInstance().currentUser
                ?: FirebaseAuth.getInstance().signInAnonymously().await().user
                ?: throw IllegalStateException("Autentikasi Firebase gagal")
            val idToken = user.getIdToken(false).await().token
                ?: throw IllegalStateException("Token Firebase kosong")
            updateNotification("Menghubungkan ruang kamera aman…")
            val response = withContext(Dispatchers.IO) { requestCameraToken(idToken, code, preferredServerId) }
            if (response.optString("role") != "child") {
                throw IllegalStateException("Server tidak memberikan akses kamera anak")
            }
            val liveKitConnection = LiveKitFailover.connectWithServer(
                response = response,
                createRoom = { LiveKit.create(applicationContext) },
                prepareRoom = { candidateRoom ->
                    // Track the in-flight candidate so a handshake-time disconnect is visible.
                    room = candidateRoom
                    roomEventsJob?.cancel()
                    roomEventsJob = scope.launch {
                        candidateRoom.events.collect { event ->
                            if (event is RoomEvent.Disconnected && isArmed() &&
                                room === candidateRoom
                            ) {
                                room = null
                                cameraTrack = null
                                stopCameraSettings()
                                updateNotification("Koneksi kamera terputus • mencoba menyambungkan kembali…")
                                // The retry loop waits for this connection attempt to finish.
                                scheduleCameraReconnect()
                            }
                        }
                    }
                }
            )
            val newRoom = liveKitConnection.room
            activeServerId = liveKitConnection.serverId
            if (room !== newRoom) {
                try { newRoom.disconnect() } catch (_: Exception) { }
                try { newRoom.release() } catch (_: Exception) { }
                throw IllegalStateException("Koneksi kamera terputus saat proses penyambungan")
            }

            // The parent may select a different LiveKit server while this initial
            // child connection is still handshaking. Never publish the camera into
            // the stale room: reconnect using the latest server ID first.
            val requiredServerId = preferredServerId
            if (requiredServerId != null && requiredServerId != activeServerId) {
                room = null
                cameraTrack = null
                try { newRoom.disconnect() } catch (_: Exception) { }
                try { newRoom.release() } catch (_: Exception) { }
                activeServerId = null
                updateNotification("Server kamera berubah • menyambungkan ke server pilihan orang tua…")
                scheduleCameraReconnect()
                return
            }

            newRoom.localParticipant.setMicrophoneEnabled(false)
            updateNotification("Siaga kamera aktif • menunggu permintaan orang tua")
            val queuedRequestId = pendingCameraRequestId
            if (queuedRequestId != null) {
                pendingCameraRequestId = null
                startCamera(queuedRequestId, pendingCameraFacing)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            room = null
            activeServerId = null
            updateNotification("Kamera belum siap: ${error.localizedMessage ?: "koneksi gagal"}")
            scheduleCameraReconnect()
        } finally {
            connecting = false
        }
    }

    private fun startCamera(requestId: String?, facing: String) {
        if (cameraStopJob?.isActive == true) return
        cameraStopJob = scope.launch {
            try {
                val activeRoom = room ?: throw IllegalStateException("Ruang kamera belum terhubung")
                updateNotification("Permintaan diterima • kamera sedang diaktifkan")
                // Use one LiveKit-owned camera session. The processor is attached
                // to this exact track; do not register a second CameraX session.
                val participant = activeRoom.localParticipant
                val captureOptions = participant.videoTrackCaptureDefaults.copy(
                    position = if (facing == "front") CameraPosition.FRONT else CameraPosition.BACK,
                    captureParams = VideoPreset43.H1080.capture
                )
                participant.videoTrackCaptureDefaults = captureOptions
                participant.videoTrackPublishDefaults =
                    participant.videoTrackPublishDefaults.copy(
                        videoEncoding = VideoPreset43.H1080.encoding
                    )

                val processor = AdaptiveBrightnessVideoProcessor()
                brightnessProcessor = processor
                val track = participant.createVideoTrack(
                    options = captureOptions,
                    videoProcessor = processor
                )
                cameraTrack = track
                try {
                    track.startCapture()
                    // Observe LiveKit's existing CameraX session and reapply supported
                    // hardware processing/exposure after every camera switch.
                    observeCameraSessions(track, processor)
                    val published = participant.publishVideoTrack(track)
                    if (!published) {
                        throw IllegalStateException("LiveKit gagal menerbitkan kamera")
                    }
                } catch (error: Exception) {
                    cameraTrack = null
                    stopCameraSettings()
                    runCatching { participant.unpublishTrack(track) }
                    runCatching { track.dispose() }
                    throw error
                }
                val audioEnabled = if (hasCameraAudioConsent()) {
                    try { activeRoom.localParticipant.setMicrophoneEnabled(true) } catch (_: Exception) { false }
                } else {
                    try { activeRoom.localParticipant.setMicrophoneEnabled(false) } catch (_: Exception) { }
                    false
                }
                updateNotification(
                    if (audioEnabled) "KAMERA + SUARA AKTIF • suara lingkungan dikirim ke orang tua"
                    else "KAMERA AKTIF • suara lingkungan tidak diizinkan"
                )
                delay(MAX_CAMERA_SESSION_MS)
                stopCamera("Sesi kamera berakhir otomatis setelah 10 menit")
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                updateNotification("Kamera gagal aktif: ${error.localizedMessage ?: "kesalahan"}")
            }
        }
    }

    /** Follows LiveKit's CameraX session and replaces settings on every camera switch. */
    private fun observeCameraSessions(track: LocalVideoTrack, processor: AdaptiveBrightnessVideoProcessor) {
        cameraSettingsJob?.cancel()
        val cameraFlow = track.capturer.getCameraX() ?: return
        cameraSettingsJob = scope.launch {
            cameraFlow.filterNotNull().distinctUntilChanged().collectLatest { camera ->
                val controller = CameraExposureController(camera, applicationContext)
                processor.setExposureFeedback(controller::submit)
                try {
                    applyCameraHardwareQualityModes(camera)
                    controller.run()
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    Log.w("SantetHPCamera", "Camera session settings could not be maintained", error)
                } finally { processor.setExposureFeedback(null) }
            }
        }
    }

    private fun stopCameraSettings() {
        cameraSettingsJob?.cancel()
        cameraSettingsJob = null
        brightnessProcessor?.setExposureFeedback(null)
        brightnessProcessor = null
    }

    /** Merges supported hardware quality modes into the active CameraX request. */
    private suspend fun applyCameraHardwareQualityModes(camera: Camera) {
        try {
            val info = Camera2CameraInfo.from(camera.cameraInfo)
            val noise = info.getCameraCharacteristic(CameraCharacteristics.NOISE_REDUCTION_AVAILABLE_NOISE_REDUCTION_MODES)?.toSet().orEmpty()
            val edge = info.getCameraCharacteristic(CameraCharacteristics.EDGE_AVAILABLE_EDGE_MODES)?.toSet().orEmpty()
            val options = CaptureRequestOptions.Builder()
            var count = 0
            if (noise.contains(CaptureRequest.NOISE_REDUCTION_MODE_HIGH_QUALITY)) {
                options.setCaptureRequestOption(CaptureRequest.NOISE_REDUCTION_MODE, CaptureRequest.NOISE_REDUCTION_MODE_HIGH_QUALITY)
                count++
            }
            if (edge.contains(CaptureRequest.EDGE_MODE_HIGH_QUALITY)) {
                options.setCaptureRequestOption(CaptureRequest.EDGE_MODE, CaptureRequest.EDGE_MODE_HIGH_QUALITY)
                count++
            }
            if (count > 0) Camera2CameraControl.from(camera.cameraControl).addCaptureRequestOptions(options.build()).get(5, java.util.concurrent.TimeUnit.SECONDS)
            Log.i("SantetHPCamera", "Applied $count supported hardware quality modes to active camera")
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.w("SantetHPCamera", "Could not enable hardware quality modes", error)
        }
    }
    private fun switchCamera(facing: String) {
        scope.launch {
            try {
                val track = cameraTrack
                if (track == null) {
                    startCamera(null, facing)
                } else {
                    track.switchCamera(position = if (facing == "front") CameraPosition.FRONT else CameraPosition.BACK)
                    val side = if (facing == "front") "depan" else "belakang"
                    updateNotification("Kamera " + side + " aktif")
                }
            } catch (error: Exception) {
                updateNotification("Gagal mengganti kamera: " + (error.localizedMessage ?: "kesalahan"))
            }
        }
    }

    private fun stopCamera(message: String) {
        stopCameraSettings()
        cameraStopJob?.cancel()
        cameraStopJob = null
        scope.launch {
            val participant = room?.localParticipant
            try { participant?.setMicrophoneEnabled(false) } catch (_: Exception) { }
            val track = cameraTrack
            cameraTrack = null
            if (track != null) {
                try { participant?.unpublishTrack(track) } catch (_: Exception) { }
                try { track.dispose() } catch (_: Exception) { }
            } else {
                try { participant?.setCameraEnabled(false) } catch (_: Exception) { }
            }
            updateNotification(message + " • suara dan kamera berhenti, layanan tetap siaga")
        }
    }

    private fun disarm() {
        reconnectJob?.cancel()
        reconnectJob = null
        roomEventsJob?.cancel()
        roomEventsJob = null
        pendingCameraRequestId = null
        cameraStopJob?.cancel()
        cameraStopJob = null
        stopCameraSettings()
        getSharedPreferences("parent_audio", MODE_PRIVATE).edit()
            .putBoolean("remote_camera_armed", false).apply()
        scope.launch {
            val participant = room?.localParticipant
            try { participant?.setMicrophoneEnabled(false) } catch (_: Exception) { }
            val track = cameraTrack
            cameraTrack = null
            if (track != null) {
                try { participant?.unpublishTrack(track) } catch (_: Exception) { }
                try { track.dispose() } catch (_: Exception) { }
            } else {
                try { participant?.setCameraEnabled(false) } catch (_: Exception) { }
            }
            try { room?.disconnect() } catch (_: Exception) { }
            try { room?.release() } catch (_: Exception) { }
            room = null
            cameraTrack = null
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun isArmed(): Boolean =
        getSharedPreferences("parent_audio", MODE_PRIVATE).getBoolean("remote_camera_armed", false)

    private fun requestCameraToken(idToken: String, code: String, preferredServerId: String?): JSONObject {
        val connection = URL(CAMERA_URL).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use {
                it.write(JSONObject().put("idToken", idToken).put("code", code)
                    .put("preferredServerId", preferredServerId ?: "")
                    .toString().toByteArray(Charsets.UTF_8))
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val response = JSONObject(stream?.bufferedReader()?.use { it.readText() } ?: "{}")
            if (status !in 200..299) throw IllegalStateException(response.optString("error", "Server menolak akses"))
            return response
        } finally {
            connection.disconnect()
        }
    }

    private fun notification(message: String): Notification {
        val openApp = PendingIntent.getActivity(
            this, 43, Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val disarmIntent = PendingIntent.getService(
            this, 44, Intent(this, RemoteCameraService::class.java).apply {
                action = ACTION_DISARM
            }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val revokeAudioIntent = PendingIntent.getService(
            this, 45, Intent(this, RemoteCameraService::class.java).apply {
                action = ACTION_REVOKE_AUDIO
            }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle("SantetHP • Kamera Jarak Jauh")
            .setContentText(message)
            .setContentIntent(openApp)
            .addAction(android.R.drawable.ic_lock_silent_mode, "Matikan suara", revokeAudioIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Matikan siaga", disarmIntent)
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
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Siaga kamera jarak jauh", NotificationManager.IMPORTANCE_LOW)
                    .apply { description = "Status layanan kamera jarak jauh SantetHP" }
            )
        }
    }

    override fun onDestroy() {
        cameraStopJob?.cancel()
        stopCameraSettings()
        reconnectJob?.cancel()
        roomEventsJob?.cancel()
        scope.cancel()
        val activeRoom = room
        room = null
        cameraTrack = null
        if (activeRoom != null) {
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                try { activeRoom.localParticipant.setMicrophoneEnabled(false) } catch (_: Exception) { }
                try { activeRoom.localParticipant.setCameraEnabled(false) } catch (_: Exception) { }
                try { activeRoom.disconnect() } finally { activeRoom.release() }
            }
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

/**
 * Per-frame adaptive tone correction for the outgoing LiveKit camera track.
 *
 * Samples the centre of each frame and gently adjusts luma with a smoothed
 * gamma curve. This is intentionally a video-frame processor: it reuses the
 * camera session already owned by LiveKit and never opens a second CameraX
 * session. Chroma planes are shared read-only; only the luma plane is copied.
 */
private data class CameraLumaStats(val median: Int, val percentile90: Int)

/** Serializes bounded exposure requests; CameraX cancels overlapping requests. */
private class CameraExposureController(private val camera: Camera, context: Context) {
    private val updates = Channel<CameraLumaStats>(Channel.CONFLATED)
    private val inFlight = AtomicBoolean(false)
    private val mainExecutor = ContextCompat.getMainExecutor(context)
    private var lastRequestAtMs = 0L
    private var lastRequestedIndex = Int.MIN_VALUE
    fun submit(median: Int, percentile90: Int) { updates.trySend(CameraLumaStats(median, percentile90)) }
    suspend fun run() {
        for (firstStats in updates) {
            delay(1800L)
            var stats = firstStats
            while (true) { val newest = updates.tryReceive().getOrNull() ?: break; stats = newest }
            requestExposure(stats)
        }
    }
    private fun requestExposure(stats: CameraLumaStats) {
        val state = camera.cameraInfo.exposureState
        if (!state.isExposureCompensationSupported) return
        val range = state.exposureCompensationRange
        val step = state.exposureCompensationStep.toFloat()
        if (step <= 0f || range.lower == range.upper) return
        // Give genuinely dark scenes a little more sensor exposure, while
        // allowing a few bright lamps/windows without forcing the whole image dark.
        val desiredEv = when {
            stats.median < 45 && stats.percentile90 < 175 -> 1.0f
            stats.median < 62 && stats.percentile90 < 195 -> 0.8f
            stats.median < 82 && stats.percentile90 < 215 -> 0.55f
            stats.median < 100 && stats.percentile90 < 225 -> 0.3f
            stats.median > 155 && stats.percentile90 > 232 -> 0f
            else -> 0.15f
        }
        val maxPositive = minOf(range.upper, (1.0f / step).toInt())
        val target = (desiredEv / step).roundToInt().coerceIn(range.lower, maxPositive)
        val current = state.exposureCompensationIndex
        if (target == current || target == lastRequestedIndex) return
        if (kotlin.math.abs((target - current) * step) < 0.25f) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastRequestAtMs < 1800L || !inFlight.compareAndSet(false, true)) return
        lastRequestAtMs = now
        try {
            val future = camera.cameraControl.setExposureCompensationIndex(target)
            future.addListener({
                try {
                    future.get()
                    lastRequestedIndex = target
                    Log.d("SantetHPCamera", "Exposure compensation applied: " + (target * step) + " EV")
                } catch (error: Exception) {
                    Log.d("SantetHPCamera", "Exposure compensation unavailable for this camera session", error)
                } finally { inFlight.set(false) }
            }, mainExecutor)
        } catch (error: Exception) {
            inFlight.set(false)
            Log.d("SantetHPCamera", "Exposure compensation request rejected", error)
        }
    }
}

/** Reusable centre histogram, restrained shadow lift, and natural colour recovery. */
private class AdaptiveBrightnessVideoProcessor : NoDropVideoProcessor() {
    @Volatile private var outputSink: VideoSink? = null
    @Volatile private var exposureFeedback: ((Int, Int) -> Unit)? = null
    private var currentGamma = 1.0f
    private var currentSaturation = 1.10f
    private val lookup = IntArray(256)
    private val histogram = IntArray(256)

    fun setExposureFeedback(listener: ((Int, Int) -> Unit)?) { exposureFeedback = listener }
    override fun onCapturerStarted(success: Boolean) = Unit
    override fun onCapturerStopped() = Unit
    override fun setSink(sink: VideoSink?) { outputSink = sink }

    override fun onFrameCaptured(frame: VideoFrame) {
        val sink = outputSink ?: return
        val source = try { frame.buffer.toI420() } catch (_: Exception) { null }
        if (source == null) { sink.onFrame(frame); return }
        var ownershipTransferred = false
        try {
            val width = source.width
            val height = source.height
            if (width <= 0 || height <= 0) {
                source.release()
                sink.onFrame(frame)
                return
            }

            val yPlane = source.dataY
            val stride = source.strideY
            java.util.Arrays.fill(histogram, 0)
            val left = width / 6
            val right = width - left
            val top = height / 6
            val bottom = height - top
            var samples = 0
            var y = top
            while (y < bottom) {
                var x = left
                while (x < right) {
                    val value = (yPlane.get(y * stride + x).toInt() and 0xff).coerceIn(0, 255)
                    histogram[value]++
                    samples++
                    x += 12
                }
                y += 12
            }

            var median = 116
            var p90 = 180
            if (samples > 0) {
                val medianTarget = (samples * 50 + 99) / 100
                val p90Target = (samples * 90 + 99) / 100
                var cumulative = 0
                var medianFound = false
                for (value in 0..255) {
                    cumulative += histogram[value]
                    if (!medianFound && cumulative >= medianTarget) {
                        median = value
                        medianFound = true
                    }
                    if (cumulative >= p90Target) {
                        p90 = value
                        break
                    }
                }
                exposureFeedback?.invoke(median, p90)

                // A slightly brighter mid-tone target helps avoid the dim,
                // flat appearance of the previous global gamma correction.
                val normalized = ((median - 16f) / 219f).coerceIn(0.025f, 0.97f)
                val targetLuma = (120f - 16f) / 219f
                val minimumGamma = when {
                    p90 >= 220 -> 0.70f
                    p90 >= 195 -> 0.60f
                    else -> 0.45f
                }
                val gamma = (ln(targetLuma.toDouble()) / ln(normalized.toDouble()))
                    .toFloat().coerceIn(minimumGamma, 1.35f)
                currentGamma += (gamma - currentGamma) * 0.07f

                // Recover a little colour after shadow lifting, but reduce the
                // boost in very dark/noisy scenes and around clipped highlights.
                val targetSaturation = when {
                    median < 55 -> 1.06f
                    p90 > 228 -> 1.08f
                    else -> 1.13f
                }
                currentSaturation += (targetSaturation - currentSaturation) * 0.06f
            }

            // Keep the existing smooth luma adjustment and preserve the video range.
            for (value in 0..255) {
                val normalized = ((value - 16f) / 219f).coerceIn(0f, 1f)
                lookup[value] = if (value <= 16) 16 else
                    (16f + normalized.pow(currentGamma) * 219f).roundToInt().coerceIn(16, 235)
            }
            for (row in 0 until height) {
                val offset = row * stride
                for (column in 0 until width) {
                    val index = offset + column
                    val value = yPlane.get(index).toInt() and 0xff
                    yPlane.put(index, lookup[value].toByte())
                }
            }

            // Gently increase chroma around neutral grey. This restores colour
            // richness without shifting hue or applying a blanket colour tint.
            val chromaWidth = (width + 1) / 2
            val chromaHeight = (height + 1) / 2
            val uPlane = source.dataU
            val vPlane = source.dataV
            val uStride = source.strideU
            val vStride = source.strideV
            for (row in 0 until chromaHeight) {
                val uOffset = row * uStride
                val vOffset = row * vStride
                for (column in 0 until chromaWidth) {
                    val uIndex = uOffset + column
                    val vIndex = vOffset + column
                    val u = uPlane.get(uIndex).toInt() and 0xff
                    val v = vPlane.get(vIndex).toInt() and 0xff
                    val adjustedU = (128f + (u - 128f) * currentSaturation).roundToInt().coerceIn(0, 255)
                    val adjustedV = (128f + (v - 128f) * currentSaturation).roundToInt().coerceIn(0, 255)
                    uPlane.put(uIndex, adjustedU.toByte())
                    vPlane.put(vIndex, adjustedV.toByte())
                }
            }

            val processed = VideoFrame(source, frame.rotation, frame.timestampNs)
            ownershipTransferred = true
            try {
                sink.onFrame(processed)
            } finally {
                processed.release()
            }
        } catch (error: Exception) {
            if (!ownershipTransferred) runCatching { source.release() }
            Log.w("SantetHPCamera", "Adaptive image processing skipped for a frame", error)
            sink.onFrame(frame)
        }
    }
}
private suspend fun <T> Task<T>.await(): T =
    suspendCancellableCoroutine { continuation ->
        addOnCompleteListener { task ->
            if (!continuation.isActive) return@addOnCompleteListener
            if (task.isSuccessful) continuation.resume(task.result)
            else continuation.resumeWithException(task.exception ?: IllegalStateException("Operasi Firebase gagal"))
        }
    }
