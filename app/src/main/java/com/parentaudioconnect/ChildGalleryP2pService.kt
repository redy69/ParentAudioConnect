package com.santethp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat

/**
 * Keeps the child's explicitly consented media P2P transport alive while the
 * Activity is not visible. Gallery browsing and Drive backup use separate
 * consent flags; starting this service never grants gallery-browsing access.
 */
class ChildGalleryP2pService : Service() {
    companion object {
        const val ACTION_START = "com.santethp.action.START_GALLERY_P2P"
        const val ACTION_STOP = "com.santethp.action.STOP_GALLERY_P2P"
        private const val TAG = "SantetHP-GalleryService"
        private const val CHANNEL_ID = "gallery_sharing"
        private const val NOTIFICATION_ID = 4307
    }

    private var manager: GalleryP2pManager? = null
    private var protocol: GalleryP2pGalleryProtocol? = null
    private var parentReceiveProtocol: ParentToChildMediaProtocol? = null

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

        val prefs = getSharedPreferences("parent_audio", MODE_PRIVATE)
        val galleryConsent = prefs.getBoolean("child_gallery_sharing_enabled", false) &&
            prefs.getBoolean("child_gallery_server_consent_confirmed", false)
        val backupConsent = prefs.getBoolean("child_drive_backup_consent", false)
        val parentReceiveConsent = prefs.getBoolean("child_parent_media_receive_consent", false) &&
            prefs.getBoolean("child_parent_media_server_confirmed", false)
        val code = prefs.getString("pair_code", null)
        if ((!galleryConsent && !backupConsent && !parentReceiveConsent) || code.isNullOrBlank()) {
            Log.i(TAG, "Service tidak dimulai: persetujuan, izin media, atau pairing belum tersedia")
            stopTransport()
            stopSelf()
            return START_NOT_STICKY
        }

        startAsForeground()
        if (manager == null) startTransport(code)
        return START_STICKY
    }

    private fun startAsForeground() {
        val prefs = getSharedPreferences("parent_audio", MODE_PRIVATE)
        val galleryEnabled = prefs.getBoolean("child_gallery_sharing_enabled", false) &&
            prefs.getBoolean("child_gallery_server_consent_confirmed", false)
        val message = if (galleryEnabled) {
            "Menjaga koneksi galeri P2P ke HP orang tua"
        } else {
            "Menjaga koneksi backup media baru yang telah disetujui"
        }
        val notification = buildNotification(message)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(message: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_gallery)
            .setContentTitle("SantetHP • Layanan media aktif")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                "$message. Berbagi hanya berlangsung selama persetujuan anak dan izin media tetap aktif."
            ))
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Berbagi galeri SantetHP",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Menampilkan status koneksi galeri P2P yang diaktifkan pengguna HP anak"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun startTransport(pairingCode: String) {
        lateinit var activeProtocol: GalleryP2pGalleryProtocol
        val activeManager = GalleryP2pManager(
            context = applicationContext,
            pairingCode = pairingCode,
            localRole = GalleryP2pManager.Role.CHILD,
            onState = { message ->
                Log.i(TAG, message)
                updateNotification(message)
            },
            onData = { bytes, binary ->
                if (parentReceiveProtocol?.onFrame(bytes, binary) != true) activeProtocol.onFrame(bytes, binary)
            }
        )
        parentReceiveProtocol = ParentToChildMediaProtocol(
            context = applicationContext,
            role = GalleryP2pManager.Role.CHILD,
            sendFrame = { bytes, binary -> activeManager.send(bytes, binary) },
            onStatus = { message ->
                Log.i(TAG, message)
                updateNotification(message)
            }
        )
        activeProtocol = GalleryP2pGalleryProtocol(
            context = applicationContext,
            role = GalleryP2pManager.Role.CHILD,
            sendFrame = { bytes, binary -> activeManager.send(bytes, binary) },
            onMediaIndex = { },
            onThumbnail = { _, _ -> },
            onStatus = { message ->
                Log.i(TAG, message)
                updateNotification(message)
            }
        )
        manager = activeManager
        protocol = activeProtocol
        activeManager.start()
    }

    private fun updateNotification(message: String) {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, buildNotification(message))
    }

    private fun hasGalleryMediaAccess(): Boolean {
        val permissions = if (Build.VERSION.SDK_INT >= 34) {
            listOf(
                android.Manifest.permission.READ_MEDIA_IMAGES,
                android.Manifest.permission.READ_MEDIA_VIDEO,
                android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
            )
        } else if (Build.VERSION.SDK_INT >= 33) {
            listOf(
                android.Manifest.permission.READ_MEDIA_IMAGES,
                android.Manifest.permission.READ_MEDIA_VIDEO
            )
        } else {
            listOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        return permissions.any {
            checkSelfPermission(it) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }

    private fun stopTransport() {
        parentReceiveProtocol?.close()
        parentReceiveProtocol = null
        protocol?.close()
        protocol = null
        manager?.close()
        manager = null
    }

    override fun onDestroy() {
        stopTransport()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
