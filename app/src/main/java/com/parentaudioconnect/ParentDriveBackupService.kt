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
        const val EXTRA_CHILD_NAME = "child_name"
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
    private val networkGateRunnable = object : Runnable {
        override fun run() {
            if (stopping) return
            if (canUseBackupNetwork()) {
                protocol?.requestNextBackup()
            } else {
                updateStatus("Backup dijeda • HP orang tua menunggu koneksi Wi-Fi")
            }
            reconnectHandler.postDelayed(this, 15000L)
        }
    }

    private fun canUseBackupNetwork(): Boolean {
        val wifiOnly = getSharedPreferences("parent_audio", MODE_PRIVATE)
            .getBoolean("drive_backup_wifi_only", true)
        return BackupNetworkPolicy.canUseBackupNetwork(this, wifiOnly)
    }
    @Volatile private var stopping = false
    private var pairingCode: String? = null
    private var childDisplayName: String = "HP Anak"
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
            stopTransport(clearSavedState = true)
            stopSelf()
            return START_NOT_STICKY
        }
        val code = intent?.getStringExtra(EXTRA_PAIR_CODE)
        val token = intent?.getStringExtra(EXTRA_ACCESS_TOKEN)
        val displayName = intent?.getStringExtra(EXTRA_CHILD_NAME)?.trim().orEmpty()
        if (code.isNullOrBlank() || token.isNullOrBlank()) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (accessToken != token) folderId = null
        accessToken = token
        if (displayName.isNotBlank()) childDisplayName = displayName
        if (manager != null) {
            updateStatus("Izin Google Drive diperbarui • melanjutkan antrean backup")
            protocol?.requestNextBackup()
            return START_STICKY
        }
        getSharedPreferences("parent_audio", MODE_PRIVATE).edit()
            .putBoolean("parent_drive_backup_service_active", true)
            .putString("parent_drive_backup_pair_code", code)
            .putString("parent_drive_backup_child_name", childDisplayName)
            .apply()
        startAsForeground("Menunggu koneksi HP anak untuk backup media baru")
        stopping = false
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
                val wifiOnly = snapshot?.getBoolean("driveBackupWifiOnly") ?: true
                getSharedPreferences("parent_audio", MODE_PRIVATE).edit()
                    .putBoolean("drive_backup_wifi_only", wifiOnly).apply()
                if (snapshot?.getBoolean("driveBackupEnabled") != true) {
                    updateStatus("Persetujuan backup dari HP anak tidak aktif")
                    stopTransport()
                    stopSelf()
                } else if (canUseBackupNetwork()) {
                    protocol?.requestNextBackup()
                }
            }
    }

    private fun startTransport(code: String) {
        stopping = false
        reconnectHandler.removeCallbacks(networkGateRunnable)
        reconnectHandler.post(networkGateRunnable)
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
            canReceiveBackup = { !accessToken.isNullOrBlank() && canUseBackupNetwork() },
            onBackupFileReady = { key, file, name, mime, complete ->
                val token = accessToken
                if (!canUseBackupNetwork()) {
                    file.delete()
                    updateStatus("Backup dijeda • menunggu Wi-Fi sebelum upload")
                    complete(false)
                } else if (token.isNullOrBlank()) {
                    file.delete()
                    complete(false)
                } else {
                    Thread {
                        try {
                            val uploader = GoogleDriveUploader(token)
                            val rootFolder = folderId ?: uploader.ensureBackupFolder().also { folderId = it }
                            val childKey = java.security.MessageDigest.getInstance("SHA-256")
                                .digest((pairingCode ?: "unknown-child").toByteArray(Charsets.UTF_8))
                                .joinToString("") { "%02x".format(it.toInt() and 0xff) }
                            val backupPrefs = getSharedPreferences("parent_audio", MODE_PRIVATE)
                            val customFolderName = backupPrefs.getString("parent_drive_child_folder_name_$childKey", null)
                            val childFolder = uploader.ensureChildFolder(
                                childKey, customFolderName?.takeIf { it.isNotBlank() } ?: childDisplayName, rootFolder
                            )
                            backupPrefs.edit().putString("parent_drive_child_folder_id_$childKey", childFolder).apply()
                            if (backupPrefs.getBoolean("parent_drive_child_folder_rename_pending_$childKey", false)) {
                                uploader.renameFolder(
                                    childFolder,
                                    customFolderName?.takeIf { it.isNotBlank() } ?: childDisplayName
                                )
                                backupPrefs.edit().putBoolean("parent_drive_child_folder_rename_pending_$childKey", false).apply()
                            }
                            val mediaType = if (mime.startsWith("video/")) "video" else "photo"
                            val mediaFolder = uploader.ensureMediaFolder(childKey, mediaType, childFolder)
                            uploader.uploadFile(
                                file, name, mime, mediaFolder, stableKey = key,
                                progress = GoogleDriveUploader.ProgressListener { sent, total ->
                                    updateStatus(
                                        "Mengunggah ke Google Drive: " +
                                            android.text.format.Formatter.formatFileSize(this, sent) +
                                            " / " + android.text.format.Formatter.formatFileSize(this, total)
                                    )
                                }
                            )
                            file.delete()
                            val stats = getSharedPreferences("parent_audio", MODE_PRIVATE)
                            val successCount = stats.getInt("parent_drive_backup_success_count", 0) + 1
                            stats.edit()
                                .putInt("parent_drive_backup_success_count", successCount)
                                .putString("parent_drive_backup_last_result", "Berhasil: $name")
                                .putLong("parent_drive_backup_last_result_at", System.currentTimeMillis())
                                .apply()
                            updateStatus("Backup berhasil: $name • total $successCount file")
                            complete(true)
                        } catch (error: Exception) {
                            file.delete()
                            Log.e(TAG, "Upload Drive gagal", error)
                            val authorizationFailure = error.message.orEmpty().contains("401") ||
                                error.message.orEmpty().contains("403")
                            if (authorizationFailure) {
                                getSharedPreferences("parent_audio", MODE_PRIVATE).edit()
                                    .putBoolean("parent_drive_backup_reauth_required", true).apply()
                            }
                            val stats = getSharedPreferences("parent_audio", MODE_PRIVATE)
                            val failedCount = stats.getInt("parent_drive_backup_failed_count", 0) + 1
                            stats.edit()
                                .putInt("parent_drive_backup_failed_count", failedCount)
                                .putString("parent_drive_backup_last_result", "Gagal: $name")
                                .putLong("parent_drive_backup_last_result_at", System.currentTimeMillis())
                                .apply()
                            updateStatus(
                                if (authorizationFailure) {
                                    "Izin Google Drive kedaluwarsa/ditolak • buka SantetHP untuk otorisasi ulang"
                                } else {
                                    "Upload gagal: $name • file tetap di antrean • total gagal $failedCount"
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
        reconnectHandler.removeCallbacks(networkGateRunnable)
        protocol?.close()
        protocol = null
        manager?.close()
        manager = null
    }

    private fun stopTransport(clearSavedState: Boolean = true) {
        stopP2pTransport()
        consentListener?.remove()
        consentListener = null
        accessToken = null
        folderId = null
        pairingCode = null
        childDisplayName = "HP Anak"
        if (clearSavedState) {
            getSharedPreferences("parent_audio", MODE_PRIVATE).edit()
                .putBoolean("parent_drive_backup_service_active", false)
                .remove("parent_drive_backup_pair_code")
                .remove("parent_drive_backup_authorized_at")
                .remove("parent_drive_backup_reauth_required")
                .apply()
        }
    }

    override fun onDestroy() {
        // Preserve the user's requested backup state on an OS/process stop so
        // MainActivity can obtain a fresh short-lived token when reopened.
        stopTransport(clearSavedState = false)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
