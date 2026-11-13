package com.santethp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

/** Parent-side foreground transport for the child's separately consented Drive backup queue. */
class ParentDriveBackupService : Service() {
    companion object {
        const val ACTION_START = "com.santethp.action.START_PARENT_DRIVE_BACKUP"
        const val ACTION_STOP = "com.santethp.action.STOP_PARENT_DRIVE_BACKUP"
        const val EXTRA_PAIR_CODE = "pair_code"
        const val EXTRA_ACCESS_TOKEN = "drive_access_token"
        private const val TAG = "SantetHP-ParentBackup"
        private const val CHANNEL_ID = "parent_drive_backup"
        private const val NOTIFICATION_ID = 4310
    }

    private var manager: GalleryP2pManager? = null
    private var protocol: GalleryP2pGalleryProtocol? = null
    private var consentListener: ListenerRegistration? = null
    private var folderId: String? = null
    private var accessToken: String? = null
    private val reconnectHandler = Handler(Looper.getMainLooper())
    @Volatile private var stopping = false
    private var pairingCode: String? = null
    private val reconnectRunnable = Runnable {
        val code = pairingCode
        if (!stopping && !code.isNullOrBlank() &&
            getSharedPreferences("parent_audio", MODE_PRIVATE).getBoolean("parent_drive_backup_service_active", false)
        ) {
            stopP2pTransport()
            startTransport(code)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopTransport()
            stopSelf()
            return START_NOT_STICKY
        }
        val code = intent?.getStringExtra(EXTRA_PAIR_CODE)
        val token = intent?.getStringExtra(EXTRA_ACCESS_TOKEN)
        if (code.isNullOrBlank() || token.isNullOrBlank()) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (accessToken != token) folderId = null
        accessToken = token
        if (manager != null) {
            updateStatus("Izin Google Drive diperbarui • melanjutkan antrean backup")
            protocol?.requestNextBackup()
            return START_STICKY
        }
        getSharedPreferences("parent_audio", MODE_PRIVATE).edit()
            .putBoolean("parent_drive_backup_service_active", true)
            .putString("parent_drive_backup_pair_code", code)
            .apply()
        startAsForeground("Menunggu koneksi HP anak untuk backup media baru")
        pairingCode = code
        listenForConsent(code)
        startTransport(code)
        return START_STICKY
    }

    private fun startAsForeground(message: String) {
        val notification = buildNotification(message)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(message: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_upload)
            .setContentTitle("SantetHP • Backup Google Drive")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                "$message. Hanya media baru yang telah disetujui dari HP anak yang diproses."
            ))
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    private fun updateStatus(message: String) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(message))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Backup Google Drive SantetHP", NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Status backup foto dan video baru dari perangkat anak yang telah disetujui"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun listenForConsent(code: String) {
        consentListener?.remove()
        consentListener = FirebaseFirestore.getInstance().collection("pairings").document(code)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Tidak dapat memeriksa status persetujuan backup", error)
                    return@addSnapshotListener
                }
                if (snapshot?.getBoolean("driveBackupEnabled") != true) {
                    updateStatus("Persetujuan backup dari HP anak tidak aktif")
                    stopTransport()
                    stopSelf()
                }
            }
    }

    private fun startTransport(code: String) {
        stopping = false
        lateinit var activeProtocol: GalleryP2pGalleryProtocol
        val activeManager = GalleryP2pManager(
            context = applicationContext,
            pairingCode = code,
            localRole = GalleryP2pManager.Role.PARENT,
            onState = { message ->
                Log.i(TAG, message)
                updateStatus(message)
                if (message.contains("P2P TERHUBUNG")) {
                    reconnectHandler.removeCallbacks(reconnectRunnable)
                    activeProtocol.requestNextBackup()
                } else if (!stopping &&
                    (message.contains("terputus") || message.contains("gagal") || message.contains("ditutup"))
                ) {
                    reconnectHandler.removeCallbacks(reconnectRunnable)
                    reconnectHandler.postDelayed(reconnectRunnable, 30000L)
                }
            },
            onData = { bytes, binary -> activeProtocol.onFrame(bytes, binary) }
        )
        activeProtocol = GalleryP2pGalleryProtocol(
            context = applicationContext,
            role = GalleryP2pManager.Role.PARENT,
            sendFrame = { bytes, binary -> activeManager.send(bytes, binary) },
            onMediaIndex = { },
            onThumbnail = { _, _ -> },
            onStatus = ::updateStatus,
            canReceiveBackup = { !accessToken.isNullOrBlank() },
            onBackupFileReady = { key, file, name, mime, complete ->
                val token = accessToken
                if (token.isNullOrBlank()) {
                    file.delete()
                    complete(false)
                } else {
                    Thread {
                        try {
                            val uploader = GoogleDriveUploader(token)
                            val parentFolder = folderId ?: uploader.ensureBackupFolder().also { folderId = it }
                            uploader.uploadFile(
                                file, name, mime, parentFolder, stableKey = key,
                                progress = GoogleDriveUploader.ProgressListener { sent, total ->
                                    updateStatus(
                                        "Mengunggah ke Google Drive: " +
                                            android.text.format.Formatter.formatFileSize(this, sent) +
                                            " / " + android.text.format.Formatter.formatFileSize(this, total)
                                    )
                                }
                            )
                            file.delete()
                            updateStatus("Backup Google Drive berhasil: $name")
                            complete(true)
                        } catch (error: Exception) {
                            file.delete()
                            Log.e(TAG, "Upload Drive gagal", error)
                            val authorizationFailure = error.message.orEmpty().contains("401") ||
                                error.message.orEmpty().contains("403")
                            updateStatus(
                                if (authorizationFailure) {
                                    "Izin Google Drive kedaluwarsa/ditolak • buka SantetHP untuk otorisasi ulang"
                                } else {
                                    "Upload Drive gagal • file tetap dalam antrean; percobaan ulang dijadwalkan"
                                }
                            )
                            complete(false)
                            if (!authorizationFailure &&
                                (error is java.io.IOException || error.message.orEmpty().contains("(5"))
                            ) {
                                reconnectHandler.postDelayed({ protocol?.requestNextBackup() }, 30000L)
                            }
                        }
                    }.start()
                }
            }
        )
        protocol = activeProtocol
        manager = activeManager
        activeManager.start()
    }

    private fun stopP2pTransport() {
        stopping = true
        reconnectHandler.removeCallbacks(reconnectRunnable)
        protocol?.close()
        protocol = null
        manager?.close()
        manager = null
    }

    private fun stopTransport() {
        stopP2pTransport()
        consentListener?.remove()
        consentListener = null
        accessToken = null
        folderId = null
        pairingCode = null
        getSharedPreferences("parent_audio", MODE_PRIVATE).edit()
            .putBoolean("parent_drive_backup_service_active", false)
            .remove("parent_drive_backup_pair_code")
            .remove("parent_drive_backup_authorized_at")
            .apply()
    }

    override fun onDestroy() {
        stopTransport()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
