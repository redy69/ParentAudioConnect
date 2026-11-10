package com.santethp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import io.livekit.android.LiveKit
import io.livekit.android.room.Room
import io.livekit.android.room.track.CameraPosition
import io.livekit.android.room.track.LocalVideoTrack
import io.livekit.android.room.track.Track
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
        const val ACTION_DISARM = "com.santethp.camera.DISARM"
        const val EXTRA_REQUEST_ID = "request_id"
        private const val CHANNEL_ID = "remote_camera"
        private const val NOTIFICATION_ID = 4301
        private const val CAMERA_URL = "https://parent-audio-token.yudhis-r15.workers.dev/camera-token"
        private const val MAX_CAMERA_SESSION_MS = 10 * 60 * 1000L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var room: Room? = null
    private var cameraTrack: LocalVideoTrack? = null
    private var cameraStopJob: Job? = null
    @Volatile private var connecting = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_ARM -> armFromVisibleApp()
            ACTION_START_CAMERA -> {
                if (isArmed() && room != null) {
                    startCamera(intent.getStringExtra(EXTRA_REQUEST_ID), intent.getStringExtra("facing") ?: "back")
                } else {
                    updateNotification("Siaga kamera tidak aktif atau koneksi terputus. Buka aplikasi anak.")
                }
            }
            ACTION_STOP_CAMERA -> stopCamera("Kamera dihentikan")
            ACTION_SWITCH_CAMERA -> switchCamera(intent.getStringExtra("facing") ?: "back")
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
        if (room == null && !connecting) {
            connecting = true
            scope.launch { connectCameraRoom() }
        }
    }

    private fun startCameraForeground() {
        val notification = notification("Siaga kamera aktif • kamera belum menyala")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA)
        } else {
            startForeground(NOTIFICATION_ID, notification)
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
            val response = withContext(Dispatchers.IO) { requestCameraToken(idToken, code) }
            if (response.optString("role") != "child") {
                throw IllegalStateException("Server tidak memberikan akses kamera anak")
            }
            val newRoom = LiveKit.create(applicationContext)
            newRoom.connect(url = response.getString("serverUrl"), token = response.getString("token"))
            newRoom.localParticipant.setMicrophoneEnabled(false)
            room = newRoom
            updateNotification("Siaga kamera aktif • menunggu permintaan orang tua")
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            updateNotification("Kamera belum siap: ${error.localizedMessage ?: "koneksi gagal"}")
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
                activeRoom.localParticipant.videoTrackCaptureDefaults =
                    activeRoom.localParticipant.videoTrackCaptureDefaults.copy(
                        position = if (facing == "front") CameraPosition.FRONT else CameraPosition.BACK
                    )
                val enabled = activeRoom.localParticipant.setCameraEnabled(true)
                if (!enabled) throw IllegalStateException("Android tidak dapat mengaktifkan kamera")
                cameraTrack = activeRoom.localParticipant.getTrackPublication(Track.Source.CAMERA)?.track as? LocalVideoTrack
                cameraTrack?.switchCamera(position = if (facing == "front") CameraPosition.FRONT else CameraPosition.BACK)
                updateNotification("KAMERA AKTIF • tayangan dibagikan ke orang tua")
                delay(MAX_CAMERA_SESSION_MS)
                stopCamera("Sesi kamera berakhir otomatis setelah 10 menit")
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                updateNotification("Kamera gagal aktif: ${error.localizedMessage ?: "kesalahan"}")
            }
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
        cameraStopJob?.cancel()
        cameraStopJob = null
        scope.launch {
            try { room?.localParticipant?.setCameraEnabled(false) } catch (_: Exception) { }
            cameraTrack = null
            updateNotification(message + " • layanan tetap siaga")
        }
    }

    private fun disarm() {
        cameraStopJob?.cancel()
        cameraStopJob = null
        getSharedPreferences("parent_audio", MODE_PRIVATE).edit()
            .putBoolean("remote_camera_armed", false).apply()
        scope.launch {
            try { room?.localParticipant?.setCameraEnabled(false) } catch (_: Exception) { }
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

    private fun requestCameraToken(idToken: String, code: String): JSONObject {
        val connection = URL(CAMERA_URL).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use {
                it.write(JSONObject().put("idToken", idToken).put("code", code)
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
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle("SantetHP • Kamera Jarak Jauh")
            .setContentText(message)
            .setContentIntent(openApp)
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
        scope.cancel()
        val activeRoom = room
        room = null
        cameraTrack = null
        if (activeRoom != null) {
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                try { activeRoom.localParticipant.setCameraEnabled(false) } catch (_: Exception) { }
                try { activeRoom.disconnect() } finally { activeRoom.release() }
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
            if (task.isSuccessful) continuation.resume(task.result)
            else continuation.resumeWithException(task.exception ?: IllegalStateException("Operasi Firebase gagal"))
        }
    }
