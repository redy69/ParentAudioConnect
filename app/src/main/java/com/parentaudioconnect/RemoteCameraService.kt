package com.santethp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
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
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

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

                val track = participant.createVideoTrack(
                    options = captureOptions,
                    videoProcessor = NaturalCameraVideoProcessor()
                )
                cameraTrack = track
                try {
                    track.startCapture()
                    // Observe LiveKit's existing CameraX session and reapply supported
                    // hardware processing/exposure after every camera switch.
                    observeCameraSessions(track)
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

    /** Follows LiveKit's CameraX session and applies supported sensor noise reduction. */
    private fun observeCameraSessions(track: LocalVideoTrack) {
        cameraSettingsJob?.cancel()
        val cameraFlow = track.capturer.getCameraX() ?: return
        cameraSettingsJob = scope.launch {
            cameraFlow.filterNotNull().distinctUntilChanged().collectLatest { camera ->
                try {
                    applyCameraHardwareQualityModes(camera)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    Log.w("SantetHPCamera", "Camera quality settings could not be maintained", error)
                }
            }
        }
    }

    private fun stopCameraSettings() {
        cameraSettingsJob?.cancel()
        cameraSettingsJob = null
    }

    /**
     * Applies capability-aware camera tuning while keeping sensor auto exposure enabled.
     * The observed comparison is substantially underexposed, so request up to +2 EV,
     * clamped to the camera's advertised compensation range. The torch is never enabled.
     */
    private suspend fun applyCameraHardwareQualityModes(camera: Camera) {
        // Exposure is independent of optional Camera2 noise-reduction support.
        try {
            val exposure = camera.cameraInfo.exposureState
            val range = exposure.exposureCompensationRange
            val step = exposure.exposureCompensationStep
            if (exposure.isExposureCompensationSupported && range.upper > 0 && step.toDouble() > 0.0) {
                // Request up to +2.0 EV (subject to the physical camera's range and step).
                // AE stays ON: the sensor/ISP still chooses shutter and ISO automatically.
                val requestedIndex = kotlin.math.round(2.0 / step.toDouble()).toInt()
                    .coerceIn(range.lower, range.upper)
                if (requestedIndex > 0 && exposure.exposureCompensationIndex != requestedIndex) {
                    camera.cameraControl.setExposureCompensationIndex(requestedIndex)
                        .get(5, java.util.concurrent.TimeUnit.SECONDS)
                }
                Log.i(
                    "SantetHPCamera",
                    "Exposure request: targetEV=+2.0, requestedIndex=$requestedIndex, " +
                        "reportedIndex=" + exposure.exposureCompensationIndex +
                        ", stepEV=" + step.toDouble() + ", range=" + range
                )
            } else {
                Log.i("SantetHPCamera", "Exposure compensation is not supported; keeping device auto exposure")
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.w("SantetHPCamera", "Could not apply mild exposure compensation", error)
        }

        // Tune the existing LiveKit-owned CameraX session through Camera2 interop.
        // Every value is checked against this physical camera's advertised capabilities.
        try {
            val info = Camera2CameraInfo.from(camera.cameraInfo)
            val afModes = info.getCameraCharacteristic(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES)
            val aeModes = info.getCameraCharacteristic(CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES)
            val awbModes = info.getCameraCharacteristic(CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES)
            val antibandingModes = info.getCameraCharacteristic(CameraCharacteristics.CONTROL_AE_AVAILABLE_ANTIBANDING_MODES)
            val oisModes = info.getCameraCharacteristic(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION)
            val eisModes = info.getCameraCharacteristic(CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES)
            val noiseModes = info.getCameraCharacteristic(CameraCharacteristics.NOISE_REDUCTION_AVAILABLE_NOISE_REDUCTION_MODES)
            val edgeModes = info.getCameraCharacteristic(CameraCharacteristics.EDGE_AVAILABLE_EDGE_MODES)
            val fpsRanges = info.getCameraCharacteristic(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
            val aeLockAvailable = info.getCameraCharacteristic(CameraCharacteristics.CONTROL_AE_LOCK_AVAILABLE) == true

            // Prefer a 30 fps ceiling, while allowing the camera to drop toward 15 fps
            // in dim scenes when that range is advertised by the sensor.
            val selectedFpsRange = fpsRanges
                ?.filter { it.upper <= 30 && it.lower <= it.upper }
                ?.maxWithOrNull(compareBy<android.util.Range<Int>> { it.upper }.thenBy { -it.lower })

            val continuousAf = when {
                afModes?.contains(CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO) == true ->
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO
                afModes?.contains(CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE) == true ->
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                afModes?.contains(CaptureRequest.CONTROL_AF_MODE_AUTO) == true ->
                    CaptureRequest.CONTROL_AF_MODE_AUTO
                else -> null
            }

            // Compute selected modes outside the builder so diagnostics can report them.
            val useOis = oisModes?.contains(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON) == true
            val useEis = !useOis &&
                eisModes?.contains(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON) == true
            val noiseMode = when {
                noiseModes?.contains(CaptureRequest.NOISE_REDUCTION_MODE_HIGH_QUALITY) == true ->
                    CaptureRequest.NOISE_REDUCTION_MODE_HIGH_QUALITY
                noiseModes?.contains(CaptureRequest.NOISE_REDUCTION_MODE_FAST) == true ->
                    CaptureRequest.NOISE_REDUCTION_MODE_FAST
                else -> null
            }
            val edgeMode = when {
                edgeModes?.contains(CaptureRequest.EDGE_MODE_HIGH_QUALITY) == true ->
                    CaptureRequest.EDGE_MODE_HIGH_QUALITY
                edgeModes?.contains(CaptureRequest.EDGE_MODE_FAST) == true ->
                    CaptureRequest.EDGE_MODE_FAST
                else -> null
            }

            val options = CaptureRequestOptions.Builder().apply {
                if (continuousAf != null) {
                    setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, continuousAf)
                }
                if (aeModes?.contains(CaptureRequest.CONTROL_AE_MODE_ON) == true) {
                    setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                }
                // Explicitly leave auto exposure unlocked so it can react to scene changes.
                if (aeLockAvailable) {
                    setCaptureRequestOption(CaptureRequest.CONTROL_AE_LOCK, false)
                }
                if (awbModes?.contains(CaptureRequest.CONTROL_AWB_MODE_AUTO) == true) {
                    setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO)
                }
                if (antibandingModes?.contains(CaptureRequest.CONTROL_AE_ANTIBANDING_MODE_AUTO) == true) {
                    setCaptureRequestOption(CaptureRequest.CONTROL_AE_ANTIBANDING_MODE, CaptureRequest.CONTROL_AE_ANTIBANDING_MODE_AUTO)
                }
                if (selectedFpsRange != null) {
                    setCaptureRequestOption(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, selectedFpsRange)
                }

                // Match AirDroid's mutually-exclusive stabilization policy:
                // optical stabilization takes priority; use EIS only when OIS is absent.
                if (useOis) {
                    setCaptureRequestOption(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON)
                    setCaptureRequestOption(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE, CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_OFF)
                } else if (useEis) {
                    setCaptureRequestOption(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE, CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON)
                    setCaptureRequestOption(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_OFF)
                }

                // Prefer the camera's higher-quality ISP modes when supported.
                if (noiseMode != null) {
                    setCaptureRequestOption(CaptureRequest.NOISE_REDUCTION_MODE, noiseMode)
                }
                if (edgeMode != null) {
                    setCaptureRequestOption(CaptureRequest.EDGE_MODE, edgeMode)
                }
            }.build()

            Camera2CameraControl.from(camera.cameraControl)
                .addCaptureRequestOptions(options)
                .get(5, java.util.concurrent.TimeUnit.SECONDS)

            Log.i(
                "SantetHPCamera",
                "Applied camera modes: AF=$continuousAf, AE=auto, AWB=auto, FPS=$selectedFpsRange, " +
                    "OIS=$useOis, EIS=$useEis, NR=$noiseMode, EDGE=$edgeMode, captureFormat=LiveKit"
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.w("SantetHPCamera", "Could not apply supported Camera2 focus/quality modes", error)
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
 * Keeps the camera manufacturer's native ISP image untouched.
 * The sensor pipeline handles exposure, white balance, denoising and colour;
 * this processor only forwards frames into the existing LiveKit track.
 */
private class NaturalCameraVideoProcessor : NoDropVideoProcessor() {
    @Volatile private var outputSink: VideoSink? = null

    override fun onCapturerStarted(success: Boolean) = Unit
    override fun onCapturerStopped() = Unit
    override fun setSink(sink: VideoSink?) { outputSink = sink }

    override fun onFrameCaptured(frame: VideoFrame) {
        outputSink?.onFrame(frame)
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
