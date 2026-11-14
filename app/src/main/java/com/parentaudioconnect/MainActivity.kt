
package com.santethp
import android.content.Intent
import android.content.ContentValues
import android.content.ComponentName
import android.provider.Settings
import android.provider.MediaStore
import android.net.Uri
import android.os.Build
import java.io.File
import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.os.Bundle
import android.graphics.Color
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Path
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.ScrollView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.work.WorkManager

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.Timestamp
import com.google.firebase.messaging.FirebaseMessaging

import io.livekit.android.AudioOptions
import io.livekit.android.AudioType
import io.livekit.android.LiveKit
import io.livekit.android.LiveKitOverrides
import io.livekit.android.room.Room

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import org.json.JSONObject
import org.json.JSONArray

import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom
import java.security.MessageDigest
import java.util.Date

class MainActivity : Activity(), LifecycleOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    private val activityScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Refresh presence labels locally so stale ONLINE states expire even without
    // a new Firestore snapshot arriving while the parent dashboard is open.
    private val childPresenceRefreshRunnable = object : Runnable {
        override fun run() {
            if (currentRole != "parent" || isFinishing) return
            if (::childrenContainer.isInitialized && isChildrenPanelExpanded) {
                renderChildrenList()
            }
            if (::selectedChildButton.isInitialized) updateSelectedChildButton()
            if (::rootLayout.isInitialized) rootLayout.postDelayed(this, 30_000L)
        }
    }

    private fun startChildPresenceRefresh() {
        if (currentRole != "parent" || !::rootLayout.isInitialized) return
        rootLayout.removeCallbacks(childPresenceRefreshRunnable)
        rootLayout.postDelayed(childPresenceRefreshRunnable, 30_000L)
    }

    private fun stopChildPresenceRefresh() {
        if (::rootLayout.isInitialized) rootLayout.removeCallbacks(childPresenceRefreshRunnable)
    }

    private lateinit var auth: FirebaseAuth
    private val db = FirebaseFirestore.getInstance()

    private lateinit var statusText: TextView
    private lateinit var connectionStatusText: TextView
    private lateinit var updateInfoText: TextView
    private lateinit var childAudioActivityText: AudioVisualizerView
    private lateinit var childDeviceStatusText: LinearLayout
    private lateinit var childAppActivityStatusText: LinearLayout
    private lateinit var childUsageRowsContainer: LinearLayout
    private lateinit var childActivityRowsContainer: LinearLayout
    private lateinit var codeText: TextView
    private lateinit var pairingInstructionText: TextView
    private lateinit var roleIntroText: TextView
    private lateinit var startButton: LinearLayout
    private lateinit var stopButton: LinearLayout
    private lateinit var sendMp3Button: LinearLayout
private lateinit var playMp3Button: Button
    private lateinit var adminWhatsAppButton: LinearLayout
    private lateinit var parentButton: Button
    private lateinit var childButton: Button
    private lateinit var rootLayout: LinearLayout
    private lateinit var childrenContainer: LinearLayout
    private lateinit var actionButtonsRow: LinearLayout
    private lateinit var addChildButton: Button
    private lateinit var selectedChildButton: Button
    private lateinit var childQuickStatusText: TextView
    private lateinit var childBatteryGlyph: StatusGlyphDrawable
    private lateinit var childNetworkGlyph: StatusGlyphDrawable
    private lateinit var cancelPairingButton: Button
    private lateinit var updateButton: Button
    private lateinit var cameraStandbyButton: Button
    private lateinit var additionalControlsHeading: TextView
    private lateinit var cameraLiveButton: LinearLayout
    private lateinit var liveKitTestButton: LinearLayout
    private lateinit var childModePanel: TextView
    private lateinit var childLocationPanel: TextView
    private lateinit var childLocationMapView: ChildLocationMapView
    private lateinit var safeZonesContainer: LinearLayout
    private lateinit var childAppBlockPanel: TextView
    private lateinit var childAppActivityPanel: TextView
    private lateinit var childNotificationPanel: TextView
    private lateinit var childGalleryPanel: TextView
    private var childGalleryP2pManager: GalleryP2pManager? = null
    private var childGalleryProtocol: GalleryP2pGalleryProtocol? = null
    private var parentGalleryP2pManager: GalleryP2pManager? = null
    private var parentGalleryProtocol: GalleryP2pGalleryProtocol? = null
    private var parentDriveBackupBinder: ParentDriveBackupService.LocalBinder? = null
    private var parentDriveBackupBindRequested = false
    private var parentDriveBackupBound = false
    private var parentGalleryUsingBackupService = false
    private val parentDriveBackupConnection = object : android.content.ServiceConnection {
        override fun onServiceConnected(name: android.content.ComponentName?, service: android.os.IBinder?) {
            parentDriveBackupBinder = service as? ParentDriveBackupService.LocalBinder
            parentDriveBackupBound = parentDriveBackupBinder != null
            parentDriveBackupBindRequested = false
            if (parentDriveBackupBinder != null) rootLayout.post { showParentGalleryStatus() }
        }
        override fun onServiceDisconnected(name: android.content.ComponentName?) {
            parentDriveBackupBinder = null
            parentDriveBackupBound = false
            parentDriveBackupBindRequested = false
        }
    }
    private var parentGalleryDialog: AlertDialog? = null
    private var googleDriveAuthorization: GoogleDriveAuthorization? = null
    // Access tokens are short-lived; keep only in memory, never in preferences.
    private var googleDriveAccessToken: String? = null
    private var googleDriveBackupFolderId: String? = null
    private var driveReauthInProgress = false
    private var parentSendP2pManager: GalleryP2pManager? = null
    private var parentSendProtocol: ParentToChildMediaProtocol? = null
    private var parentSendStarted = false
    private var pendingParentSendUri: Uri? = null
    private var pendingParentSendName: String = ""
    private var pendingParentSendMime: String = ""
    private var parentGalleryStatus: TextView? = null
    private var parentGalleryList: LinearLayout? = null
    private val parentGalleryRows = linkedMapOf<String, LinearLayout>()
    private val parentGalleryMediaItems = linkedMapOf<String, GalleryP2pGalleryProtocol.RemoteMedia>()
    private val parentGalleryThumbQueue = ArrayDeque<String>()
    private var parentGalleryThumbInFlight: String? = null
    private var parentGalleryIndexRequested = false
    private var notificationHistoryListener: ListenerRegistration? = null
    private lateinit var parentAppBlockPanel: LinearLayout
    private lateinit var parentAppBlockSubtitle: TextView
    private var pendingLocationStart = false
    private val parentOnlyViews = mutableListOf<View>()
    private var pendingPairingCode: String? = null

    private val childNames = linkedMapOf<String, String>()
    private val childConnected = linkedMapOf<String, Boolean>()
    private val childListeners = linkedMapOf<String, ListenerRegistration>()
    private val renamePromptedCodes = mutableSetOf<String>()
    private var selectedChildCode: String? = null
    private var activeRoomCode: String? = null
    private var isChildrenPanelExpanded = false

private var mp3TransferManager: Mp3TransferManager? = null
private var receivedMp3File: File? = null

    private var pairingListener: ListenerRegistration? = null
    private var latestPairingSnapshot: DocumentSnapshot? = null
    private val childSnapshots = linkedMapOf<String, DocumentSnapshot>()
    private val staleStatusRunnable = Runnable {
        selectedChildCode?.let { code ->
            childSnapshots[code]?.let { snapshot ->
                latestPairingSnapshot = snapshot
                renderChildAudioActivity(snapshot)
                renderChildDeviceStatus(snapshot)
            }
        }
    }
    private var room: Room? = null
    private var activeLiveKitServerId: String? = null
    private var isConnecting = false
    private var connectingRoomCode: String? = null
    private var roomConnectionGeneration = 0L
    private val pendingRoomConnectedCallbacks = mutableListOf<() -> Unit>()
    private val pendingRoomFailureCallbacks = mutableListOf<(String) -> Unit>()
    private var isMicrophoneActive = false
    private var currentRole = ""

    private val characters = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    private val workerUrl =
        "https://parent-audio-token.yudhis-r15.workers.dev/token"

    companion object {
    private const val MIC_PERMISSION = 1001
    private const val MP3_PICKER = 1002
    private const val CAMERA_PERMISSION = 1003
    private const val LOCATION_PERMISSION = 1004
    private const val LOCATION_NOTIFICATION_PERMISSION = 1005
    private const val SCHEDULE_NOTIFICATION_PERMISSION = 1006
    private const val OPERATOR_PERMISSION = 1007
    private const val SAFE_ZONE_NOTIFICATION_PERMISSION = 1008
    private const val GALLERY_MEDIA_PERMISSION = 1009
    private const val PARENT_MEDIA_WRITE_PERMISSION = 1010
    private const val PARENT_MEDIA_PICKER = 1011
}
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        lifecycleRegistry.currentState = Lifecycle.State.STARTED

        auth = FirebaseAuth.getInstance()

        rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.TOP
            setPadding(dp(11), dp(7), dp(11), dp(11))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(244, 249, 246), Color.rgb(250, 252, 250), Color.rgb(238, 246, 241))
            )
        }

        val title = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(9), dp(14), dp(9))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(16, 43, 33), Color.rgb(25, 91, 67), Color.rgb(40, 116, 84))
            ).apply {
                cornerRadius = dp(19).toFloat()
                setStroke(dp(1), Color.argb(68, 255, 255, 255))
            }
            elevation = dp(3).toFloat()

            addView(TextView(this@MainActivity).apply {
                text = "PARENTAL CARE  •  FAMILY CONNECT"
                textSize = 10f
                letterSpacing = 0.13f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(Color.rgb(202, 229, 213))
            })

            val brandRow = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(3), 0, dp(1))
            }
            brandRow.addView(TextView(this@MainActivity).apply {
                text = "lim45ekawan"
                textSize = 23f
                letterSpacing = 0.005f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            brandRow.addView(TextView(this@MainActivity).apply {
                text = "BantengMati"
                textSize = 10f
                letterSpacing = 0.035f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                setPadding(dp(12), dp(7), dp(12), dp(7))
                background = GradientDrawable().apply {
                    setColor(Color.argb(46, 255, 255, 255))
                    cornerRadius = dp(20).toFloat()
                    setStroke(dp(1), Color.argb(85, 255, 255, 255))
                }
            })
            addView(brandRow)

            addView(TextView(this@MainActivity).apply {
                text = "Ruang kendali bersama"
                textSize = 13.5f
                setTextColor(Color.rgb(225, 240, 230))
                letterSpacing = 0.015f
            })
        }

        title.tag = "santethp_brand_header"

        statusText = TextView(this).apply {
            text = "Memeriksa koneksi..."
            textSize = 13.5f
            setTypeface(null, android.graphics.Typeface.BOLD)
            letterSpacing = 0.01f
            setTextColor(Color.rgb(36, 69, 52))
            gravity = Gravity.CENTER_VERTICAL
            setLineSpacing(dp(2).toFloat(), 1f)
            setPadding(dp(12), dp(6), dp(12), dp(6))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.WHITE, Color.rgb(242, 249, 244))
            ).apply {
                cornerRadius = dp(15).toFloat()
                setStroke(dp(1), Color.rgb(205, 226, 211))
            }
            elevation = dp(2).toFloat()
            minHeight = dp(40)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, dp(2), 0, dp(4)) }
        }

        connectionStatusText = TextView(this).apply {
            text = "○ OFFLINE"
            textSize = 10.5f
            setTypeface(null, android.graphics.Typeface.BOLD)
            letterSpacing = 0.015f
            setTextColor(Color.rgb(105, 116, 137))
            gravity = Gravity.CENTER
            includeFontPadding = false
            setPadding(dp(9), dp(4), dp(9), dp(4))
            background = GradientDrawable().apply {
                setColor(Color.rgb(239, 243, 249))
                cornerRadius = dp(15).toFloat()
                setStroke(dp(1), Color.rgb(218, 226, 239))
            }
            maxLines = 1
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(30)
            ).apply { setMargins(dp(4), 0, dp(3), 0) }
        }

        childBatteryGlyph = StatusGlyphDrawable(true)
        childNetworkGlyph = StatusGlyphDrawable(false)
        childQuickStatusText = TextView(this).apply {
            text = "--    ·    Menunggu jaringan"
            textSize = 11.5f
            setTypeface(null, android.graphics.Typeface.BOLD)
            letterSpacing = 0.008f
            setTextColor(Color.rgb(43, 76, 54))
            gravity = Gravity.CENTER_VERTICAL
            includeFontPadding = false
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(dp(11), dp(4), dp(11), dp(4))
            compoundDrawablePadding = dp(7)
            // Keep battery at the leading edge; network glyph is placed inline before its label. 
            setCompoundDrawablesRelativeWithIntrinsicBounds(childBatteryGlyph, null, null, null)
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = dp(13).toFloat()
                setStroke(dp(1), Color.rgb(204, 220, 244))
            }
        }

        updateInfoText = TextView(this).apply {
            text = "Belum ada pemeriksaan pembaruan."
            textSize = 11.5f
            setTextColor(Color.rgb(91, 108, 137))
            gravity = Gravity.CENTER_VERTICAL
            setLineSpacing(dp(2).toFloat(), 1f)
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = GradientDrawable().apply {
                setColor(Color.rgb(247, 249, 253))
                cornerRadius = dp(14).toFloat()
                setStroke(dp(1), Color.rgb(222, 230, 243))
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, dp(3), 0, dp(7)) }
            visibility = View.GONE
        }

        childAudioActivityText = AudioVisualizerView().apply {
            contentDescription = "Aktivitas audio anak"
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48)
            ).apply { setMargins(0, 0, 0, dp(8)) }
        }

        childUsageRowsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        childActivityRowsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        childDeviceStatusText = createChildActivityCard("📊", "RIWAYAT PENGGUNAAN (24 JAM)", childUsageRowsContainer)
        childAppActivityStatusText = createChildActivityCard("📱", "APLIKASI TERAKHIR DIBUKA", childActivityRowsContainer)
        addChildActivityRow(childUsageRowsContainer, "Menunggu data dari perangkat anak...")
        addChildActivityRow(childActivityRowsContainer, "Menunggu data dari perangkat anak...")

        childLocationMapView = ChildLocationMapView(this)
        codeText = TextView(this).apply {
            textSize = 28f
            setTypeface(null, android.graphics.Typeface.BOLD)
            letterSpacing = 0.12f
            setTextColor(Color.rgb(39, 78, 180))
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(17), dp(16), dp(17))
            background = roundedPanel(Color.argb(195, 239, 246, 255), 19)
            elevation = dp(2).toFloat()
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, dp(5), 0, dp(7)) }
        }

        pairingInstructionText = TextView(this).apply {
            text = "HUBUNGKAN HP ANAK\nMasukkan kode ini pada aplikasi SantetHP di perangkat anak. Kode berlaku selama 5 menit."
            textSize = 14f
            setTypeface(null, android.graphics.Typeface.BOLD)
            letterSpacing = 0.005f
            setTextColor(Color.rgb(38, 59, 99))
            gravity = Gravity.CENTER
            setLineSpacing(dp(4).toFloat(), 1f)
            setPadding(dp(19), dp(18), dp(19), dp(18))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.WHITE, Color.rgb(239, 245, 255))
            ).apply {
                cornerRadius = dp(19).toFloat()
                setStroke(dp(1), Color.rgb(202, 219, 247))
            }
            elevation = dp(2).toFloat()
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, dp(10), 0, dp(4)) }
            visibility = View.GONE
        }

        roleIntroText = TextView(this).apply {
            text = "SELAMAT DATANG DI SANTETHP\nPilih jenis perangkat untuk melanjutkan. Pastikan Anda memilih peran yang sesuai dengan HP ini."
            textSize = 14f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(Color.rgb(48, 67, 103))
            gravity = Gravity.CENTER
            setLineSpacing(dp(4).toFloat(), 1f)
            setPadding(dp(18), dp(20), dp(18), dp(20))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.WHITE, Color.rgb(237, 244, 255))
            ).apply {
                cornerRadius = dp(21).toFloat()
                setStroke(dp(1), Color.rgb(205, 220, 246))
            }
            elevation = dp(2).toFloat()
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, dp(24), 0, dp(12)) }
            visibility = View.GONE
        }
        parentButton = createButton("👨‍👩‍👧  ORANG TUA\nKelola perangkat anak")
        childButton = createButton("📱  PERANGKAT ANAK\nHubungkan ke orang tua")
        listOf(parentButton, childButton).forEach { button ->
            button.textSize = 14f
            button.gravity = Gravity.CENTER
            button.setLineSpacing(dp(3).toFloat(), 1f)
            button.minHeight = dp(76)
            button.setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        parentButton.background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(Color.rgb(35, 73, 174), Color.rgb(55, 105, 220))
        ).apply { cornerRadius = dp(18).toFloat() }
        childButton.background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(Color.rgb(20, 135, 163), Color.rgb(34, 105, 190))
        ).apply { cornerRadius = dp(18).toFloat() }
        startButton = createActionButton("🎙", "BICARA LIVE", Color.rgb(25, 91, 67))
        stopButton = createActionButton("⏹", "HENTIKAN LIVE", Color.rgb(190, 55, 55))
        sendMp3Button = createActionButton("♫", "PUTAR MP3", Color.rgb(25, 91, 67))
        playMp3Button = createButton("PUTAR MP3 DITERIMA")
        adminWhatsAppButton = createActionButton("WA", "CHAT ADMIN", Color.rgb(37, 211, 102))
        cameraLiveButton = createActionButton("📷", "KAMERA LIVE", Color.rgb(40, 116, 84))
        actionButtonsRow = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        val microphoneSlot = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        val firstActionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val secondActionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val actionParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            setMargins(dp(2), dp(2), dp(2), dp(2))
        }
        microphoneSlot.addView(startButton, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        microphoneSlot.addView(stopButton, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        firstActionRow.addView(microphoneSlot, LinearLayout.LayoutParams(actionParams))
        firstActionRow.addView(sendMp3Button, LinearLayout.LayoutParams(actionParams))
        secondActionRow.addView(cameraLiveButton, LinearLayout.LayoutParams(actionParams))
        actionButtonsRow.addView(firstActionRow)
        addChildButton = createButton("+")
        addChildButton.textSize = 24f
        addChildButton.includeFontPadding = false
        addChildButton.textAlignment = View.TEXT_ALIGNMENT_CENTER
        addChildButton.setTypeface(android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD))
        addChildButton.contentDescription = "Tambah anak"
        addChildButton.minimumHeight = dp(42)
        addChildButton.minimumWidth = dp(42)
        addChildButton.setPadding(0, 0, 0, 0)
        addChildButton.gravity = Gravity.CENTER
        addChildButton.stateListAnimator = null
        addChildButton.layoutParams = LinearLayout.LayoutParams(dp(42), dp(42)).apply { setMargins(dp(3), 0, dp(2), 0) }
        selectedChildButton = createButton("Pilih anak  ▾")
        selectedChildButton.gravity = Gravity.CENTER_VERTICAL
        selectedChildButton.setOnClickListener { toggleChildrenPanel() }
        selectedChildButton.setTextColor(Color.rgb(31, 83, 56))
        selectedChildButton.textSize = 14.5f
        selectedChildButton.maxLines = 1
        selectedChildButton.ellipsize = android.text.TextUtils.TruncateAt.END
        selectedChildButton.contentDescription = "Pilih perangkat anak"
        selectedChildButton.background = GradientDrawable().apply {
            setColor(Color.rgb(250, 253, 250))
            cornerRadius = dp(13).toFloat()
            setStroke(dp(1), Color.rgb(202, 224, 209))
        }
        selectedChildButton.setPadding(dp(13), dp(2), dp(10), dp(2))
        selectedChildButton.elevation = dp(1).toFloat()
        selectedChildButton.minimumHeight = dp(40)
        selectedChildButton.setTypeface(null, android.graphics.Typeface.BOLD)
        addChildButton.setTextColor(Color.WHITE)
        addChildButton.background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(Color.rgb(31, 105, 73), Color.rgb(25, 91, 67), Color.rgb(48, 128, 91))
        ).apply {
            cornerRadius = dp(16).toFloat()
            setStroke(dp(1), Color.argb(90, 255, 255, 255))
        }
        addChildButton.elevation = dp(3).toFloat()
        cancelPairingButton = createButton("BATALKAN KODE")
        updateButton = createButton("PERIKSA PEMBARUAN")
        cameraStandbyButton = createButton("AKTIFKAN SIAGA KAMERA")
        liveKitTestButton = createActionButton("▤", "SERVER", Color.rgb(25, 91, 67))
        listOf(playMp3Button, cameraStandbyButton).forEach { button ->
            button.textSize = 12.5f
            button.minHeight = dp(44)
            button.setPadding(dp(12), dp(5), dp(12), dp(5))
            button.layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, dp(4), 0, dp(4)) }
        }
        liveKitTestButton.setOnClickListener { showLiveKitServerTestDialog() }
        childrenContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }

        parentButton.setOnClickListener { createPairingCode() }
        childButton.setOnClickListener { showCodeDialog() }
        startButton.setOnClickListener { startMicrophone() }
        stopButton.setOnClickListener { stopMicrophone() }
        sendMp3Button.setOnClickListener { openMp3Picker() }
        playMp3Button.setOnClickListener { playReceivedMp3() }
        adminWhatsAppButton.setOnClickListener { openAdminWhatsApp() }
        addChildButton.setOnClickListener { createPairingCode() }
        cameraStandbyButton.setOnClickListener { toggleRemoteCameraStandby() }
        cameraLiveButton.setOnClickListener { requestRemoteCamera() }
        cancelPairingButton.setOnClickListener { cancelPendingPairingCode() }
        updateButton.setOnClickListener {
            val versionCode = packageManager.getPackageInfo(packageName, 0).versionCode
            AppUpdateManager.checkForUpdates(this, versionCode) { message ->
                val styledMessage = android.text.SpannableString(message)
                val detailStart = message.indexOf('\n')
                if (detailStart >= 0 && detailStart < message.length - 1) {
                    styledMessage.setSpan(
                        android.text.style.RelativeSizeSpan(0.78f),
                        detailStart + 1,
                        message.length,
                        android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    styledMessage.setSpan(
                        android.text.style.StyleSpan(android.graphics.Typeface.NORMAL),
                        detailStart + 1,
                        message.length,
                        android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
                updateInfoText.text = styledMessage
            }
        }

        rootLayout.addView(title, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(9) })
        childModePanel = TextView(this).apply {
            text = "🛡️  HP ANAK SIAP\n\nPerangkat berhasil terhubung\nMenunggu perintah dari HP orang tua"
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(Color.rgb(35, 53, 88))
            gravity = Gravity.CENTER
            setLineSpacing(dp(4).toFloat(), 1f)
            setPadding(dp(18), dp(22), dp(18), dp(22))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.WHITE, Color.rgb(239, 247, 242))
            ).apply {
                cornerRadius = dp(19).toFloat()
                setStroke(dp(1), Color.rgb(201, 222, 210))
            }
            elevation = dp(3).toFloat()
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, dp(8), 0, dp(10)) }
            visibility = View.GONE
        }
        childModePanel.setOnClickListener {
            SantetHpDialogBuilder()
                .setTitle("Riwayat penggunaan aplikasi")
                .setMessage(
                    "SantetHP perlu izin khusus Android bernama Akses penggunaan aplikasi (Usage Access). Ketuk Buka pengaturan, cari SantetHP, lalu aktifkan sakelarnya. Setelah kembali ke SantetHP, status izin akan diperiksa langsung oleh Android. Yang dibagikan hanya nama aplikasi dan perkiraan durasi penggunaan 24 jam terakhir."
                )
                .setNegativeButton("Nanti", null)
                .setPositiveButton("Buka pengaturan") { _, _ ->
                    getSharedPreferences("parent_audio", MODE_PRIVATE).edit()
                        .putBoolean("child_usage_audit_enabled", true).apply()
                    try {
                        startActivity(Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    } catch (_: Exception) {
                        Toast.makeText(this, "Pengaturan Usage Access tidak tersedia di perangkat ini", Toast.LENGTH_LONG).show()
                    }
                }
                .show()
        }
        updateChildUsageAccessPanel()
        rootLayout.addView(roleIntroText)
        rootLayout.addView(childModePanel)
        childLocationPanel = TextView(this).apply {
            text = "📍  BERBAGI LOKASI HP INI\nNonaktif • Ketuk untuk membaca penjelasan dan mengaktifkan berbagi lokasi"
            textSize = 12.5f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(Color.rgb(36, 70, 56))
            gravity = Gravity.CENTER
            setLineSpacing(dp(2).toFloat(), 1f)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.WHITE, Color.rgb(239, 247, 242))
            ).apply {
                cornerRadius = dp(15).toFloat()
                setStroke(dp(1), Color.rgb(201, 222, 210))
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, dp(3), 0, dp(7)) }
            visibility = View.GONE
            setOnClickListener { showChildLocationConsentDialog() }
        }
        rootLayout.addView(childLocationPanel)
        childAppBlockPanel = TextView(this).apply {
            text = "🚫  BLOKIR APLIKASI\nNonaktif • Ketuk untuk membaca penjelasan dan mengaktifkan kontrol aplikasi"
            textSize = 12.5f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(Color.rgb(36, 70, 56))
            gravity = Gravity.CENTER
            setLineSpacing(dp(2).toFloat(), 1f)
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.WHITE, Color.rgb(239, 247, 242))).apply {
                cornerRadius = dp(15).toFloat()
                setStroke(dp(1), Color.rgb(201, 222, 210))
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, dp(2), 0, dp(5)) }
            visibility = View.GONE
            setOnClickListener { showChildAppBlockConsent() }
        }
        rootLayout.addView(childAppBlockPanel)
        childAppActivityPanel = TextView(this).apply {
            text = "📱  PEMANTAUAN APLIKASI AKTIF\nNonaktif • Ketuk untuk membaca penjelasan dan memberi persetujuan"
            textSize = 12.5f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(Color.rgb(36, 70, 56))
            gravity = Gravity.CENTER
            setLineSpacing(dp(2).toFloat(), 1f)
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.WHITE, Color.rgb(239, 247, 242))
            ).apply {
                cornerRadius = dp(15).toFloat()
                setStroke(dp(1), Color.rgb(201, 222, 210))
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, dp(2), 0, dp(5)) }
            visibility = View.GONE
            setOnClickListener { showChildAppActivityConsent() }
        }
        rootLayout.addView(childAppActivityPanel)
        childNotificationPanel = TextView(this).apply {
            text = "🔔  PEMANTAUAN NOTIFIKASI\nNonaktif • Ketuk untuk membaca penjelasan dan mengaktifkan akses notifikasi"
            textSize = 12.5f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(Color.rgb(36, 70, 56))
            gravity = Gravity.CENTER
            setLineSpacing(dp(2).toFloat(), 1f)
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.WHITE, Color.rgb(239, 247, 242))
            ).apply {
                cornerRadius = dp(15).toFloat()
                setStroke(dp(1), Color.rgb(201, 222, 210))
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, dp(2), 0, dp(5)) }
            visibility = View.GONE
            setOnClickListener { showChildNotificationConsent() }
        }
        rootLayout.addView(childNotificationPanel)
        childGalleryPanel = TextView(this).apply {
            text = "🖼️  BERBAGI GALERI HP INI\nNonaktif • Ketuk untuk membaca penjelasan dan memilih media yang boleh dibagikan"
            textSize = 12.5f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(Color.rgb(36, 70, 56))
            gravity = Gravity.CENTER
            setLineSpacing(dp(2).toFloat(), 1f)
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.WHITE, Color.rgb(239, 247, 242))
            ).apply {
                cornerRadius = dp(15).toFloat()
                setStroke(dp(1), Color.rgb(201, 222, 210))
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, dp(2), 0, dp(5)) }
            visibility = View.GONE
            setOnClickListener { showChildMediaSettingsMenu() }
        }
        rootLayout.addView(childGalleryPanel)
        val deviceSectionTitle = TextView(this).apply {
            text = "PERANGKAT ANAK"
            textSize = 11.5f
            setTextColor(Color.rgb(49, 82, 145))
            setTypeface(null, android.graphics.Typeface.BOLD)
            letterSpacing = 0.085f
            setPadding(dp(4), dp(7), dp(4), dp(3))
        }
        rootLayout.addView(deviceSectionTitle)
        parentOnlyViews.add(deviceSectionTitle)
        val childHeaderRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val childIdentityColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
        }
        childIdentityColumn.addView(selectedChildButton, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(42)
        ))
        childHeaderRow.addView(childIdentityColumn, LinearLayout.LayoutParams(0, dp(42), 1f).apply { weight = 1f })
        val notificationsBellButton = createButton("🔔")
        notificationsBellButton.textSize = 19f
        notificationsBellButton.minimumHeight = dp(42)
        notificationsBellButton.minimumWidth = dp(42)
        notificationsBellButton.setPadding(0, 0, 0, 0)
        notificationsBellButton.gravity = Gravity.CENTER
        notificationsBellButton.stateListAnimator = null
        notificationsBellButton.contentDescription = "Lihat notifikasi HP anak"
        notificationsBellButton.setTextColor(Color.rgb(42, 76, 145))
        notificationsBellButton.background = GradientDrawable().apply {
            setColor(Color.WHITE)
            cornerRadius = dp(15).toFloat()
            setStroke(dp(1), Color.rgb(210, 223, 245))
        }
        notificationsBellButton.layoutParams = LinearLayout.LayoutParams(dp(42), dp(42)).apply {
            setMargins(dp(3), 0, dp(3), 0)
        }
        notificationsBellButton.setOnClickListener { showChildNotificationsDialog() }
        childHeaderRow.addView(notificationsBellButton)
        parentOnlyViews.add(notificationsBellButton)
        childHeaderRow.addView(addChildButton)
        rootLayout.addView(childHeaderRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42)))
        parentOnlyViews.add(childHeaderRow)
        rootLayout.addView(childQuickStatusText, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(32)
        ).apply { setMargins(dp(3), dp(4), dp(3), dp(3)) })
        parentOnlyViews.add(childQuickStatusText)
        rootLayout.addView(childrenContainer)
        parentOnlyViews.add(childrenContainer)
        rootLayout.addView(parentButton)
        rootLayout.addView(childButton)
        val statusHeading = TextView(this).apply {
            text = "STATUS APLIKASI"
            textSize = 11.5f
            setTextColor(Color.rgb(49, 82, 145))
            setTypeface(null, android.graphics.Typeface.BOLD)
            letterSpacing = 0.085f
            setPadding(dp(4), dp(8), dp(4), dp(4))
        }
        rootLayout.addView(statusHeading)
        parentOnlyViews.add(statusHeading)
        rootLayout.addView(statusText)
        parentOnlyViews.add(statusText)
        rootLayout.addView(pairingInstructionText)
        rootLayout.addView(codeText)
        rootLayout.addView(childAudioActivityText)
        parentOnlyViews.add(childAudioActivityText)
        val featureHeading = TextView(this).apply {
            text = "8 FITUR UTAMA"
            textSize = 11.5f
            setTextColor(Color.rgb(25, 91, 67))
            setTypeface(null, android.graphics.Typeface.BOLD)
            letterSpacing = 0.085f
            setPadding(dp(4), dp(8), dp(4), dp(4))
        }
        parentAppBlockPanel = createActionButton("🚫", "BLOKIR APLIKASI", Color.rgb(25, 91, 67))
        parentAppBlockSubtitle = ((parentAppBlockPanel.getChildAt(1) as LinearLayout).getChildAt(1) as TextView)
        parentAppBlockPanel.setOnClickListener { showParentAppBlockDialog() }

        val parentGalleryButton = createActionButton("🖼️", "GALLERY", Color.rgb(25, 91, 67)).apply {
            setOnClickListener { showGaleriBackupMenu() }
        }
        parentGalleryButton.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
            setMargins(dp(3), dp(3), dp(3), dp(3))
        }
        secondActionRow.addView(parentGalleryButton, LinearLayout.LayoutParams(actionParams))

        val mapsButton = createActionButton("🗺️", "MAPS", Color.rgb(25, 91, 67)).apply {
            setOnClickListener { showMapsPage() }
        }
        mapsButton.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
            setMargins(dp(3), dp(3), dp(3), dp(3))
        }
        liveKitTestButton.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
            setMargins(dp(3), dp(3), dp(3), dp(3))
        }

        val firstFeatureRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        firstFeatureRow.addView(parentAppBlockPanel, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
            setMargins(dp(3), dp(3), dp(3), dp(3))
        })
        firstFeatureRow.addView(liveKitTestButton)
        val secondFeatureRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        secondFeatureRow.addView(mapsButton)
        adminWhatsAppButton.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
            setMargins(dp(3), dp(3), dp(3), dp(3))
        }
        secondFeatureRow.addView(adminWhatsAppButton)

        val featureGroupCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(9), dp(10), dp(9))
            background = GradientDrawable().apply {
                setColor(Color.rgb(251, 253, 252))
                cornerRadius = dp(18).toFloat()
                setStroke(dp(1), Color.rgb(211, 230, 221))
            }
            elevation = dp(2).toFloat()
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, dp(2), 0, dp(5)) }
        }
        featureGroupCard.addView(featureHeading)
        featureGroupCard.addView(actionButtonsRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(68)
        ))
        featureGroupCard.addView(secondActionRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(68)
        ))
        featureGroupCard.addView(firstFeatureRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(68)
        ))
        featureGroupCard.addView(secondFeatureRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(68)
        ))
        rootLayout.addView(featureGroupCard)
        parentOnlyViews.add(featureGroupCard)
        rootLayout.addView(childAppActivityStatusText)
        rootLayout.addView(childDeviceStatusText)
        parentOnlyViews.add(childAppActivityStatusText)
        parentOnlyViews.add(childDeviceStatusText)

        rootLayout.addView(cancelPairingButton)
        additionalControlsHeading = TextView(this).apply {
            text = "KONTROL TAMBAHAN"
            textSize = 11.5f
            setTextColor(Color.rgb(49, 82, 145))
            setTypeface(null, android.graphics.Typeface.BOLD)
            letterSpacing = 0.085f
            setPadding(dp(4), dp(8), dp(4), dp(4))
            visibility = View.GONE
        }
        rootLayout.addView(additionalControlsHeading)
        rootLayout.addView(playMp3Button)
        rootLayout.addView(cameraStandbyButton)
        // All eight dashboard actions are grouped together as equal main features.

        val mapsPage = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(18))
            setBackgroundColor(Color.rgb(244, 249, 246))
            visibility = View.GONE
        }
        val mapsHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(5), 0, dp(12))
        }
        val mapsBack = TextView(this).apply {
            text = "‹"
            textSize = 34f
            setTextColor(Color.rgb(24, 96, 70))
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = dp(15).toFloat()
                setStroke(dp(1), Color.rgb(211, 230, 221))
            }
            layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
            setOnClickListener { showParentDashboard() }
            contentDescription = "Kembali ke dashboard"
        }
        val mapsTitle = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        mapsTitle.addView(TextView(this@MainActivity).apply {
            text = "Maps"
            textSize = 23f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(Color.rgb(24, 86, 64))
        })
        mapsTitle.addView(TextView(this@MainActivity).apply {
            text = "Lokasi perangkat anak"
            textSize = 12f
            setTextColor(Color.rgb(104, 117, 140))
        })
        mapsHeader.addView(mapsBack)
        mapsHeader.addView(mapsTitle)
        mapsPage.addView(mapsHeader)
        mapsPage.addView(childLocationMapView, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        childLocationMapView.onMapPointSelected = { latitude, longitude ->
            childLocationMapView.setMapTapMode(false)
            showSafeZoneEditor(selectedLatitude = latitude, selectedLongitude = longitude)
        }
        val addSafeZoneButton = Button(this).apply {
            text = "＋ TAMBAH TITIK AMAN"
            isAllCaps = false
            textSize = 13f
            setTextColor(Color.WHITE)
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(32, 119, 84), Color.rgb(20, 91, 65))
            ).apply {
                cornerRadius = dp(14).toFloat()
                setStroke(dp(1), Color.rgb(104, 166, 137))
            }
            elevation = dp(2).toFloat()
            stateListAnimator = null
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48)
            ).apply { setMargins(0, dp(9), 0, dp(6)) }
            setOnClickListener { startSafeZoneMapSelection() }
        }
        mapsPage.addView(addSafeZoneButton)
        mapsPage.addView(TextView(this).apply {
            text = "Pilih lokasi langsung pada peta. Setiap titik dapat diberi nama dan radius 50–5.000 meter."
            textSize = 11.5f
            setTextColor(Color.rgb(77, 105, 91))
            setPadding(dp(11), dp(9), dp(11), dp(9))
            background = GradientDrawable().apply {
                setColor(Color.rgb(235, 245, 239))
                cornerRadius = dp(12).toFloat()
                setStroke(dp(1), Color.rgb(211, 230, 221))
            }
        })
        safeZonesContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        mapsPage.addView(safeZonesContainer)
        rootLayout.addView(mapsPage)
        mapsPage.tag = "santethp_maps_page"
        updateButton.textSize = 12.5f
        updateButton.minHeight = dp(46)
        updateButton.background = GradientDrawable().apply {
            setColor(Color.WHITE)
            cornerRadius = dp(14).toFloat()
            setStroke(dp(1), Color.rgb(202, 218, 242))
        }
        updateButton.setTextColor(Color.rgb(45, 79, 143))
        updateButton.elevation = dp(1).toFloat()
        updateButton.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(46)
        ).apply { setMargins(0, dp(8), 0, dp(4)) }
        rootLayout.addView(updateButton)
        rootLayout.addView(updateInfoText)

        showRoleSelection()
        // Keep the brand and selected-child status visible while dashboard content scrolls.
        // Move these existing views out of the scrolling content without recreating them,
        // preserving their listeners and parent-only visibility behavior.
        rootLayout.removeView(title)
        rootLayout.removeView(deviceSectionTitle)
        rootLayout.removeView(childHeaderRow)
        rootLayout.removeView(childQuickStatusText)

        val stickyHeader = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(11), dp(7), dp(11), dp(3))
        }
        stickyHeader.addView(title, title.layoutParams)
        stickyHeader.addView(deviceSectionTitle, deviceSectionTitle.layoutParams)
        stickyHeader.addView(childHeaderRow, childHeaderRow.layoutParams)
        stickyHeader.addView(childQuickStatusText, childQuickStatusText.layoutParams)

        val scrollView = ScrollView(this).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            setBackgroundColor(Color.TRANSPARENT)
            addView(rootLayout)
        }
        val screenLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(244, 249, 246), Color.rgb(250, 252, 250), Color.rgb(238, 246, 241))
            )
            addView(stickyHeader, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ))
            addView(scrollView, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            ))
        }
        setContentView(screenLayout)

        connectFirebase()
    }

    override fun onResume() {
        super.onResume()
        startChildPresenceRefresh()
        AppUpdateManager.resumePendingInstall(this)
        reauthorizeParentDriveBackupIfNeeded()
        // Retry registration whenever the child app returns to foreground.
        val childPrefs = getSharedPreferences("parent_audio", MODE_PRIVATE)
        if (childPrefs.getString("pair_role", null) == "child") {
            if (childPrefs.getBoolean("child_gallery_sharing_enabled", false) && !hasGalleryMediaAccess()) {
                childPrefs.edit()
                    .putBoolean("child_gallery_sharing_enabled", false)
                    .putBoolean("child_gallery_server_consent_confirmed", false)
                    .apply()
                updateGalleryConsentOnServer(false)
            } else if (
                childPrefs.getBoolean("child_gallery_sharing_enabled", false) &&
                hasGalleryMediaAccess() &&
                !childPrefs.getBoolean("child_gallery_server_consent_confirmed", false)
            ) {
                // Retry a consent write that may have been skipped or failed previously.
                updateGalleryConsentOnServer(true) { startChildGalleryP2pIfAllowed() }
            } else if (
                childPrefs.getBoolean("child_parent_media_receive_consent", false) &&
                !childPrefs.getBoolean("child_parent_media_server_confirmed", false)
            ) {
                updateParentMediaReceiveConsentOnServer(true)
                startChildGalleryP2pIfAllowed()
            } else {
                startChildGalleryP2pIfAllowed()
            }
            updateChildUsageAccessPanel()
            maybeRequestScheduleNotificationPermission()
            rootLayout.postDelayed({ maybeRequestOperatorPermission() }, 1200)
            updateCameraStandbyButton()
            updateChildAppBlockPanel()
            updateChildAppActivityPanel()
            updateChildNotificationPanel()
            updateChildGalleryPanel()
            // Re-evaluate the child landing page after Android settings return.
            showChildConnectedScreen()
            // Device battery/network reporting is independent of Usage Access.
            // Always re-enqueue the lightweight status worker when the child app resumes.
            ChildDeviceStatusWorker.schedule(this)
            refreshFcmToken()
            restoreCameraStandbyIfNeeded()
        }
    }

    override fun onPause() {
        stopChildPresenceRefresh()
        if (currentRole == "parent" && parentGalleryDialog?.isShowing == true) {
            parentGalleryDialog?.dismiss()
        }
        super.onPause()
    }

    private fun reauthorizeParentDriveBackupIfNeeded() {
        if (currentRole != "parent" || driveReauthInProgress) return
        val prefs = getSharedPreferences("parent_audio", MODE_PRIVATE)
        if (!prefs.getBoolean("parent_drive_backup_service_active", false)) return
        val lastAuthorized = prefs.getLong("parent_drive_backup_authorized_at", 0L)
        val needsToken = prefs.getBoolean("parent_drive_backup_reauth_required", false) ||
            googleDriveAccessToken.isNullOrBlank() ||
            System.currentTimeMillis() - lastAuthorized >= 45L * 60L * 1000L
        if (!needsToken) return
        val code = prefs.getString("parent_drive_backup_pair_code", null) ?: return
        driveReauthInProgress = true
        val authorization = GoogleDriveAuthorization(this)
        googleDriveAuthorization = authorization
        authorization.authorize { token, error ->
            runOnUiThread {
                driveReauthInProgress = false
                if (token.isNullOrBlank()) {
                    statusText.text = "Backup Drive menunggu otorisasi ulang: ${error ?: "buka menu Backup Google Drive"}"
                    return@runOnUiThread
                }
                googleDriveAccessToken = token
                prefs.edit().putLong("parent_drive_backup_authorized_at", System.currentTimeMillis())
                    .putBoolean("parent_drive_backup_reauth_required", false).apply()
                val intent = Intent(this, ParentDriveBackupService::class.java).apply {
                    action = ParentDriveBackupService.ACTION_START
                    putExtra(ParentDriveBackupService.EXTRA_PAIR_CODE, code)
                    putExtra(ParentDriveBackupService.EXTRA_ACCESS_TOKEN, token)
                    putExtra(ParentDriveBackupService.EXTRA_CHILD_NAME, prefs.getString("parent_drive_backup_child_name", "HP Anak") ?: "HP Anak")
                    prefs.getString("parent_drive_backup_child_queue_json", null)?.let {
                        putExtra(ParentDriveBackupService.EXTRA_CHILD_QUEUE_JSON, it)
                    }
                }
                try {
                    ContextCompat.startForegroundService(this, intent)
                    statusText.text = "Izin Google Drive diperbarui • backup dilanjutkan"
                } catch (exception: Exception) {
                    statusText.text = "Izin Drive diperbarui, tetapi layanan belum dapat dilanjutkan: ${exception.localizedMessage ?: "coba lagi"}"
                }
            }
        }
    }

    private fun startChildGalleryP2pIfAllowed() {
        if (currentRole != "child") return
        val prefs = getSharedPreferences("parent_audio", MODE_PRIVATE)
        val galleryConsent = prefs.getBoolean("child_gallery_sharing_enabled", false) &&
            prefs.getBoolean("child_gallery_server_consent_confirmed", false)
        val backupConsent = prefs.getBoolean("child_drive_backup_consent", false)
        // Backup has its own explicit child consent. It may keep the transport
        // ready without enabling parent browsing of the child's gallery.
        val receiveConsent = prefs.getBoolean("child_parent_media_receive_consent", false) &&
            prefs.getBoolean("child_parent_media_server_confirmed", false)
        if (!galleryConsent && !backupConsent && !receiveConsent) return
        val code = prefs.getString("pair_code", null)
        if (code.isNullOrBlank()) return

        // The foreground service owns WebRTC and the media observer. It survives
        // Activity.onPause(), so switching apps or locking the screen won't close P2P.
        val serviceIntent = Intent(this, ChildGalleryP2pService::class.java)
            .setAction(ChildGalleryP2pService.ACTION_START)
        try {
            ContextCompat.startForegroundService(this, serviceIntent)
        } catch (error: Exception) {
            Toast.makeText(
                this,
                "Layanan galeri tidak dapat dimulai: ${error.localizedMessage ?: "periksa izin notifikasi dan pengaturan baterai"}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun stopChildGalleryP2p() {
        stopService(Intent(this, ChildGalleryP2pService::class.java))
    }

    private fun updateChildUsageAccessPanel() {
        if (!this::childModePanel.isInitialized) return
        val prefs = getSharedPreferences("parent_audio", MODE_PRIVATE)
        val optedIn = prefs.getBoolean("child_usage_audit_enabled", false)
        val granted = AppUsageReporter.hasUsageAccess(this)
        childModePanel.text = when {
            !optedIn ->
                "🛡️  HP ANAK SIAP\n\nPerangkat berhasil terhubung\nRiwayat penggunaan aplikasi: BELUM DIAKTIFKAN\n\nKetuk untuk membaca penjelasan dan mengatur izin secara opsional."
            granted ->
                "🛡️  HP ANAK SIAP\n\nPerangkat berhasil terhubung\nRIWAYAT PENGGUNAAN: IZIN ANDROID AKTIF ✓\nSantetHP telah memverifikasi Akses penggunaan aplikasi dari Android."
            else ->
                "🛡️  HP ANAK SIAP\n\nPerangkat berhasil terhubung\nRIWAYAT PENGGUNAAN: IZIN ANDROID BELUM AKTIF\nKetuk di sini, pilih Buka pengaturan, lalu aktifkan sakelar SantetHP pada halaman Akses penggunaan aplikasi Android."
        }
        if (optedIn) ChildDeviceStatusWorker.schedule(this)
    }

    private fun maybeRequestScheduleNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT < 33) return

        val prefs = getSharedPreferences("parent_audio", MODE_PRIVATE)
        if (!prefs.getBoolean("child_schedule_config_enabled", false)) return
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) return
        if (prefs.getBoolean("child_schedule_notification_prompted", false)) return

        SantetHpDialogBuilder()
            .setTitle("Aktifkan notifikasi jadwal")
            .setMessage(
                "Jadwal penggunaan HP dari orang tua sudah tersimpan. Izinkan notifikasi agar SantetHP dapat memberi pengingat saat waktu belajar atau istirahat dimulai dan berakhir. Pengingat ini hanya berjalan sesuai jadwal yang tersimpan di HP anak."
            )
            .setPositiveButton("Aktifkan") { _, _ ->
                prefs.edit()
                    .putBoolean("child_schedule_notification_prompted", true)
                    .apply()
                requestPermissions(
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    SCHEDULE_NOTIFICATION_PERMISSION
                )
            }
            .setNegativeButton("Nanti") { _, _ ->
                prefs.edit()
                    .putBoolean("child_schedule_notification_prompted", true)
                    .apply()
            }
            .show()
    }

    private fun maybeRequestOperatorPermission() {
        if (currentRole != "child") return
        val prefs = getSharedPreferences("parent_audio", MODE_PRIVATE)
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.READ_PHONE_STATE
            ) == PackageManager.PERMISSION_GRANTED
        ) return
        if (prefs.getBoolean("child_operator_permission_prompted", false)) return

        SantetHpDialogBuilder()
            .setTitle("Izin nama operator seluler")
            .setMessage(
                "SantetHP dapat menampilkan nama operator SIM pada HP orang tua, misalnya Telkomsel atau Indosat. Izin ini hanya digunakan untuk membaca nama operator jaringan. SantetHP tidak membaca nomor telepon, SMS, atau isi komunikasi. Izin ini opsional; pemantauan baterai dan jenis jaringan tetap berjalan jika ditolak."
            )
            .setNegativeButton("Nanti") { _, _ ->
                prefs.edit().putBoolean("child_operator_permission_prompted", true).apply()
            }
            .setPositiveButton("Izinkan") { _, _ ->
                prefs.edit().putBoolean("child_operator_permission_prompted", true).apply()
                requestPermissions(
                    arrayOf(Manifest.permission.READ_PHONE_STATE),
                    OPERATOR_PERMISSION
                )
            }
            .show()
    }

    private fun showRoleSelection() {
        roleIntroText.visibility = View.VISIBLE
        childModePanel.visibility = View.GONE
        childLocationPanel.visibility = View.GONE
        childAppBlockPanel.visibility = View.GONE
        childAppActivityPanel.visibility = View.GONE
        childNotificationPanel.visibility = View.GONE
        childGalleryPanel.visibility = View.GONE
        parentOnlyViews.forEach { it.visibility = View.GONE }
        additionalControlsHeading.visibility = View.GONE
        actionButtonsRow.visibility = View.GONE
        parentButton.visibility = View.VISIBLE
        childButton.visibility = View.VISIBLE
        selectedChildButton.visibility = View.GONE
        connectionStatusText.visibility = View.GONE
        updateInfoText.visibility = View.GONE
        pairingInstructionText.visibility = View.GONE
        codeText.visibility = View.GONE
        childAudioActivityText.visibility = View.GONE
        startButton.visibility = View.GONE
        stopButton.visibility = View.GONE
        sendMp3Button.visibility = View.GONE
        playMp3Button.visibility = View.GONE
        adminWhatsAppButton.visibility = View.GONE
        childrenContainer.visibility = View.GONE
        addChildButton.visibility = View.GONE
        cancelPairingButton.visibility = View.GONE
        cameraStandbyButton.visibility = View.GONE
        cameraLiveButton.visibility = View.GONE
        updateButton.visibility = View.GONE
    }

    private fun showPairingCodeScreen() {
        roleIntroText.visibility = View.GONE
        childModePanel.visibility = View.GONE
        childLocationPanel.visibility = View.GONE
        childAppActivityPanel.visibility = View.GONE
        childNotificationPanel.visibility = View.GONE
        childGalleryPanel.visibility = View.GONE
        parentOnlyViews.forEach { it.visibility = View.GONE }
        additionalControlsHeading.visibility = View.GONE
        actionButtonsRow.visibility = View.GONE
        parentButton.visibility = View.GONE
        childButton.visibility = View.GONE
        selectedChildButton.visibility = View.GONE
        connectionStatusText.visibility = View.GONE
        updateInfoText.visibility = View.GONE
        pairingInstructionText.visibility = View.VISIBLE
        codeText.visibility = View.VISIBLE
        childAudioActivityText.visibility = View.GONE
        startButton.visibility = View.GONE
        stopButton.visibility = View.GONE
        sendMp3Button.visibility = View.GONE
        playMp3Button.visibility = View.GONE
        adminWhatsAppButton.visibility = View.GONE
        childrenContainer.visibility = View.GONE
        addChildButton.visibility = View.GONE
        cancelPairingButton.visibility = View.VISIBLE
        cameraStandbyButton.visibility = View.GONE
        cameraLiveButton.visibility = View.GONE
        updateButton.visibility = View.GONE
    }

    private data class SafeZone(
        val id: String,
        val name: String,
        val latitude: Double,
        val longitude: Double,
        val radiusM: Int
    )

    private fun readSafeZones(snapshot: DocumentSnapshot): MutableList<SafeZone> {
        val result = mutableListOf<SafeZone>()
        val raw = snapshot.get("safeZones") as? List<*>
        if (raw != null) {
            raw.forEachIndexed { index, value ->
                val item = value as? Map<*, *> ?: return@forEachIndexed
                val lat = (item["latitude"] as? Number)?.toDouble() ?: return@forEachIndexed
                val lon = (item["longitude"] as? Number)?.toDouble() ?: return@forEachIndexed
                val radius = (item["radiusM"] as? Number)?.toInt() ?: return@forEachIndexed
                if (lat !in -90.0..90.0 || lon !in -180.0..180.0 || radius !in 50..5000) return@forEachIndexed
                result.add(
                    SafeZone(
                        (item["id"] as? String) ?: "zone-" + index,
                        (item["name"] as? String) ?: "Area aman " + (index + 1),
                        lat, lon, radius
                    )
                )
            }
        } else if (snapshot.getBoolean("geofenceEnabled") == true) {
            val lat = snapshot.getDouble("geofenceLatitude")
            val lon = snapshot.getDouble("geofenceLongitude")
            val radius = snapshot.getLong("geofenceRadiusM")?.toInt() ?: 0
            if (lat != null && lon != null && radius in 50..5000) {
                result.add(SafeZone("legacy-geofence", "Area aman lama", lat, lon, radius))
            }
        }
        return result
    }

    private fun safeZonesJson(snapshot: DocumentSnapshot): String {
        val array = JSONArray()
        readSafeZones(snapshot).forEach { zone ->
            array.put(
                JSONObject().put("id", zone.id).put("name", zone.name)
                    .put("latitude", zone.latitude).put("longitude", zone.longitude)
                    .put("radiusM", zone.radiusM)
            )
        }
        return array.toString()
    }

    private fun renderSafeZoneList(snapshot: DocumentSnapshot) {
        if (!this::safeZonesContainer.isInitialized) return
        safeZonesContainer.removeAllViews()
        val zones = readSafeZones(snapshot)
        safeZonesContainer.addView(TextView(this).apply {
            text = "📍  TITIK AMAN TERSIMPAN  " + zones.size + "/10"
            textSize = 11.5f
            setTypeface(null, android.graphics.Typeface.BOLD)
            letterSpacing = 0.035f
            setTextColor(Color.rgb(25, 91, 67))
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(11), dp(9), dp(11), dp(9))
            background = GradientDrawable().apply {
                setColor(Color.rgb(235, 246, 239))
                cornerRadius = dp(11).toFloat()
                setStroke(dp(1), Color.rgb(198, 222, 207))
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, dp(5), 0, dp(7)) }
        })
        if (zones.isEmpty()) {
            safeZonesContainer.addView(TextView(this).apply {
                text = "Belum ada titik aman. Tekan tombol tambah, lalu pilih lokasi pada peta."
                textSize = 12f
                setTextColor(Color.rgb(83, 108, 99))
                gravity = Gravity.CENTER_VERTICAL
                setLineSpacing(dp(2).toFloat(), 1f)
                setPadding(dp(12), dp(12), dp(12), dp(12))
                background = GradientDrawable().apply {
                    setColor(Color.rgb(248, 251, 249))
                    cornerRadius = dp(12).toFloat()
                    setStroke(dp(1), Color.rgb(216, 231, 221))
                }
            })
            renderSafeZoneHistory(snapshot)
            return
        }
        zones.forEach { zone ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), dp(9), dp(12), dp(9))
                background = GradientDrawable().apply {
                    setColor(Color.rgb(250, 253, 251))
                    cornerRadius = dp(15).toFloat()
                    setStroke(dp(1), Color.rgb(198, 222, 207))
                }
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, dp(3), 0, dp(3)) }
            }
            card.addView(TextView(this).apply {
                text = zone.name + "  •  radius " + zone.radiusM + " m"
                textSize = 13f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(Color.rgb(27, 65, 48))
            })
            card.addView(TextView(this).apply {
                text = String.format(java.util.Locale.US, "%.5f, %.5f", zone.latitude, zone.longitude)
                textSize = 10.5f
                setTextColor(Color.rgb(104, 117, 140))
                setPadding(0, dp(3), 0, dp(5))
            })
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            row.addView(Button(this).apply {
                text = "Edit"
                isAllCaps = false
                textSize = 12f
                setTextColor(Color.rgb(24, 96, 70))
                background = GradientDrawable().apply {
                    setColor(Color.rgb(235, 245, 239))
                    cornerRadius = dp(10).toFloat()
                    setStroke(dp(1), Color.rgb(197, 222, 207))
                }
                stateListAnimator = null
                setOnClickListener {
                    childLocationMapView.focusLocation(zone.latitude, zone.longitude)
                    showSafeZoneEditor(zone)
                }
            }, LinearLayout.LayoutParams(0, dp(40), 1f))
            row.addView(Button(this).apply {
                text = "Hapus"
                isAllCaps = false
                textSize = 12f
                setTextColor(Color.rgb(166, 55, 61))
                background = GradientDrawable().apply {
                    setColor(Color.rgb(255, 243, 243))
                    cornerRadius = dp(10).toFloat()
                    setStroke(dp(1), Color.rgb(239, 205, 207))
                }
                stateListAnimator = null
                setOnClickListener {
                    SantetHpDialogBuilder()
                        .setTitle("Hapus titik aman?")
                        .setMessage("Area " + zone.name + " akan dihapus.")
                        .setNegativeButton("Batal", null)
                        .setPositiveButton("Hapus") { _, _ ->
                            val latest = selectedChildCode?.let { childSnapshots[it] } ?: return@setPositiveButton
                            saveSafeZones(readSafeZones(latest).filterNot { it.id == zone.id })
                        }.show()
                }
            }, LinearLayout.LayoutParams(0, dp(40), 1f))
            card.addView(row)
            safeZonesContainer.addView(card)
        }
        renderSafeZoneHistory(snapshot)
    }

    private fun renderSafeZoneHistory(snapshot: DocumentSnapshot) {
        val events = (snapshot.get("safeZoneEvents") as? List<*>)
            ?.filterIsInstance<Map<String, Any>>()
            ?.takeLast(30)?.reversed() ?: emptyList()
        safeZonesContainer.addView(TextView(this).apply {
            text = "🕘  RIWAYAT AKTIVITAS ZONA AMAN"
            textSize = 11.5f
            setTypeface(null, android.graphics.Typeface.BOLD)
            letterSpacing = 0.025f
            setTextColor(Color.rgb(45, 79, 112))
            setPadding(dp(11), dp(9), dp(11), dp(9))
            background = GradientDrawable().apply {
                setColor(Color.rgb(239, 246, 252))
                cornerRadius = dp(11).toFloat()
                setStroke(dp(1), Color.rgb(210, 225, 239))
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, dp(8), 0, dp(6)) }
        })
        if (events.isEmpty()) {
            safeZonesContainer.addView(TextView(this).apply {
                text = "Belum ada aktivitas masuk atau keluar zona aman."
                textSize = 11.5f
                setTextColor(Color.rgb(91, 108, 124))
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(11), dp(10), dp(11), dp(10))
                background = GradientDrawable().apply {
                    setColor(Color.rgb(249, 251, 253))
                    cornerRadius = dp(11).toFloat()
                    setStroke(dp(1), Color.rgb(222, 231, 239))
                }
            })
            return
        }
        val formatter = java.text.SimpleDateFormat("dd MMM, HH:mm", java.util.Locale("id", "ID"))
        events.take(10).forEach { event ->
            val entering = event["transition"] == "ENTER"
            val zoneName = event["zoneName"]?.toString() ?: "Zona aman"
            val timestamp = event["occurredAt"] as? Timestamp
            val dateText = timestamp?.toDate()?.let { formatter.format(it) } ?: "Waktu tidak tersedia"
            safeZonesContainer.addView(TextView(this).apply {
                text = (if (entering) "● MASUK  " else "○ KELUAR  ") + zoneName + "\n" + dateText
                textSize = 11.5f
                setLineSpacing(dp(2).toFloat(), 1f)
                setTextColor(if (entering) Color.rgb(27, 91, 62) else Color.rgb(151, 65, 68))
                setPadding(dp(11), dp(9), dp(11), dp(9))
                background = GradientDrawable().apply {
                    setColor(if (entering) Color.rgb(241, 249, 244) else Color.rgb(255, 246, 246))
                    cornerRadius = dp(12).toFloat()
                    setStroke(dp(1), if (entering) Color.rgb(207, 229, 214) else Color.rgb(240, 215, 216))
                }
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, dp(2), 0, dp(2)) }
            })
        }
    }

    private fun saveSafeZones(zones: List<SafeZone>) {
        val code = selectedChildCode ?: return
        if (zones.size > 10) {
            Toast.makeText(this, "Maksimal 10 titik aman.", Toast.LENGTH_LONG).show()
            return
        }
        val values = zones.map { zone ->
            mapOf(
                "id" to zone.id, "name" to zone.name,
                "latitude" to zone.latitude, "longitude" to zone.longitude,
                "radiusM" to zone.radiusM
            )
        }
        db.collection("pairings").document(code).update(
            mapOf(
                "safeZones" to values,
                "geofenceEnabled" to false,
                "geofenceUpdatedAt" to Timestamp(Date())
            )
        ).addOnSuccessListener {
            Toast.makeText(this, "Titik aman berhasil disimpan.", Toast.LENGTH_LONG).show()
        }.addOnFailureListener { error ->
            Toast.makeText(
                this,
                "Gagal menyimpan titik aman: " + (error.localizedMessage ?: "Periksa aturan Firestore."),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun showSafeZoneEditor(
        existing: SafeZone? = null,
        selectedLatitude: Double? = null,
        selectedLongitude: Double? = null
    ) {
        val code = selectedChildCode ?: return
        val snapshot = childSnapshots[code]
        if (snapshot?.getBoolean("childLocationSharingEnabled") != true) {
            Toast.makeText(this, "Aktifkan berbagi lokasi di HP anak terlebih dahulu.", Toast.LENGTH_LONG).show()
            return
        }
        val lat = selectedLatitude ?: existing?.latitude ?: snapshot.getDouble("childLatitude")
        val lon = selectedLongitude ?: existing?.longitude ?: snapshot.getDouble("childLongitude")
        if (lat == null || lon == null) {
            Toast.makeText(this, "Lokasi belum tersedia. Pilih titik pada peta.", Toast.LENGTH_LONG).show()
            return
        }

        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(8), dp(22), dp(4))
        }
        val nameInput = EditText(this).apply {
            hint = "Contoh: Rumah, Sekolah, Les"
            setSingleLine(true)
            setText(existing?.name ?: "")
        }
        val radiusInput = EditText(this).apply {
            hint = "Radius 50–5000 meter"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setSingleLine(true)
            setText((existing?.radiusM ?: 250).toString())
        }
        form.addView(TextView(this).apply { text = "Nama titik aman"; textSize = 12f })
        form.addView(nameInput)
        form.addView(TextView(this).apply {
            text = "Radius dalam meter"
            textSize = 12f
            setPadding(0, dp(8), 0, 0)
        })
        form.addView(radiusInput)
        form.addView(TextView(this).apply {
            text = String.format(java.util.Locale.US, "Koordinat: %.6f, %.6f", lat, lon)
            textSize = 10.5f
            setTextColor(Color.rgb(104, 117, 140))
            setPadding(0, dp(8), 0, 0)
        })

        val dialog = SantetHpDialogBuilder()
            .setTitle(if (existing == null) "Tambah titik aman" else "Edit titik aman")
            .setView(popupContentCard(form)).setNegativeButton("Batal", null)
            .setPositiveButton("Simpan", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = nameInput.text.toString().trim()
                val radius = radiusInput.text.toString().toIntOrNull()
                if (name.isBlank()) {
                    nameInput.error = "Isi nama lokasi"
                } else if (radius == null || radius !in 50..5000) {
                    radiusInput.error = "Masukkan 50 sampai 5000 meter"
                } else {
                    val latest = selectedChildCode?.let { childSnapshots[it] }
                    val zones = latest?.let { readSafeZones(it) }?.toMutableList() ?: mutableListOf()
                    if (existing == null && zones.size >= 10) {
                        Toast.makeText(this, "Maksimal 10 titik aman.", Toast.LENGTH_LONG).show()
                        return@setOnClickListener
                    }
                    val zone = SafeZone(
                        existing?.id ?: java.util.UUID.randomUUID().toString(),
                        name, lat, lon, radius
                    )
                    val index = zones.indexOfFirst { it.id == zone.id }
                    if (index >= 0) zones[index] = zone else zones.add(zone)
                    dialog.dismiss()
                    saveSafeZones(zones)
                }
            }
        }
        dialog.show()
    }

    private fun startSafeZoneMapSelection() {
        val snapshot = selectedChildCode?.let { childSnapshots[it] }
        if (snapshot?.getBoolean("childLocationSharingEnabled") != true) {
            Toast.makeText(this, "Berbagi lokasi harus aktif di HP anak.", Toast.LENGTH_LONG).show()
            return
        }
        childLocationMapView.setMapTapMode(true)
        Toast.makeText(this, "Geser peta lalu ketuk lokasi untuk membuat titik aman.", Toast.LENGTH_LONG).show()
    }


    @Deprecated("Deprecated in Android API")
    override fun onBackPressed() {
        val mapsPage = rootLayout.findViewWithTag<View>("santethp_maps_page")
        if (mapsPage?.visibility == View.VISIBLE) {
            showParentDashboard()
            return
        }

        // On the parent home screen, ask before leaving SantetHP.
        if (currentRole == "parent") {
            SantetHpDialogBuilder()
                .setTitle("Keluar dari SantetHP?")
                .setMessage("Apakah kamu yakin ingin keluar dari aplikasi?")
                .setNegativeButton("BATAL", null)
                .setPositiveButton("KELUAR") { _, _ ->
                    finish()
                }
                .show()
        } else {
            super.onBackPressed()
        }
    }

    private fun showMapsPage() {
        if (currentRole != "parent") return
        for (index in 0 until rootLayout.childCount) {
            rootLayout.getChildAt(index).visibility = View.GONE
        }
        rootLayout.findViewWithTag<View>("santethp_maps_page")?.visibility = View.VISIBLE
    }

    private fun requestParentNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT < 33 || currentRole != "parent") return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) return
        val prefs = getSharedPreferences("parent_audio", MODE_PRIVATE)
        if (prefs.getBoolean("safe_zone_notification_prompted", false)) return
        SantetHpDialogBuilder()
            .setTitle("Aktifkan notifikasi zona aman")
            .setMessage("Izinkan SantetHP menampilkan pemberitahuan saat HP anak masuk atau keluar dari titik aman.")
            .setNegativeButton("Nanti") { _, _ ->
                prefs.edit().putBoolean("safe_zone_notification_prompted", true).apply()
            }
            .setPositiveButton("Izinkan") { _, _ ->
                prefs.edit().putBoolean("safe_zone_notification_prompted", true).apply()
                requestPermissions(
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    SAFE_ZONE_NOTIFICATION_PERMISSION
                )
            }
            .show()
    }

    private fun showParentDashboard() {
        rootLayout.findViewWithTag<View>("santethp_brand_header")?.visibility = View.VISIBLE
        requestParentNotificationPermission()
        childLocationMapView.setMapTapMode(false)
        roleIntroText.visibility = View.GONE
        rootLayout.findViewWithTag<View>("santethp_maps_page")?.visibility = View.GONE
        childModePanel.visibility = View.GONE
        childLocationPanel.visibility = View.GONE
        childGalleryPanel.visibility = View.GONE
        childAppActivityPanel.visibility = View.GONE
        parentOnlyViews.forEach { it.visibility = View.VISIBLE }
        additionalControlsHeading.visibility = View.GONE
        updateButton.visibility = View.VISIBLE
        actionButtonsRow.visibility = View.VISIBLE
        parentButton.visibility = View.GONE
        childButton.visibility = View.GONE
        selectedChildButton.visibility = View.VISIBLE
        connectionStatusText.visibility = View.VISIBLE
        updateInfoText.visibility = View.VISIBLE
        statusText.visibility = View.VISIBLE
        pairingInstructionText.visibility = View.GONE
        codeText.visibility = View.GONE
        childAudioActivityText.visibility = View.VISIBLE
        startButton.visibility = if (isMicrophoneActive) View.GONE else View.VISIBLE
        stopButton.visibility = if (isMicrophoneActive) View.VISIBLE else View.GONE
        sendMp3Button.visibility = View.VISIBLE
        playMp3Button.visibility = View.GONE
        adminWhatsAppButton.visibility = View.VISIBLE
        childrenContainer.visibility = if (isChildrenPanelExpanded) View.VISIBLE else View.GONE
        addChildButton.visibility = View.VISIBLE
        cancelPairingButton.visibility = View.GONE
        cameraStandbyButton.visibility = View.GONE
        cameraLiveButton.visibility = View.VISIBLE
        renderChildrenList()
        updateSelectedChildButton()
    }

    /**
     * Only permissions for features explicitly enabled on this child device
     * are required. Optional features that were never enabled do not block
     * the simplified child screen.
     */
    private fun childPermissionIssues(): List<String> {
        val prefs = getSharedPreferences("parent_audio", MODE_PRIVATE)
        val issues = mutableListOf<String>()

        if (prefs.getBoolean("child_usage_audit_enabled", false) &&
            !AppUsageReporter.hasUsageAccess(this)
        ) issues.add("Akses penggunaan aplikasi")

        if (prefs.getBoolean("child_location_sharing_enabled", false)) {
            val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            if (!fine && !coarse) issues.add("Izin lokasi")
        }

        if ((prefs.getBoolean("child_app_block_consent", false) ||
             prefs.getBoolean("child_app_activity_consent", false)) &&
            !isAppBlockAccessibilityEnabled()
        ) issues.add("Aksesibilitas SantetHP")

        if (prefs.getBoolean("child_notification_monitoring_enabled", false) &&
            !notificationListenerAccessEnabled()
        ) issues.add("Akses pemantauan notifikasi")

        if (prefs.getBoolean("child_gallery_sharing_enabled", false) &&
            !hasGalleryMediaAccess()
        ) issues.add("Akses foto dan video")

        if (prefs.getBoolean("remote_camera_armed", false) &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
        ) issues.add("Izin kamera")

        return issues
    }

    private fun showChildConnectedScreen() {
        roleIntroText.visibility = View.GONE
        parentOnlyViews.forEach { it.visibility = View.GONE }
        updateChildGalleryPanel()
        updateChildLocationPanel()
        updateChildAppBlockPanel()
        updateChildAppActivityPanel()
        updateChildNotificationPanel()

        val issues = childPermissionIssues()
        val needsSetup = issues.isNotEmpty()
        additionalControlsHeading.text = if (needsSetup) "PEMERIKSAAN PERIZINAN" else "PERANGKAT SIAP"
        additionalControlsHeading.visibility = View.VISIBLE
        childModePanel.visibility = if (issues.any { it == "Akses penggunaan aplikasi" }) View.VISIBLE else View.GONE
        childLocationPanel.visibility = if (issues.any { it.startsWith("Izin lokasi") || it.startsWith("Izin notifikasi untuk layanan lokasi") }) View.VISIBLE else View.GONE
        childAppBlockPanel.visibility = if (issues.any { it == "Aksesibilitas SantetHP" } &&
            getSharedPreferences("parent_audio", MODE_PRIVATE).getBoolean("child_app_block_consent", false)) View.VISIBLE else View.GONE
        childAppActivityPanel.visibility = if (issues.any { it == "Aksesibilitas SantetHP" } &&
            getSharedPreferences("parent_audio", MODE_PRIVATE).getBoolean("child_app_activity_consent", false)) View.VISIBLE else View.GONE
        childNotificationPanel.visibility = if (issues.any { it == "Akses pemantauan notifikasi" }) View.VISIBLE else View.GONE
        childGalleryPanel.visibility = if (issues.any { it == "Akses foto dan video" }) View.VISIBLE else View.GONE

        statusText.text = if (needsSetup) {
            "Beberapa izin untuk fitur yang diaktifkan perlu diperiksa: " + issues.joinToString(", ") + ". Ketuk kartu terkait untuk membuka pengaturan."
        } else {
            "Semua izin untuk fitur yang digunakan sudah aktif. Pengaturan perangkat dikelola dari HP orang tua."
        }
        statusText.visibility = View.VISIBLE
        updateButton.visibility = View.VISIBLE
        updateInfoText.visibility = View.VISIBLE
        updateButton.text = "PERIKSA PEMBARUAN APLIKASI"
        actionButtonsRow.visibility = View.GONE
        parentButton.visibility = View.GONE
        childButton.visibility = View.GONE
        selectedChildButton.visibility = View.GONE
        codeText.visibility = View.GONE
        childAudioActivityText.visibility = View.GONE
        startButton.visibility = View.GONE
        stopButton.visibility = View.GONE
        sendMp3Button.visibility = View.GONE
        playMp3Button.visibility = View.GONE
        adminWhatsAppButton.visibility = View.GONE
        childrenContainer.visibility = View.GONE
        addChildButton.visibility = View.GONE
        cancelPairingButton.visibility = View.GONE
        cameraLiveButton.visibility = View.GONE
        updateCameraStandbyButton()
    }

    private fun connectFirebase() {
        if (auth.currentUser != null) {
            refreshFcmToken()
            statusText.text = "Firebase terhubung!"
            restoreSavedPairing()
            return
        }

        statusText.text = "Menghubungkan ke Firebase..."

        auth.signInAnonymously()
            .addOnCompleteListener(this) { task ->

                if (task.isSuccessful) {
                    refreshFcmToken()
                    statusText.text = "Firebase terhubung!"
                    restoreSavedPairing()

                    Toast.makeText(
                        this,
                        "Identitas perangkat berhasil dibuat",
                        Toast.LENGTH_SHORT
                    ).show()

                } else {
                    statusText.text = "Gagal terhubung ke Firebase"

                    Toast.makeText(
                        this,
                        task.exception?.localizedMessage ?: "Koneksi gagal",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
    }

    private fun refreshFcmToken() {
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token ->
                getSharedPreferences("parent_audio", MODE_PRIVATE)
                    .edit()
                    .putString("fcm_token", token)
                    .apply()
                statusText.text = "Firebase terhubung • notifikasi siap"
                val prefs = getSharedPreferences("parent_audio", MODE_PRIVATE)
                val savedRole = prefs.getString("pair_role", null)
                if (savedRole == "parent") {
                    loadSavedChildren()
                    childNames.keys.forEach { registerDeviceToken(it, "parent", token) }
                } else {
                    val savedCode = prefs.getString("pair_code", null)
                    if (!savedCode.isNullOrBlank() && savedRole == "child") {
                        registerDeviceToken(savedCode, savedRole, token)
                    }
                }
            }
            .addOnFailureListener {
                // Keep the app usable if token retrieval is temporarily unavailable.
            }
    }

    private inner class SantetHpDialogBuilder : AlertDialog.Builder(this@MainActivity) {
        override fun create(): AlertDialog {
            val dialog = super.create()
            styleDialogWhenLaidOut(dialog)
            return dialog
        }

        override fun show(): AlertDialog {
            val dialog = super.show()
            styleDialogWhenLaidOut(dialog)
            return dialog
        }
    }

    private fun styleDialogWhenLaidOut(dialog: AlertDialog) {
        dialog.window?.setBackgroundDrawableResource(R.drawable.bg_santethp_popup)
        val decor = dialog.window?.decorView ?: return
        val observer = decor.viewTreeObserver
        observer.addOnGlobalLayoutListener(object : android.view.ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                val message = dialog.findViewById<TextView>(android.R.id.message)
                if (message != null) {
                    message.setTextColor(Color.rgb(35, 55, 91))
                    message.setPadding(dp(12), dp(11), dp(12), dp(11))
                    message.background = GradientDrawable(
                        GradientDrawable.Orientation.TL_BR,
                        intArrayOf(Color.rgb(247, 250, 255), Color.rgb(235, 243, 255))
                    ).apply {
                        cornerRadius = dp(13).toFloat()
                        setStroke(dp(1), Color.rgb(177, 202, 238))
                    }
                    val params = message.layoutParams as? ViewGroup.MarginLayoutParams
                    params?.setMargins(dp(8), dp(5), dp(8), dp(5))
                    if (params != null) message.layoutParams = params
                }
                if (observer.isAlive) observer.removeOnGlobalLayoutListener(this)
            }
        })
    }

    private fun popupInfoCards(sections: List<Pair<String, String>>): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(2), dp(2), dp(2), dp(2))
            sections.forEachIndexed { index, section ->
                val card = LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(13), dp(11), dp(13), dp(11))
                    background = GradientDrawable(
                        GradientDrawable.Orientation.TL_BR,
                        intArrayOf(Color.rgb(249, 252, 255), Color.rgb(239, 246, 255))
                    ).apply {
                        cornerRadius = dp(13).toFloat()
                        setStroke(dp(1), Color.rgb(190, 210, 238))
                    }
                    val heading = TextView(this@MainActivity).apply {
                        text = section.first
                        setTextColor(Color.rgb(25, 91, 67))
                        setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12f)
                        setTypeface(typeface, android.graphics.Typeface.BOLD)
                    }
                    val body = TextView(this@MainActivity).apply {
                        text = section.second
                        setTextColor(Color.rgb(35, 55, 91))
                        setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14f)
                        setLineSpacing(dp(2).toFloat(), 1f)
                        setPadding(0, dp(5), 0, 0)
                    }
                    addView(heading, LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ))
                    addView(body, LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ))
                }
                addView(card, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    if (index < sections.lastIndex) bottomMargin = dp(9)
                })
            }
        }

    private fun popupContentCard(content: View): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(12), dp(10), dp(12), dp(10))
        background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(Color.rgb(248, 251, 255), Color.rgb(232, 241, 255))
        ).apply {
            cornerRadius = dp(16).toFloat()
            setStroke(dp(1), Color.rgb(190, 210, 238))
        }
        addView(content, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun dp(value: Float): Float =
        value * resources.displayMetrics.density

    private fun blendWithWhite(color: Int, amount: Float): Int {
        val inverse = 1f - amount
        return Color.rgb(
            (Color.red(color) * inverse + 255 * amount).toInt(),
            (Color.green(color) * inverse + 255 * amount).toInt(),
            (Color.blue(color) * inverse + 255 * amount).toInt()
        )
    }

    private inner class ChildAvatarView : View(this@MainActivity) {
        private val brush = Paint(Paint.ANTI_ALIAS_FLAG)

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val side = minOf(width, height).toFloat()
            if (side <= 0f) return
            val cx = width / 2f
            val cy = height / 2f
            val scale = side / 100f

            canvas.save()
            canvas.translate(cx - 50f * scale, cy - 50f * scale)
            canvas.scale(scale, scale)

            brush.style = Paint.Style.FILL
            brush.color = Color.rgb(231, 239, 255)
            canvas.drawCircle(50f, 50f, 49f, brush)

            brush.color = Color.rgb(55, 91, 190)
            canvas.drawRoundRect(RectF(13f, 61f, 87f, 105f), 25f, 25f, brush)

            brush.color = Color.rgb(225, 171, 133)
            canvas.drawRoundRect(RectF(42f, 53f, 58f, 72f), 7f, 7f, brush)
            canvas.drawCircle(50f, 39f, 23f, brush)

            brush.color = Color.rgb(48, 43, 47)
            val hair = Path().apply {
                moveTo(27f, 39f)
                cubicTo(24f, 12f, 40f, 8f, 53f, 14f)
                cubicTo(70f, 13f, 79f, 28f, 73f, 43f)
                lineTo(67f, 34f)
                cubicTo(55f, 27f, 43f, 28f, 32f, 38f)
                close()
            }
            canvas.drawPath(hair, brush)

            brush.color = Color.rgb(55, 45, 42)
            canvas.drawCircle(42f, 43f, 1.8f, brush)
            canvas.drawCircle(58f, 43f, 1.8f, brush)
            brush.style = Paint.Style.STROKE
            brush.strokeWidth = 2f
            brush.strokeCap = Paint.Cap.ROUND
            canvas.drawArc(RectF(43f, 46f, 57f, 58f), 15f, 150f, false, brush)
            brush.style = Paint.Style.FILL
            canvas.restore()
        }
    }

    private fun roundedPanel(color: Int, radiusDp: Int): GradientDrawable =
        GradientDrawable().apply {
            // Lapisan kaca transparan dengan garis putih seperti widget referensi.
            val glassColor = when (Color.alpha(color)) {
                255 -> Color.argb(
                    if (color == Color.WHITE) 174 else 190,
                    Color.red(color), Color.green(color), Color.blue(color)
                )
                else -> color
            }
            setColor(glassColor)
            cornerRadius = dp(radiusDp).toFloat()
            setStroke(dp(1), Color.argb(185, 255, 255, 255))
        }

    /**
     * Lightweight animated audio-style visualizer for the parent dashboard.
     * It animates locally and only while attached to the visible window.
     */
    private inner class AudioVisualizerView : View(this@MainActivity) {
        private val panelPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(32, 55, 92)
            textSize = dp(12.5f).toFloat()
            typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
        }
        private val statusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(104, 117, 140)
            textSize = dp(11f).toFloat()
            typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
        }
        private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
        private var statusLabel = "Tidak sedang memutar audio"
        private var audioActive = false
        private var framePosted = false

        fun setActivityState(label: String, active: Boolean) {
            if (statusLabel == label && audioActive == active) return
            statusLabel = label
            audioActive = active
            contentDescription = "Aktivitas audio anak: $label"
            invalidate()
            updateAnimation()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0f || h <= 0f) return
            val radius = dp(16).toFloat()
            panelPaint.style = Paint.Style.FILL
            panelPaint.color = Color.argb(248, 255, 255, 255)
            canvas.drawRoundRect(0f, 0f, w, h, radius, radius, panelPaint)
            panelPaint.style = Paint.Style.STROKE
            panelPaint.strokeWidth = dp(1).toFloat()
            panelPaint.color = Color.rgb(218, 229, 246)
            canvas.drawRoundRect(dp(0.5f), dp(0.5f), w - dp(0.5f), h - dp(0.5f), radius, radius, panelPaint)

            // Musik divisualisasikan hanya dengan batang equalizer; teks status
            // tetap dipakai untuk aksesibilitas, tetapi tidak ditampilkan.
            val barCount = 15
            val gap = dp(3).toFloat()
            val barWidth = dp(3.5f).toFloat()
            val totalBarsWidth = barCount * barWidth + (barCount - 1) * gap
            // Center the equalizer horizontally within the entire panel.
            val startX = (w - totalBarsWidth) * 0.5f
            val centerY = h * 0.5f
            val now = android.os.SystemClock.uptimeMillis()
            for (i in 0 until barCount) {
                val wave = if (audioActive) {
                    (kotlin.math.sin(now / 175.0 + i * 0.72) * 0.5 + 0.5).toFloat()
                } else {
                    // Keep a gentle equalizer silhouette while idle, not a row of loading dots.
                    listOf(0.18f, 0.30f, 0.48f, 0.34f, 0.66f, 0.43f, 0.24f, 0.58f, 0.78f, 0.46f, 0.29f, 0.62f, 0.40f, 0.22f, 0.50f)[i]
                }
                val barHeight = dp(5).toFloat() + wave * dp(if (audioActive) 31 else 14)
                val x = startX + i * (barWidth + gap)
                barPaint.color = if (audioActive) {
                    // Active playback uses a vivid, animated green equalizer.
                    Color.rgb(28, 150 + (wave * 75).toInt(), 55 + (wave * 22).toInt())
                } else {
                    Color.rgb(174, 198, 228)
                }
                canvas.drawRoundRect(
                    x, centerY - barHeight / 2f, x + barWidth, centerY + barHeight / 2f,
                    barWidth, barWidth, barPaint
                )
            }
        }

        private val animationTick = object : Runnable {
            override fun run() {
                framePosted = false
                if (!audioActive || !isAttachedToWindow || visibility != View.VISIBLE) return
                invalidate()
                postAnimationFrame()
            }
        }

        private fun postAnimationFrame() {
            if (framePosted || !audioActive || !isAttachedToWindow || visibility != View.VISIBLE) return
            framePosted = true
            postDelayed(animationTick, 45L)
        }

        private fun updateAnimation() {
            if (audioActive) postAnimationFrame()
            else {
                removeCallbacks(animationTick)
                framePosted = false
            }
        }

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            updateAnimation()
        }

        override fun onDetachedFromWindow() {
            removeCallbacks(animationTick)
            framePosted = false
            super.onDetachedFromWindow()
        }

        override fun onVisibilityChanged(changedView: View, visibility: Int) {
            super.onVisibilityChanged(changedView, visibility)
            if (visibility == View.VISIBLE) updateAnimation()
            else {
                removeCallbacks(animationTick)
                framePosted = false
            }
        }
    }

    private inner class StatusGlyphDrawable(private val isBattery: Boolean) : android.graphics.drawable.Drawable() {
        private val glyphPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        var levelPercent: Int = -1
            set(value) { field = value.coerceIn(-1, 100); invalidateSelf() }
        var isCharging: Boolean = false
            set(value) { field = value; invalidateSelf() }
        var mode: String = "wifi"
            set(value) { field = value; invalidateSelf() }
        override fun getIntrinsicWidth(): Int = dp(18)
        override fun getIntrinsicHeight(): Int = dp(18)
        override fun draw(canvas: Canvas) {
            val b = bounds
            val w = b.width().toFloat()
            val h = b.height().toFloat()
            if (w <= 0f || h <= 0f) return
            val cx = b.exactCenterX()
            val cy = b.exactCenterY()
            glyphPaint.color = Color.rgb(48, 111, 194)
            glyphPaint.style = Paint.Style.STROKE
            glyphPaint.strokeWidth = dp(1.6f)
            glyphPaint.strokeCap = Paint.Cap.ROUND
            glyphPaint.strokeJoin = Paint.Join.ROUND
            glyphPaint.colorFilter = null
            if (isBattery) {
                val left = cx - w * 0.34f
                val top = cy - h * 0.25f
                val right = cx + w * 0.27f
                val bottom = cy + h * 0.25f
                canvas.drawRoundRect(left, top, right, bottom, dp(2f), dp(2f), glyphPaint)
                glyphPaint.style = Paint.Style.FILL
                canvas.drawRoundRect(right, cy - h * 0.09f, right + w * 0.10f, cy + h * 0.09f, dp(1f), dp(1f), glyphPaint)
                if (levelPercent >= 0) {
                    glyphPaint.color = when { levelPercent <= 15 -> Color.rgb(218, 76, 76); levelPercent <= 30 -> Color.rgb(221, 155, 49); else -> Color.rgb(38, 159, 105) }
                    val inset = dp(1.5f)
                    val fillWidth = (right - left - inset * 2) * levelPercent / 100f
                    if (fillWidth > 0f) canvas.drawRoundRect(left + inset, top + inset, left + inset + fillWidth, bottom - inset, dp(1f), dp(1f), glyphPaint)
                }
                if (isCharging) {
                    val bolt = Path().apply {
                        moveTo(cx + w * 0.03f, cy - h * 0.19f)
                        lineTo(cx - w * 0.13f, cy + h * 0.02f)
                        lineTo(cx - w * 0.01f, cy + h * 0.02f)
                        lineTo(cx - w * 0.06f, cy + h * 0.19f)
                        lineTo(cx + w * 0.14f, cy - h * 0.04f)
                        lineTo(cx + w * 0.03f, cy - h * 0.04f)
                        close()
                    }
                    glyphPaint.color = Color.rgb(255, 255, 255)
                    glyphPaint.style = Paint.Style.FILL
                    canvas.drawPath(bolt, glyphPaint)
                    glyphPaint.color = Color.rgb(24, 155, 98)
                    glyphPaint.style = Paint.Style.STROKE
                    glyphPaint.strokeWidth = dp(0.8f)
                    canvas.drawPath(bolt, glyphPaint)
                }
            } else when (mode) {
                "cellular" -> {
                    glyphPaint.style = Paint.Style.FILL
                    val barW = w * 0.13f
                    val gap = w * 0.055f
                    for (i in 0..3) {
                        val barH = h * (0.25f + i * 0.17f)
                        val x = cx - (barW * 2f + gap * 1.5f) + i * (barW + gap)
                        canvas.drawRoundRect(x, cy + h * 0.32f - barH, x + barW, cy + h * 0.32f, dp(1f), dp(1f), glyphPaint)
                    }
                }
                "ethernet" -> {
                    val p = Path().apply { moveTo(cx - w * 0.24f, cy - h * 0.10f); lineTo(cx + w * 0.24f, cy - h * 0.10f); lineTo(cx + w * 0.24f, cy + h * 0.12f); lineTo(cx - w * 0.24f, cy + h * 0.12f); close() }
                    canvas.drawPath(p, glyphPaint)
                    canvas.drawLine(cx - w * 0.12f, cy - h * 0.32f, cx - w * 0.12f, cy - h * 0.10f, glyphPaint)
                    canvas.drawLine(cx + w * 0.12f, cy - h * 0.32f, cx + w * 0.12f, cy - h * 0.10f, glyphPaint)
                    canvas.drawLine(cx, cy + h * 0.12f, cx, cy + h * 0.31f, glyphPaint)
                }
                else -> {
                    for (r in listOf(0.20f, 0.36f, 0.51f)) {
                        val oval = android.graphics.RectF(cx - w * r, cy - h * r, cx + w * r, cy + h * r)
                        canvas.drawArc(oval, 225f, 90f, false, glyphPaint)
                    }
                    glyphPaint.style = Paint.Style.FILL
                    canvas.drawCircle(cx, cy + h * 0.28f, dp(1.1f), glyphPaint)
                }
            }
        }
        override fun setAlpha(alpha: Int) { glyphPaint.alpha = alpha; invalidateSelf() }
        override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) { glyphPaint.colorFilter = colorFilter; invalidateSelf() }
        @Deprecated("Deprecated in Android graphics")
        override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
    }

    private inner class WhatsAppBrandIconView : View(this@MainActivity) {
        private val iconPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)

        override fun onDraw(canvas: android.graphics.Canvas) {
            super.onDraw(canvas)
            val side = minOf(width, height).toFloat()
            val cx = width / 2f
            val cy = height / 2f
            val radius = side * 0.47f

            iconPaint.style = android.graphics.Paint.Style.FILL
            iconPaint.color = Color.rgb(37, 211, 102)
            canvas.drawCircle(cx, cy, radius, iconPaint)

            // White WhatsApp-style chat bubble with a handset silhouette.
            iconPaint.color = Color.WHITE
            iconPaint.style = android.graphics.Paint.Style.STROKE
            iconPaint.strokeWidth = side * 0.085f
            iconPaint.strokeCap = android.graphics.Paint.Cap.ROUND
            iconPaint.strokeJoin = android.graphics.Paint.Join.ROUND
            val bubble = android.graphics.Path().apply {
                moveTo(cx - side * 0.25f, cy - side * 0.20f)
                cubicTo(cx - side * 0.39f, cy - side * 0.02f, cx - side * 0.30f, cy + side * 0.22f, cx - side * 0.10f, cy + side * 0.28f)
                cubicTo(cx + side * 0.08f, cy + side * 0.35f, cx + side * 0.30f, cy + side * 0.19f, cx + side * 0.29f, cy - side * 0.04f)
                cubicTo(cx + side * 0.28f, cy - side * 0.28f, cx + side * 0.02f, cy - side * 0.38f, cx - side * 0.18f, cy - side * 0.27f)
                close()
            }
            canvas.drawPath(bubble, iconPaint)

            iconPaint.style = android.graphics.Paint.Style.STROKE
            iconPaint.strokeWidth = side * 0.105f
            val handset = android.graphics.Path().apply {
                moveTo(cx - side * 0.15f, cy - side * 0.14f)
                cubicTo(cx - side * 0.20f, cy - side * 0.05f, cx - side * 0.02f, cy + side * 0.15f, cx + side * 0.10f, cy + side * 0.12f)
                lineTo(cx + side * 0.17f, cy + side * 0.04f)
            }
            canvas.drawPath(handset, iconPaint)
        }
    }

    private fun createActionButton(icon: String, label: String, accent: Int): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(5), dp(8), dp(5))
            minimumHeight = dp(58)
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = dp(15).toFloat()
                setStroke(
                    dp(1),
                    if (label == "HENTIKAN LIVE") Color.rgb(239, 211, 212)
                    else Color.rgb(207, 226, 214)
                )
            }
            elevation = dp(2).toFloat()
            isClickable = true
            isFocusable = true
            contentDescription = label
        }
        val iconView: View = if (label == "CHAT ADMIN") {
            WhatsAppBrandIconView().apply {
                layoutParams = LinearLayout.LayoutParams(dp(31), dp(31)).apply {
                    marginEnd = dp(7)
                }
            }
        } else {
            TextView(this).apply {
                text = icon
                textSize = if (label == "SERVER") 20f else 19f
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                val tileAccent = if (label == "HENTIKAN LIVE") Color.rgb(177, 55, 58) else Color.rgb(24, 105, 77)
                background = GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    intArrayOf(blendWithWhite(tileAccent, 0.12f), tileAccent, blendWithWhite(tileAccent, 0.28f))
                ).apply {
                    cornerRadius = dp(11).toFloat()
                    setStroke(dp(1), Color.argb(105, 255, 255, 255))
                }
                layoutParams = LinearLayout.LayoutParams(dp(32), dp(32)).apply {
                    marginEnd = dp(7)
                }
            }
        }
        val helper = when (label) {
            "BICARA LIVE" -> "Dengarkan suara sekitar"
            "HENTIKAN LIVE" -> "Akhiri sesi audio"
            "PUTAR MP3" -> "Kirim audio ke perangkat"
            "KAMERA LIVE" -> "Lihat kamera perangkat"
            "BLOKIR APLIKASI" -> "Atur aplikasi HP anak"
            "GALLERY" -> "Galeri dan backup"
            "SERVER" -> "Server utama dan cadangan"
            "MAPS" -> "Lokasi dan geo-fence"
            "CHAT ADMIN" -> "Bantuan dan dukungan"
            else -> "Kontrol perangkat"
        }
        val textStack = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(1), 0, dp(1))
        }
        textStack.addView(TextView(this).apply {
            text = label
            textSize = 11.5f
            setTypeface(null, android.graphics.Typeface.BOLD)
            letterSpacing = 0.012f
            setTextColor(Color.rgb(28, 48, 42))
            maxLines = 2
            setLineSpacing(dp(1).toFloat(), 1f)
        })
        textStack.addView(TextView(this).apply {
            text = helper
            textSize = 9.5f
            setTextColor(Color.rgb(91, 111, 101))
            setPadding(0, dp(2), 0, 0)
            maxLines = 2
            setLineSpacing(dp(1).toFloat(), 1f)
        })
        card.addView(iconView)
        card.addView(textStack, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        return card
    }

    private fun createButton(label: String): Button {
        return Button(this).apply {
            text = label
            textSize = 14f
            isAllCaps = false
            setTextColor(Color.WHITE)
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(75, 111, 229), Color.rgb(43, 78, 190), Color.rgb(92, 133, 235))
            ).apply {
                cornerRadius = dp(16).toFloat()
                setStroke(dp(1), Color.argb(110, 255, 255, 255))
            }
            elevation = dp(2).toFloat()
            minHeight = dp(52)
            stateListAnimator = null
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, dp(7), 0, dp(7))
            }
        }
    }

    private fun savePairing(code: String, role: String) {
        val editor = getSharedPreferences("parent_audio", MODE_PRIVATE).edit()
            .putString("pair_role", role)
        if (role == "child") editor.putString("pair_code", code)
        editor.apply()
        if (role == "child") {
            ChildDeviceStatusWorker.schedule(this)
        } else {
            ChildDeviceStatusWorker.cancel(this)
        }

        val token = getSharedPreferences("parent_audio", MODE_PRIVATE)
            .getString("fcm_token", null)
        if (!token.isNullOrBlank()) registerDeviceToken(code, role, token)
    }

    private fun loadSavedChildren() {
        val prefs = getSharedPreferences("parent_audio", MODE_PRIVATE)
        childNames.clear()
        val stored = prefs.getString("parent_children", null)
        if (!stored.isNullOrBlank()) {
            try {
                val json = JSONObject(stored)
                json.keys().forEach { code -> childNames[code] = json.optString(code, "Anak") }
            } catch (_: Exception) { }
        }
        // Migrate the single-child format used by earlier SantetHP builds.
        val legacyCode = prefs.getString("pair_code", null)
        if (childNames.isEmpty() && !legacyCode.isNullOrBlank()) {
            childNames[legacyCode] = "Anak 1"
            persistChildren()
        }
        // Restore the parent's last selected child across app restarts.
        val savedSelectedCode = prefs.getString("selected_child_code", null)
        selectedChildCode = savedSelectedCode?.takeIf { it in childNames.keys }
            ?: childNames.keys.firstOrNull()
        if (selectedChildCode != savedSelectedCode) {
            prefs.edit().putString("selected_child_code", selectedChildCode).apply()
        }
        childNames.keys.forEach { code ->
            childConnected.putIfAbsent(code, false)
            listenForChild(code)
        }
        renderChildrenList()
    }

    private fun persistChildren() {
        val json = JSONObject()
        childNames.forEach { (code, name) -> json.put(code, name) }
        getSharedPreferences("parent_audio", MODE_PRIVATE).edit()
            .putString("parent_children", json.toString()).apply()
    }

    private fun addParentChild(code: String) {
        val index = childNames.size + 1
        childNames[code] = "Anak $index"
        childConnected[code] = false
        selectedChildCode = code
        persistChildren()
        getSharedPreferences("parent_audio", MODE_PRIVATE).edit()
            .putString("selected_child_code", code).apply()
        renderChildrenList()
    }

    private fun childPresence(code: String): Pair<String, Boolean> {
        val statusAt = childSnapshots[code]?.getTimestamp("deviceStatusAt")
            ?: return "○ STATUS BELUM TERSEDIA" to false
        val age = System.currentTimeMillis() - statusAt.toDate().time
        val recentlySeen = age in -60_000L..35 * 60 * 1000L
        return if (recentlySeen) "● ONLINE" to true else "○ OFFLINE" to false
    }

    private fun childLastSeen(code: String): String {
        val statusAt = childSnapshots[code]?.getTimestamp("deviceStatusAt")
            ?: return "Belum ada laporan perangkat"
        val age = System.currentTimeMillis() - statusAt.toDate().time
        if (age < -60_000L) return "Waktu perangkat belum sinkron"
        if (age < 60_000L) return "Terakhir terlihat: baru saja"
        val minutes = age / 60_000L
        if (minutes < 60L) return "Terakhir terlihat: $minutes menit lalu"
        val hours = minutes / 60L
        if (hours < 24L) return "Terakhir terlihat: $hours jam lalu"
        val days = hours / 24L
        return "Terakhir terlihat: $days hari lalu"
    }

    private fun updateSelectedChildButton() {
        val name = selectedChildCode?.let { childNames[it] }
        val arrow = if (isChildrenPanelExpanded) "▴" else "▾"
        val presence = selectedChildCode?.let { childPresence(it) }
            ?: ("○ STATUS BELUM TERSEDIA" to false)
        val connected = presence.second
        val childLabel = if (name.isNullOrBlank()) "Pilih perangkat anak" else name
        val connectionLabel = presence.first
        val childDisplay = android.text.SpannableString("🧒  $childLabel   $connectionLabel   $arrow")
        val nameStart = 4
        val nameEnd = (nameStart + childLabel.length).coerceAtMost(childDisplay.length)
        childDisplay.setSpan(
            android.text.style.ForegroundColorSpan(Color.rgb(31, 83, 56)),
            nameStart, nameEnd, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        childDisplay.setSpan(
            android.text.style.StyleSpan(android.graphics.Typeface.BOLD),
            nameStart, nameEnd, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        val stateStart = childDisplay.indexOf(connectionLabel)
        if (stateStart >= 0) {
            childDisplay.setSpan(
                android.text.style.ForegroundColorSpan(
                    if (connected) Color.rgb(24, 132, 83) else Color.rgb(112, 122, 143)
                ),
                stateStart, stateStart + connectionLabel.length,
                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            childDisplay.setSpan(
                android.text.style.StyleSpan(android.graphics.Typeface.BOLD),
                stateStart, stateStart + connectionLabel.length,
                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        selectedChildButton.text = childDisplay
        val selectedSnapshot = selectedChildCode?.let { childSnapshots[it] }
        if (selectedSnapshot == null) {
            childBatteryGlyph.levelPercent = -1
            childNetworkGlyph.mode = "wifi"
            childQuickStatusText.text = "--    ·    Menunggu jaringan"
        } else {
            val battery = selectedSnapshot.getLong("deviceBatteryPercent")?.toInt() ?: -1
            val network = selectedSnapshot.getString("deviceNetworkType")
                ?.trim()?.lowercase(java.util.Locale.ROOT).orEmpty()
            val batteryLabel = if (battery in 0..100) "$battery%" else "--"
            childBatteryGlyph.levelPercent = battery
            childBatteryGlyph.isCharging = selectedSnapshot.getBoolean("deviceCharging") == true
            val networkStatus = when {
                network.contains("wi-fi") || network.contains("wifi") -> {
                    childNetworkGlyph.mode = "wifi"
                    "Wi-Fi"
                }
                network.contains("data seluler") || network.contains("seluler") ||
                    network.contains("cellular") || network.contains("mobile") -> {
                    val operator = selectedSnapshot.getString("deviceOperatorName")
                        ?.trim()?.takeIf {
                            it.isNotBlank() &&
                                !it.equals("Tidak terdeteksi / tanpa SIM", ignoreCase = true) &&
                                !it.equals("Tidak tersedia", ignoreCase = true)
                        }
                    childNetworkGlyph.mode = "cellular"
                    val compactOperator = operator?.let {
                        if (it.length > 14) "${it.take(13)}…" else it
                    }
                    if (compactOperator.isNullOrBlank()) "Data seluler" else compactOperator
                }
                network.contains("ethernet") -> {
                    childNetworkGlyph.mode = "ethernet"
                    "Ethernet"
                }
                else -> {
                    childNetworkGlyph.mode = "wifi"
                    "Jaringan"
                }
            }
            val statusLine = if (networkStatus.isBlank()) {
                batteryLabel
            } else {
                "$batteryLabel    ·    ◇  $networkStatus"
            }
            val statusDisplay = android.text.SpannableString(statusLine)
            if (networkStatus.isNotBlank()) {
                val glyphIndex = statusLine.indexOf('◇')
                if (glyphIndex >= 0) {
                    childNetworkGlyph.setBounds(0, 0, dp(16), dp(16))
                    statusDisplay.setSpan(
                        android.text.style.ImageSpan(childNetworkGlyph, android.text.style.ImageSpan.ALIGN_CENTER),
                        glyphIndex, glyphIndex + 1,
                        android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
            }
            childQuickStatusText.text = statusDisplay
        }

    }

    private fun toggleChildrenPanel() {
        isChildrenPanelExpanded = !isChildrenPanelExpanded
        childrenContainer.visibility = if (isChildrenPanelExpanded) View.VISIBLE else View.GONE
        updateSelectedChildButton()
    }

    private fun renderChildrenList() {
        if (!::childrenContainer.isInitialized) return
        childrenContainer.removeAllViews()
        childrenContainer.orientation = LinearLayout.VERTICAL
        childrenContainer.setPadding(dp(6), dp(6), dp(6), dp(6))
        childrenContainer.background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(Color.WHITE, Color.rgb(244, 250, 246))
        ).apply {
            cornerRadius = dp(18).toFloat()
            setStroke(dp(1), Color.rgb(207, 226, 214))
        }
        childrenContainer.elevation = dp(2).toFloat()

        if (childNames.isEmpty()) {
            val empty = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(14), dp(16), dp(14), dp(16))
                background = GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    intArrayOf(Color.WHITE, Color.rgb(245, 250, 247))
                ).apply {
                    cornerRadius = dp(16).toFloat()
                    setStroke(dp(1), Color.rgb(216, 232, 221))
                }
            }
            empty.addView(TextView(this).apply {
                text = "📱"
                textSize = 30f
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, dp(7))
                contentDescription = "Ilustrasi perangkat anak"
            })
            empty.addView(TextView(this).apply {
                text = "Belum ada perangkat anak"
                textSize = 13f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(Color.rgb(36, 76, 56))
                gravity = Gravity.CENTER
            })
            empty.addView(TextView(this).apply {
                text = "Tekan tombol + untuk mulai menghubungkan HP anak."
                textSize = 11f
                setLineSpacing(dp(2).toFloat(), 1f)
                setTextColor(Color.rgb(104, 123, 111))
                setPadding(0, dp(5), 0, 0)
                gravity = Gravity.CENTER
            })
            childrenContainer.addView(empty)
        }

        childNames.forEach { (code, name) ->
            val presence = childPresence(code)
            val connected = presence.second
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(8), dp(6), dp(6), dp(6))
                background = GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    if (selectedChildCode == code)
                        intArrayOf(Color.rgb(231, 245, 236), Color.rgb(246, 251, 247))
                    else
                        intArrayOf(Color.WHITE, Color.rgb(248, 251, 249))
                ).apply {
                    cornerRadius = dp(17).toFloat()
                    setStroke(
                        dp(if (selectedChildCode == code) 1.5f.toInt() else 1),
                        if (selectedChildCode == code) Color.rgb(105, 161, 134)
                        else Color.rgb(218, 232, 222)
                    )
                }
                isClickable = true
                isFocusable = true
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, dp(3), 0, dp(3)) }
            }
            val phoneIcon = ChildAvatarView().apply {
                layoutParams = LinearLayout.LayoutParams(dp(40), dp(40)).apply {
                    rightMargin = dp(8)
                }
                contentDescription = "Ilustrasi profil perangkat anak"
            }
            row.addView(phoneIcon)
            val details = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            details.addView(TextView(this).apply {
                text = name
                textSize = 13.5f
                setTypeface(null, android.graphics.Typeface.BOLD)
                letterSpacing = 0.005f
                setTextColor(Color.rgb(34, 62, 46))
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            })
            details.addView(TextView(this).apply {
                text = presence.first
                textSize = 9f
                setTypeface(null, android.graphics.Typeface.BOLD)
                letterSpacing = 0.005f
                setPadding(dp(7), dp(3), dp(7), dp(3))
                setTextColor(if (connected) Color.rgb(24, 128, 82) else Color.rgb(105, 116, 137))
                background = GradientDrawable().apply {
                    setColor(if (connected) Color.rgb(226, 247, 237) else Color.rgb(239, 243, 249))
                    cornerRadius = dp(8).toFloat()
                }
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            })
            details.addView(TextView(this).apply {
                text = childLastSeen(code)
                textSize = 9.5f
                setTextColor(Color.rgb(111, 128, 116))
                setPadding(dp(1), dp(3), 0, 0)
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            })
            row.addView(details)
            if (selectedChildCode == code) row.addView(TextView(this).apply {
                text = "✓"
                textSize = 16f
                setTypeface(null, android.graphics.Typeface.BOLD)
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                setPadding(dp(6), dp(3), dp(6), dp(3))
                background = GradientDrawable().apply {
                    setColor(Color.rgb(25, 91, 67))
                    cornerRadius = dp(20).toFloat()
                }
            })
            val removeButton = Button(this).apply {
                text = "Lepaskan"
                textSize = 10.5f
                isAllCaps = false
                minHeight = dp(32)
                setPadding(dp(7), 0, dp(7), 0)
                setTextColor(Color.rgb(157, 54, 65))
                setTypeface(null, android.graphics.Typeface.BOLD)
                background = GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    intArrayOf(Color.rgb(255, 250, 250), Color.rgb(255, 241, 242))
                ).apply {
                    cornerRadius = dp(11).toFloat()
                    setStroke(dp(1), Color.rgb(232, 190, 195))
                }
                elevation = dp(1).toFloat()
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, dp(32)
                ).apply { leftMargin = dp(5) }
                setOnClickListener { promptRemoveChild(code) }
            }
            row.addView(removeButton)
            row.setOnClickListener { selectChild(code) }
            row.setOnLongClickListener { promptRenameChild(code); true }
            childrenContainer.addView(row)
        }

        val addRow = TextView(this).apply {
            text = "＋    Tambahkan perangkat anak"
            textSize = 12f
            setTypeface(null, android.graphics.Typeface.BOLD)
            letterSpacing = 0.005f
            setTextColor(Color.rgb(35, 82, 151))
            gravity = Gravity.CENTER
            includeFontPadding = false
            setPadding(dp(14), dp(11), dp(14), dp(11))
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.rgb(242, 247, 255), Color.rgb(231, 240, 255))
            ).apply {
                cornerRadius = dp(14).toFloat()
                setStroke(dp(1), Color.rgb(194, 214, 244))
            }
            elevation = dp(1).toFloat()
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp(2), dp(6), dp(2), dp(2)) }
            isClickable = true
            isFocusable = true
        }
        addRow.setOnClickListener { createPairingCode() }
        childrenContainer.addView(addRow)
        updateSelectedChildButton()
    }

    private fun promptRenameChild(code: String) {
        val input = EditText(this).apply {
            setText(childNames[code] ?: "Anak")
            setSelection(text.length)
            hint = "Nama anak"
            isSingleLine = true
        }
        SantetHpDialogBuilder()
            .setTitle("Beri nama anak")
            .setView(popupContentCard(input))
            .setNegativeButton("Batal", null)
            .setPositiveButton("Simpan") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    childNames[code] = name
                    persistChildren()
                    renderChildrenList()
                }
            }.show()
    }

    private fun promptRemoveChild(code: String) {
        val childName = childNames[code] ?: "Anak"
        SantetHpDialogBuilder()
            .setTitle("Lepaskan hubungan anak?")
            .setMessage("Hubungan dengan $childName akan dihapus. Perangkat anak lain tidak akan terpengaruh.")
            .setNegativeButton("Batal", null)
            .setPositiveButton("Ya, lepaskan") { _, _ ->
                statusText.text = "Melepaskan hubungan dengan $childName..."
                db.collection("pairings").document(code)
                    .delete()
                    .addOnSuccessListener {
                        childListeners.remove(code)?.remove()
                        childNames.remove(code)
                        childConnected.remove(code)
                        renamePromptedCodes.remove(code)
                        if (pendingPairingCode == code) pendingPairingCode = null
                        persistChildren()

                        val wasSelected = selectedChildCode == code
                        if (wasSelected && activeRoomCode == code) {
                            activityScope.launch {
                                try {
                                    room?.localParticipant?.setMicrophoneEnabled(false)
                                    room?.disconnect()
                                    room?.release()
                                } catch (_: Exception) { }
                                room = null
                                activeRoomCode = null
                                mp3TransferManager = null
                                isMicrophoneActive = false
                            }
                        }

                        if (wasSelected) selectedChildCode = childNames.keys.firstOrNull()
                        if (childNames.isEmpty()) {
                            selectedChildCode = null
                            getSharedPreferences("parent_audio", MODE_PRIVATE).edit()
                                .putString("pair_role", "parent")
                                .remove("pair_code")
                                .putString("parent_children", "{}")
                                .apply()
                        }
                        showParentDashboard()
                        val nextCode = selectedChildCode
                        if (nextCode != null && childConnected[nextCode] == true) {
                            selectChild(nextCode)
                        } else {
                            statusText.text = if (childNames.isEmpty()) {
                                "Hubungan anak berhasil dilepaskan. Belum ada perangkat anak yang terhubung."
                            } else {
                                "Hubungan dengan $childName berhasil dilepaskan."
                            }
                        }
                        Toast.makeText(this, "Hubungan $childName berhasil dilepaskan", Toast.LENGTH_LONG).show()
                    }
                    .addOnFailureListener { error ->
                        statusText.text = "Gagal melepaskan hubungan anak"
                        Toast.makeText(
                            this,
                            error.localizedMessage ?: "Tidak dapat menghapus hubungan. Periksa izin Firestore.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
            }
            .show()
    }

    private fun watchChildPairingRemoval(code: String) {
        pairingListener?.remove()
        pairingListener = db.collection("pairings").document(code)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null || snapshot.metadata.isFromCache) return@addSnapshotListener
                if (!snapshot.exists()) handleChildPairingRemoved(code)
            }
    }

    private fun handleChildPairingRemoved(code: String) {
        val prefs = getSharedPreferences("parent_audio", MODE_PRIVATE)
        val savedCode = prefs.getString("pair_code", null)
        if (currentRole != "child" || (!savedCode.isNullOrBlank() && savedCode != code)) return

        pairingListener?.remove()
        pairingListener = null
        prefs.edit()
            .remove("pair_role")
            .remove("pair_code")
            .putBoolean("remote_camera_armed", false)
            .putBoolean("child_usage_audit_enabled", false)
            .putBoolean("child_permission_audit_enabled", false)
            .putBoolean("child_app_block_consent", false)
            .putBoolean("child_app_activity_consent", false)
            .putBoolean("child_location_sharing_enabled", false)
            .apply()
        ChildDeviceStatusWorker.cancel(this)
        ChildUsageScheduleWorker.cancel(this)
        prefs.edit()
            .putBoolean("child_location_sharing_enabled", false)
            .remove("child_schedule_config_enabled")
            .remove("child_schedule_start")
            .remove("child_schedule_end")
            .remove("child_schedule_days")
            .remove("child_schedule_zone")
            .remove("child_schedule_state_known")
            .remove("child_schedule_quiet")
            .remove("child_schedule_enabled")
            .apply()
        (getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager)
            .cancel(2510)
        stopService(Intent(this, ChildLocationSharingService::class.java))

        try {
            startService(Intent(this, RemoteCameraService::class.java).apply {
                action = RemoteCameraService.ACTION_DISARM
            })
        } catch (_: Exception) { }
        stopService(Intent(this, ChildAudioConnectionService::class.java))
        stopService(Intent(this, AudioPlaybackService::class.java))

        currentRole = ""
        activeRoomCode = null
        isMicrophoneActive = false
        activityScope.launch {
            try {
                room?.localParticipant?.setMicrophoneEnabled(false)
                room?.disconnect()
                room?.release()
            } catch (_: Exception) { }
            room = null
            mp3TransferManager = null
        }
        showRoleSelection()
        statusText.text = "Hubungan dengan orang tua telah dilepaskan."
        Toast.makeText(this, "Perangkat ini tidak lagi terhubung ke orang tua.", Toast.LENGTH_LONG).show()
    }

    private fun selectChild(code: String) {
        if (selectedChildCode != code) invalidatePendingRoomConnection()
        selectedChildCode = code
        getSharedPreferences("parent_audio", MODE_PRIVATE).edit()
            .putString("selected_child_code", code).apply()
        isChildrenPanelExpanded = false
        childrenContainer.visibility = View.GONE
        updateSelectedChildButton()
        renderChildrenList()
        codeText.text = code
        childSnapshots[code]?.let { maybeStartParentDriveBackupForChild(code, it) }

        if (childConnected[code] != true) {
            statusText.text = "Menunggu HP ${childNames[code] ?: "anak"} terhubung..."
            return
        }

        childSnapshots[code]?.let { snapshot ->
            latestPairingSnapshot = snapshot
            renderChildAudioActivity(snapshot)
            renderChildDeviceStatus(snapshot)
            childAudioActivityText.removeCallbacks(staleStatusRunnable)
            childAudioActivityText.postDelayed(staleStatusRunnable, 36_000L)
        } ?: run {
            latestPairingSnapshot = null
            childAudioActivityText.removeCallbacks(staleStatusRunnable)
            childAudioActivityText.setActivityState("Menunggu data perangkat yang dipilih...", false)
            childUsageRowsContainer.removeAllViews()
            childActivityRowsContainer.removeAllViews()
            addChildActivityRow(childUsageRowsContainer, "Menunggu data dari perangkat ini...")
            addChildActivityRow(childActivityRowsContainer, "Menunggu data dari perangkat ini...")
            childLocationMapView.showUnavailable(false)
        }

        if (activeRoomCode == code && room != null) {
            statusText.text = if (isMicrophoneActive) {
                "AUDIO LIVE AKTIF"
            } else {
                "HP anak terhubung • audio siap digunakan"
            }
            return
        }

        statusText.text = "HP ${childNames[code] ?: "anak"} terhubung • audio belum diaktifkan"
        if (room != null || isConnecting) {
            activityScope.launch {
                try {
                    room?.localParticipant?.setMicrophoneEnabled(false)
                    room?.disconnect()
                    room?.release()
                } catch (_: Exception) { }
                room = null
                activeRoomCode = null
                mp3TransferManager = null
                isMicrophoneActive = false
                isConnecting = false
                startButton.visibility = View.VISIBLE
                stopButton.visibility = View.GONE
            }
        }
    }

    private fun registerDeviceToken(code: String, role: String, fcmToken: String) {
        val user = auth.currentUser ?: return
        user.getIdToken(false).addOnSuccessListener { result ->
            val idToken = result.token ?: return@addOnSuccessListener
            activityScope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        val connection = URL(workerUrl.replace("/token", "/register-device"))
                            .openConnection() as HttpURLConnection
                        try {
                            connection.requestMethod = "POST"
                            connection.connectTimeout = 15000
                            connection.readTimeout = 15000
                            connection.doOutput = true
                            connection.setRequestProperty("Content-Type", "application/json")
                            val body = JSONObject().apply {
                                put("idToken", idToken)
                                put("code", code)
                                put("fcmToken", fcmToken)
                            }
                            connection.outputStream.use {
                                it.write(body.toString().toByteArray(Charsets.UTF_8))
                            }
                            val status = connection.responseCode
                            if (status !in 200..299) {
                                val stream = connection.errorStream
                                val detail = stream?.bufferedReader()?.use { it.readText() }
                                throw Exception(
                                    JSONObject(detail ?: "{}").optString(
                                        "error", "Server menolak pendaftaran perangkat"
                                    )
                                )
                            }
                        } finally {
                            connection.disconnect()
                        }
                    }
                } catch (error: Exception) {
                    android.util.Log.w("ParentAudioFCM", "Device token registration failed")
                    // Pairing and existing audio features remain usable if registration is unavailable.
                }
            }
        }
    }

    private fun restoreSavedPairing() {
        val prefs = getSharedPreferences("parent_audio", MODE_PRIVATE)
        val role = prefs.getString("pair_role", null) ?: return
        currentRole = role
        if (role == "parent") {
            loadSavedChildren()
            showParentDashboard()
            if (childNames.isEmpty()) {
                selectedChildCode = null
                codeText.text = ""
                statusText.text = "Belum ada HP anak terhubung. Tekan TAMBAH ANAK untuk membuat kode."
            } else {
                // Keep the last selected child restored by loadSavedChildren().
                codeText.text = selectedChildCode.orEmpty()
                statusText.text = "Menampilkan data ${selectedChildCode?.let { childNames[it] } ?: "anak"}"
            }
        } else {
            val code = prefs.getString("pair_code", null) ?: return
            ChildDeviceStatusWorker.schedule(this)
            codeText.text = code
            showChildConnectedScreen()
            watchChildPairingRemoval(code)
            statusText.text = "Terhubung ke orang tua • menunggu perintah audio"
        }
    }



    private fun updateChildLocationPanel() {
        if (!::childLocationPanel.isInitialized) return
        val enabled = getSharedPreferences("parent_audio", MODE_PRIVATE)
            .getBoolean("child_location_sharing_enabled", false)
        childLocationPanel.text = if (enabled) {
            "📍  BERBAGI LOKASI AKTIF\nLokasi perangkat ini dibagikan ke HP orang tua. Ketuk untuk menghentikan."
        } else {
            "📍  BERBAGI LOKASI HP INI\nNonaktif • Ketuk untuk membaca penjelasan dan mengaktifkan berbagi lokasi"
        }
        childLocationPanel.setTextColor(
            if (enabled) Color.rgb(28, 130, 83) else Color.rgb(35, 53, 88)
        )
    }

    private fun showChildLocationConsentDialog() {
        if (currentRole != "child") return
        val enabled = getSharedPreferences("parent_audio", MODE_PRIVATE)
            .getBoolean("child_location_sharing_enabled", false)
        val fineGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val permissionMissing = enabled && !fineGranted && !coarseGranted
        val message = buildString {
            append(if (permissionMissing) "STATUS: IZIN LOKASI DICABUT" else if (enabled) "STATUS: BERBAGI LOKASI AKTIF" else "STATUS: BERBAGI LOKASI NONAKTIF")
            append("\\n\\nCARA KERJA\\n")
            append("Koordinat perangkat ini dikirim ke HP orang tua yang sudah dipasangkan, kira-kira setiap 15 menit saat layanan aktif. Akurasi mengikuti izin lokasi Android (perkiraan atau presisi).")
            append("\\n\\nKENDALI DAN PRIVASI\\n")
            append("Lokasi tidak dikirim sebelum Anda mengaktifkannya. Notifikasi SantetHP akan terlihat selama berbagi berlangsung, dan Anda dapat menghentikannya kapan saja.")
            if (permissionMissing) append("\\n\\nAktifkan kembali izin lokasi Android untuk melanjutkan fitur yang sebelumnya Anda setujui.")
        }
        SantetHpDialogBuilder()
            .setTitle(if (permissionMissing) "Izin lokasi diperlukan" else if (enabled) "Berbagi lokasi sedang aktif" else "Izin berbagi lokasi")
            .setMessage(message)
            .setNegativeButton("Tutup", null)
            .setPositiveButton(if (permissionMissing) "Aktifkan izin lokasi" else if (enabled) "Hentikan berbagi" else "Aktifkan berbagi") { _, _ ->
                if (permissionMissing || !enabled) requestChildLocationSharing() else stopChildLocationSharing()
            }
            .show()
    }

    private fun requestChildLocationSharing() {
        if (currentRole != "child") return
        pendingLocationStart = true
        val fineGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!fineGranted && !coarseGranted) {
            requestPermissions(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                LOCATION_PERMISSION
            )
            return
        }
        continueStartingChildLocationSharing()
    }

    private fun continueStartingChildLocationSharing() {
        if (!pendingLocationStart) return
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                LOCATION_NOTIFICATION_PERMISSION
            )
            return
        }
        pendingLocationStart = false
        try {
            val intent = Intent(this, ChildLocationSharingService::class.java).apply {
                action = ChildLocationSharingService.ACTION_START
            }
            ContextCompat.startForegroundService(this, intent)
            getSharedPreferences("parent_audio", MODE_PRIVATE).edit()
                .putBoolean("child_location_sharing_enabled", true).apply()
            updateChildLocationPanel()
            Toast.makeText(this, "Berbagi lokasi dimulai. Notifikasi SantetHP akan tetap terlihat.", Toast.LENGTH_LONG).show()
        } catch (error: Exception) {
            getSharedPreferences("parent_audio", MODE_PRIVATE).edit()
                .putBoolean("child_location_sharing_enabled", false).apply()
            updateChildLocationPanel()
            Toast.makeText(this, "Tidak dapat memulai berbagi lokasi: ${error.localizedMessage ?: "coba lagi"}", Toast.LENGTH_LONG).show()
        }
    }

    private fun stopChildLocationSharing() {
        pendingLocationStart = false
        try {
            startService(Intent(this, ChildLocationSharingService::class.java).apply {
                action = ChildLocationSharingService.ACTION_STOP
            })
        } catch (_: Exception) {
            stopService(Intent(this, ChildLocationSharingService::class.java))
        }
        getSharedPreferences("parent_audio", MODE_PRIVATE).edit()
            .putBoolean("child_location_sharing_enabled", false).apply()
        updateChildLocationPanel()
        Toast.makeText(this, "Berbagi lokasi dihentikan.", Toast.LENGTH_SHORT).show()
    }

    private fun isAppBlockAccessibilityEnabled(): Boolean {
        val expected = android.content.ComponentName(
            this, ChildAppBlockAccessibilityService::class.java
        )
        val enabled = android.provider.Settings.Secure.getString(
            contentResolver, android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.split(':').any { entry ->
            android.content.ComponentName.unflattenFromString(entry) == expected
        }
    }

    private fun updateChildAppBlockPanel() {
        if (!::childAppBlockPanel.isInitialized) return
        val enabled = getSharedPreferences("parent_audio", MODE_PRIVATE)
            .getBoolean("child_app_block_consent", false)
        val serviceEnabled = isAppBlockAccessibilityEnabled()
        childAppBlockPanel.text = when {
            !enabled -> "🚫  BLOKIR APLIKASI\nNonaktif • Ketuk untuk membaca penjelasan dan memberi persetujuan"
            !serviceEnabled -> "🚫  BLOKIR APLIKASI\nPersetujuan diberikan, tetapi Aksesibilitas belum diaktifkan • Ketuk untuk pengaturan"
            else -> "🚫  BLOKIR APLIKASI\nAktif • HP anak dapat mencegah aplikasi pilihan orang tua dibuka • Ketuk untuk mengelola"
        }
    }

    private fun showChildAppBlockConsent() {
        if (currentRole != "child") return
        val prefs = getSharedPreferences("parent_audio", MODE_PRIVATE)
        val enabled = prefs.getBoolean("child_app_block_consent", false)
        SantetHpDialogBuilder()
            .setTitle("Kontrol blokir aplikasi")
            .setView(popupInfoCards(listOf(
                "CARA KERJA" to "HP anak mengirim daftar nama aplikasi yang tersedia agar orang tua dapat memilih aplikasi yang dibatasi. Saat aplikasi yang diblokir dibuka, SantetHP mengembalikan ke layar utama.",
                "BATASAN AKSES" to "Android meminta Accessibility Service diaktifkan secara manual. Layanan hanya memeriksa nama paket aplikasi yang sedang tampil; tidak membaca isi layar, pesan, foto, kata sandi, atau mengetik untuk pengguna.",
                "KENDALI DAN PRIVASI" to "Persetujuan ini dapat dimatikan kapan saja dari HP anak atau aksesnya dicabut melalui Pengaturan Android."
            )))
            .setNegativeButton("Tutup", null)
            .setNeutralButton(if (enabled) "Matikan fitur" else "Setuju") { _, _ ->
                prefs.edit().putBoolean("child_app_block_consent", !enabled).apply()
                if (enabled) {
                    val code = prefs.getString("pair_code", null)
                    if (prefs.getString("pair_role", null) == "child" && !code.isNullOrBlank()) {
                        com.google.firebase.firestore.FirebaseFirestore.getInstance()
                            .collection("pairings")
                            .document(code)
                            .update(
                                mapOf(
                                    "appBlockConsent" to false,
                                    "appBlockInstalledApps" to emptyList<Map<String, String>>(),
                                    "blockedApps" to emptyList<String>(),
                                    "appBlockUpdatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                                )
                            )
                    }
                }
                ChildDeviceStatusWorker.schedule(this)
                updateChildAppBlockPanel()
            }
            .setPositiveButton("Pengaturan Aksesibilitas") { _, _ ->
                if (!enabled) {
                    prefs.edit().putBoolean("child_app_block_consent", true).apply()
                    ChildDeviceStatusWorker.schedule(this)
                }
                try {
                    startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS))
                } catch (_: Exception) {
                    Toast.makeText(this, "Pengaturan Aksesibilitas tidak tersedia", Toast.LENGTH_LONG).show()
                }
                updateChildAppBlockPanel()
            }
            .show()
    }

    private fun updateChildAppActivityPanel() {
        if (!::childAppActivityPanel.isInitialized) return
        val enabled = getSharedPreferences("parent_audio", MODE_PRIVATE)
            .getBoolean("child_app_activity_consent", false)
        val serviceEnabled = isAppBlockAccessibilityEnabled()
        childAppActivityPanel.text = when {
            !enabled ->
                "📱  PEMANTAUAN APLIKASI AKTIF\nNonaktif • Ketuk untuk membaca penjelasan dan memberi persetujuan"
            !serviceEnabled ->
                "📱  PEMANTAUAN APLIKASI AKTIF\nPersetujuan diberikan, tetapi Aksesibilitas belum aktif • Ketuk untuk pengaturan"
            else ->
                "📱  PEMANTAUAN APLIKASI AKTIF\nAktif • Nama aplikasi yang tampil dikirim saat berganti aplikasi • Ketuk untuk menghentikan"
        }
    }

    private fun showChildAppActivityConsent() {
        if (currentRole != "child") return
        val prefs = getSharedPreferences("parent_audio", MODE_PRIVATE)
        val enabled = prefs.getBoolean("child_app_activity_consent", false)
        val serviceEnabled = isAppBlockAccessibilityEnabled()
        val infoCards = if (enabled) listOf(
            "STATUS" to "Pemantauan aplikasi aktif sedang diizinkan.",
            "DATA YANG DIBAGIKAN" to "SantetHP hanya membagikan nama aplikasi yang tampil ketika berpindah aplikasi dan waktu deteksinya.",
            "PRIVASI DAN KENDALI" to "SantetHP tidak membaca isi layar, pesan, foto, kata sandi, atau ketikan. Anda dapat menghentikannya kapan saja."
        ) else listOf(
            "CARA KERJA" to "Jika disetujui, SantetHP mengirim nama aplikasi yang sedang tampil ke HP orang tua setiap kali aplikasi berganti melalui layanan Aksesibilitas Android.",
            "BATASAN AKSES" to "SantetHP hanya membaca nama paket aplikasi, bukan isi layar, pesan, foto, kata sandi, atau ketikan. Fitur ini terpisah dari blokir aplikasi.",
            "KENDALI DAN PRIVASI" to "Layanan Aksesibilitas harus diaktifkan secara manual. Pemantauan dapat dihentikan kapan saja dari HP anak."
        )
        val dialog = SantetHpDialogBuilder()
            .setTitle("Pemantauan aplikasi aktif")
            .setView(popupInfoCards(infoCards))
            .setNegativeButton("Tutup", null)
        if (enabled) {
            dialog.setPositiveButton("Hentikan pemantauan") { _, _ ->
                prefs.edit().putBoolean("child_app_activity_consent", false).apply()
                val code = prefs.getString("pair_code", null)
                if (prefs.getString("pair_role", null) == "child" && !code.isNullOrBlank()) {
                    db.collection("pairings").document(code).update("appActivityConsent", false)
                }
                updateChildAppActivityPanel()
                Toast.makeText(this, "Pemantauan aplikasi aktif dihentikan.", Toast.LENGTH_LONG).show()
            }
        } else {
            dialog.setPositiveButton(if (serviceEnabled) "Saya setuju, aktifkan" else "Setuju & buka pengaturan") { _, _ ->
                prefs.edit().putBoolean("child_app_activity_consent", true).apply()
                val code = prefs.getString("pair_code", null)
                if (prefs.getString("pair_role", null) == "child" && !code.isNullOrBlank()) {
                    db.collection("pairings").document(code).update("appActivityConsent", true)
                }
                updateChildAppActivityPanel()
                if (!serviceEnabled) {
                    try {
                        startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    } catch (_: Exception) {
                        Toast.makeText(this, "Pengaturan Aksesibilitas tidak tersedia", Toast.LENGTH_LONG).show()
                    }
                } else {
                    Toast.makeText(this, "Pemantauan aplikasi aktif diizinkan.", Toast.LENGTH_LONG).show()
                }
            }
        }
        dialog.show()
    }

    private fun showParentAppBlockDialog() {
        if (currentRole != "parent") return
        val code = selectedChildCode
        if (code.isNullOrBlank()) {
            Toast.makeText(this, "Pilih perangkat anak terlebih dahulu.", Toast.LENGTH_SHORT).show()
            return
        }
        db.collection("pairings").document(code).get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.getBoolean("appBlockConsent") != true) {
                    SantetHpDialogBuilder()
                        .setTitle("Blokir aplikasi")
                        .setMessage("Fitur ini menunggu persetujuan dari HP anak dan aktivasi Accessibility Service secara manual.")
                        .setPositiveButton("Mengerti", null).show()
                    return@addOnSuccessListener
                }
                val apps = (snapshot.get("appBlockInstalledApps") as? List<*>)
                    .orEmpty().mapNotNull { raw ->
                        val item = raw as? Map<*, *> ?: return@mapNotNull null
                        val pkg = item["packageName"] as? String ?: return@mapNotNull null
                        val name = item["appName"] as? String ?: pkg
                        pkg to name
                    }.distinctBy { it.first }.sortedBy { it.second.lowercase() }
                if (apps.isEmpty()) {
                    Toast.makeText(this, "Daftar aplikasi belum tersedia. Pastikan HP anak online.", Toast.LENGTH_LONG).show()
                    return@addOnSuccessListener
                }
                val now = System.currentTimeMillis()
                val existingRules = (snapshot.get("blockedApps") as? List<*>)
                    .orEmpty().mapNotNull { raw ->
                        val item = raw as? Map<*, *> ?: return@mapNotNull null
                        val pkg = item["packageName"] as? String ?: return@mapNotNull null
                        val name = item["appName"] as? String ?: pkg
                        val until = (item["blockedUntilMillis"] as? Number)?.toLong()
                            ?: return@mapNotNull null
                        if (until > now) Triple(pkg, name, until) else null
                    }
                val activeByPackage = existingRules.associateBy { it.first }
                val labels = apps.map { (pkg, name) ->
                    val until = activeByPackage[pkg]?.third
                    if (until != null) {
                        val time = android.text.format.DateFormat.getTimeFormat(this@MainActivity)
                            .format(Date(until))
                        "$name • diblokir sampai $time"
                    } else {
                        name
                    }
                }.toTypedArray()

                SantetHpDialogBuilder()
                    .setTitle("Pilih aplikasi • atur durasi masing-masing")
                    .setItems(labels) { _, which ->
                        val (pkg, name) = apps[which]
                        val currentUntil = activeByPackage[pkg]?.third
                        val durationInput = EditText(this).apply {
                            inputType = android.text.InputType.TYPE_CLASS_NUMBER
                            hint = "Contoh: 60"
                            setText("60")
                            selectAll()
                        }
                        val durationDialog = SantetHpDialogBuilder()
                            .setTitle("Durasi untuk $name")
                            .setMessage(
                                if (currentUntil != null) {
                                    "Aplikasi ini sedang diblokir. Masukkan durasi baru dalam menit (1 sampai 10080) untuk memulai ulang hitungan waktunya."
                                } else {
                                    "Masukkan durasi blokir dalam menit (1 sampai 10080). Setelah waktunya habis, aplikasi otomatis dapat dibuka kembali."
                                }
                            )
                            .setView(popupContentCard(durationInput))
                            .setNegativeButton("Batal", null)
                            .setPositiveButton("Blokir") { _, _ ->
                                val minutes = durationInput.text.toString().toLongOrNull()
                                if (minutes == null || minutes !in 1L..10080L) {
                                    Toast.makeText(this, "Durasi harus 1–10080 menit.", Toast.LENGTH_LONG).show()
                                } else {
                                    val retained = existingRules.filter { it.first != pkg }
                                        .map { (oldPkg, oldName, oldUntil) ->
                                            mapOf<String, Any>(
                                                "packageName" to oldPkg,
                                                "appName" to oldName,
                                                "blockedUntilMillis" to oldUntil
                                            )
                                        }
                                    val newRule = mapOf<String, Any>(
                                        "packageName" to pkg,
                                        "appName" to name,
                                        "blockedUntilMillis" to System.currentTimeMillis() + minutes * 60_000L
                                    )
                                    db.collection("pairings").document(code).update(
                                        "blockedApps", retained + newRule,
                                        "appBlockUpdatedAt", com.google.firebase.firestore.FieldValue.serverTimestamp()
                                    ).addOnSuccessListener {
                                        Toast.makeText(this, "$name diblokir selama $minutes menit.", Toast.LENGTH_LONG).show()
                                    }.addOnFailureListener {
                                        Toast.makeText(this, "Gagal menyimpan pemblokiran.", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        if (currentUntil != null) {
                            durationDialog.setNeutralButton("Hapus blokir", null)
                        }
                        durationDialog.show().apply {
                            if (currentUntil != null) {
                                getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                                    val retained = existingRules.filter { it.first != pkg }
                                        .map { (oldPkg, oldName, oldUntil) ->
                                            mapOf<String, Any>(
                                                "packageName" to oldPkg,
                                                "appName" to oldName,
                                                "blockedUntilMillis" to oldUntil
                                            )
                                        }
                                    db.collection("pairings").document(code).update(
                                        "blockedApps", retained,
                                        "appBlockUpdatedAt", com.google.firebase.firestore.FieldValue.serverTimestamp()
                                    ).addOnSuccessListener {
                                        Toast.makeText(this@MainActivity, "Blokir $name dihentikan.", Toast.LENGTH_SHORT).show()
                                        dismiss()
                                    }.addOnFailureListener {
                                        Toast.makeText(this@MainActivity, "Gagal menghapus blokir.", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        }
                    }.show()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Gagal mengambil daftar aplikasi anak.", Toast.LENGTH_LONG).show()
            }
    }

    private fun requestRemoteCamera() {
        if (currentRole != "parent") return
        val code = selectedChildCode
        if (code.isNullOrBlank()) {
            statusText.text = "Pilih HP anak terlebih dahulu"
            return
        }
        startActivity(Intent(this, CameraViewerActivity::class.java)
            .putExtra(CameraViewerActivity.EXTRA_CODE, code))
    }

    /**
     * Restore an explicitly enabled camera standby only while the child app is visible.
     * This repairs a stale preference when Android has stopped the foreground service.
     */
    private fun restoreCameraStandbyIfNeeded() {
        val prefs = getSharedPreferences("parent_audio", MODE_PRIVATE)
        if (prefs.getString("pair_role", null) != "child" ||
            !prefs.getBoolean("remote_camera_armed", false)
        ) return

        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            prefs.edit().putBoolean("remote_camera_armed", false).apply()
            updateCameraStandbyButton()
            statusText.text = "Izin kamera tidak tersedia. Aktifkan siaga kamera kembali."
            return
        }

        try {
            val serviceIntent = Intent(this, RemoteCameraService::class.java).apply {
                action = RemoteCameraService.ACTION_ARM
            }
            ContextCompat.startForegroundService(this, serviceIntent)
            rootLayout.postDelayed({
                if (!isFinishing && !isDestroyed) updateCameraStandbyButton()
            }, 1200)
        } catch (error: Exception) {
            statusText.text = "Pemulihan siaga kamera gagal: ${error.localizedMessage ?: "izin Android"}"
        }
    }

    private fun updateCameraStandbyButton() {
        val armed = getSharedPreferences("parent_audio", MODE_PRIVATE)
            .getBoolean("remote_camera_armed", false)
        cameraStandbyButton.text = "AKTIFKAN SIAGA KAMERA"
        // Once standby is active, restore the original child screen without the camera menu.
        cameraStandbyButton.visibility = if (armed) View.GONE else View.VISIBLE
    }

    private fun toggleRemoteCameraStandby() {
        if (currentRole != "child") return
        val prefs = getSharedPreferences("parent_audio", MODE_PRIVATE)
        if (prefs.getBoolean("remote_camera_armed", false)) {
            startService(Intent(this, RemoteCameraService::class.java).apply {
                action = RemoteCameraService.ACTION_DISARM
            })
            cameraStandbyButton.visibility = View.VISIBLE
            cameraStandbyButton.text = "AKTIFKAN SIAGA KAMERA"
            statusText.text = "Siaga kamera dinonaktifkan"
            return
        }
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION)
            return
        }
        armRemoteCameraStandby()
    }

    private fun armRemoteCameraStandby() {
        if (currentRole != "child") return
        try {
            val serviceIntent = Intent(this, RemoteCameraService::class.java).apply {
                action = RemoteCameraService.ACTION_ARM
            }
            ContextCompat.startForegroundService(this, serviceIntent)
            // Return to the original connected-child page after the foreground service confirms arming.
            statusText.text = "Menyiapkan siaga kamera..."
            rootLayout.postDelayed({
                updateCameraStandbyButton()
                val armed = getSharedPreferences("parent_audio", MODE_PRIVATE)
                    .getBoolean("remote_camera_armed", false)
                statusText.text = if (armed) {
                    "Siaga kamera aktif di latar belakang."
                } else {
                    "Siaga kamera belum berhasil aktif. Periksa izin kamera dan notifikasi."
                }
            }, 1000)
        } catch (error: Exception) {
            statusText.text = "Gagal mengaktifkan siaga kamera: ${error.localizedMessage ?: "izin Android"}"
        }
    }

    private fun generateCode(): String {
        val random = SecureRandom()

        return (1..8).map {
            characters[random.nextInt(characters.length)]
        }.joinToString("")
    }

    private fun createPairingCode() {
        val uid = auth.currentUser?.uid

        if (uid == null) {
            statusText.text = "Firebase belum terhubung!"
            return
        }

        statusText.text = "Membuat kode pasangan..."
        codeText.text = ""

        val code = generateCode()
        val now = System.currentTimeMillis()

        val data = hashMapOf(
            "parentUid" to uid,
            "childUid" to null,
            "createdAt" to Timestamp(Date(now)),
            "expiresAt" to Timestamp(Date(now + 5 * 60 * 1000))
        )

        db.collection("pairings")
            .document(code)
            .set(data)
            .addOnSuccessListener {

                val wasParent = currentRole == "parent"
                savePairing(code, "parent")
                currentRole = "parent"
                addParentChild(code)
                pendingPairingCode = code
                showPairingCodeScreen()

                codeText.text = code
                statusText.text = "Bagikan kode ini kepada HP anak"

                listenForChild(code)
            }
            .addOnFailureListener { error ->

                statusText.text = "Gagal membuat kode"

                Toast.makeText(
                    this,
                    error.localizedMessage ?: "Terjadi kesalahan",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    private fun cancelPendingPairingCode() {
        val code = pendingPairingCode ?: return
        if (childConnected[code] == true) {
            Toast.makeText(this, "Anak sudah terhubung. Kode tidak dapat dibatalkan dari layar ini.", Toast.LENGTH_LONG).show()
            return
        }

        SantetHpDialogBuilder()
            .setTitle("Batalkan kode anak?")
            .setMessage("Kode $code akan dihapus dan tidak bisa digunakan lagi untuk memasangkan HP anak.")
            .setNegativeButton("Kembali", null)
            .setPositiveButton("Ya, batalkan") { _, _ ->
                statusText.text = "Membatalkan kode..."
                db.collection("pairings").document(code)
                    .delete()
                    .addOnSuccessListener {
                        childListeners.remove(code)?.remove()
                        childNames.remove(code)
                        childConnected.remove(code)
                        renamePromptedCodes.remove(code)
                        persistChildren()
                        pendingPairingCode = null

                        if (selectedChildCode == code) selectedChildCode = childNames.keys.firstOrNull()
                        if (childNames.isNotEmpty()) {
                            showParentDashboard()
                            val nextCode = selectedChildCode ?: childNames.keys.first()
                            selectedChildCode = nextCode
                            codeText.text = nextCode
                            statusText.text = "Kode dibatalkan. Pilih anak yang ingin dihubungkan."
                        } else {
                            // Tetap di mode orang tua dan kembali ke menu utama,
                            // meskipun belum ada HP anak yang berhasil dipasangkan.
                            getSharedPreferences("parent_audio", MODE_PRIVATE).edit()
                                .putString("pair_role", "parent")
                                .remove("pair_code")
                                .apply()
                            currentRole = "parent"
                            selectedChildCode = null
                            codeText.text = ""
                            showParentDashboard()
                            statusText.text = "Kode dibatalkan. Anda kembali ke menu utama."
                        }
                    }
                    .addOnFailureListener { error ->
                        statusText.text = "Gagal membatalkan kode"
                        Toast.makeText(this, error.localizedMessage ?: "Tidak dapat menghapus kode. Periksa izin Firestore.", Toast.LENGTH_LONG).show()
                    }
            }
            .show()
    }

    private fun renderChildAudioActivity(snapshot: DocumentSnapshot) {
        val now = System.currentTimeMillis()
        val staleAfterMs = 35_000L
        val liveTimestamp = snapshot.getTimestamp("liveAudioActivityAt")
            ?: snapshot.getTimestamp("audioActivityAt")
        val mp3Timestamp = snapshot.getTimestamp("mp3AudioActivityAt")
            ?: snapshot.getTimestamp("audioActivityAt")
        val liveAge = liveTimestamp?.toDate()?.time?.let { now - it }
        val mp3Age = mp3Timestamp?.toDate()?.time?.let { now - it }
        val liveFresh = liveAge != null && liveAge in -60_000L..staleAfterMs
        val mp3Fresh = mp3Age != null && mp3Age in -60_000L..staleAfterMs
        val liveAudioActive =
            snapshot.getBoolean("liveAudioActive") == true && liveFresh
        val mp3AudioActive =
            snapshot.getBoolean("mp3AudioActive") == true && mp3Fresh

        val activityLabel = when {
            liveAudioActive && mp3AudioActive -> "MP3 dan Audio Live sedang aktif"
            liveAudioActive -> "Audio Live sedang aktif"
            mp3AudioActive -> "MP3 sedang diputar"
            else -> "Tidak sedang memutar audio"
        }
        childAudioActivityText.setActivityState(
            activityLabel, liveAudioActive || mp3AudioActive
        )
    }

    private fun notificationListenerAccessEnabled(): Boolean {
        val enabled = Settings.Secure.getString(
            contentResolver, "enabled_notification_listeners"
        ).orEmpty()
        val component = ComponentName(this, ChildNotificationListenerService::class.java).flattenToString()
        return enabled.split(':').any { it.equals(component, ignoreCase = true) }
    }

    private fun updateChildNotificationPanel() {
        if (!this::childNotificationPanel.isInitialized || currentRole != "child") return
        val optedIn = getSharedPreferences("parent_audio", MODE_PRIVATE)
            .getBoolean("child_notification_monitoring_enabled", false)
        val access = notificationListenerAccessEnabled()
        childNotificationPanel.text = when {
            !optedIn -> "🔔  PEMANTAUAN NOTIFIKASI\nNonaktif • Ketuk untuk membaca penjelasan dan mengaktifkan akses"
            access -> "🔔  PEMANTAUAN NOTIFIKASI AKTIF\nAkses Android sudah aktif • Notifikasi yang tampil akan dibagikan ke HP orang tua"
            else -> "🔔  PEMANTAUAN NOTIFIKASI\nPersetujuan tersimpan • Aktifkan akses SantetHP pada Pengaturan Android"
        }
        childNotificationPanel.setTextColor(
            if (optedIn && access) Color.rgb(28, 132, 86) else Color.rgb(35, 53, 88)
        )
    }

    private fun showChildNotificationConsent() {
        val prefs = getSharedPreferences("parent_audio", MODE_PRIVATE)
        if (prefs.getBoolean("child_notification_monitoring_enabled", false)) {
            try {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            } catch (_: Exception) {
                Toast.makeText(this, "Pengaturan akses notifikasi tidak tersedia.", Toast.LENGTH_LONG).show()
            }
            return
        }
        SantetHpDialogBuilder()
            .setTitle("Pemantauan semua notifikasi")
            .setView(popupInfoCards(listOf(
                "DATA YANG DIBAGIKAN" to "SantetHP dapat membaca nama aplikasi, judul, dan isi notifikasi yang ditampilkan Android pada HP ini, lalu menyimpannya agar dapat dilihat dari HP orang tua.",
                "PERHATIAN PRIVASI" to "Data dapat mencakup isi pesan dan kode verifikasi jika aplikasi menampilkannya. Jangan aktifkan jika Anda tidak setuju membagikan informasi tersebut.",
                "CARA MENGAKTIFKAN" to "Izin khusus harus diaktifkan secara manual melalui Pengaturan Android. Akses dapat dikelola kembali dari pengaturan tersebut."
            )))
            .setNegativeButton("Batal", null)
            .setPositiveButton("Saya setuju • Lanjutkan") { _, _ ->
                prefs.edit().putBoolean("child_notification_monitoring_enabled", true).apply()
                try {
                    startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                } catch (_: Exception) {
                    Toast.makeText(this, "Pengaturan akses notifikasi tidak tersedia.", Toast.LENGTH_LONG).show()
                }
                updateChildNotificationPanel()
            }
            .show()
    }

    private fun galleryMediaPermissions(): Array<String> = when {
        android.os.Build.VERSION.SDK_INT >= 34 -> arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
        )
        android.os.Build.VERSION.SDK_INT >= 33 -> arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO
        )
        else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    private fun hasGalleryMediaAccess(): Boolean =
        galleryMediaPermissions().any { permission ->
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        }

    private fun updateChildGalleryPanel() {
        if (!this::childGalleryPanel.isInitialized || currentRole != "child") return
        val prefs = getSharedPreferences("parent_audio", MODE_PRIVATE)
        val enabled = prefs.getBoolean("child_gallery_sharing_enabled", false)
        val backupEnabled = prefs.getBoolean("child_drive_backup_consent", false)
        val mediaAccess = hasGalleryMediaAccess()
        val serverConfirmed = prefs.getBoolean("child_gallery_server_consent_confirmed", false)
        val galleryStatus = when {
            !enabled -> "BERBAGI GALERI: NONAKTIF"
            !mediaAccess -> "BERBAGI GALERI: IZIN FOTO/VIDEO BELUM TERSEDIA"
            !serverConfirmed -> "BERBAGI GALERI: MENUNGGU KONFIRMASI SERVER"
            else -> "BERBAGI GALERI: AKTIF"
        }
        val pendingBackupCount = if (backupEnabled) GalleryBackupTracker(this).pendingItems().size else 0
        val receiveEnabled = prefs.getBoolean("child_parent_media_receive_consent", false) &&
            prefs.getBoolean("child_parent_media_server_confirmed", false)
        val receiveStatus = if (receiveEnabled) {
            "PENERIMAAN DARI ORANG TUA: AKTIF • foto/video tersimpan otomatis setelah diterima"
        } else {
            "PENERIMAAN DARI ORANG TUA: NONAKTIF"
        }
        val backupStatus = if (backupEnabled) {
            "BACKUP DRIVE: PERSETUJUAN AKTIF • hanya file baru\nAntrean lokal: $pendingBackupCount file baru menunggu koneksi HP orang tua dan upload Drive"
        } else {
            "BACKUP DRIVE: BELUM DISETUJUI"
        }
        childGalleryPanel.text = "🖼️  GALERI & BACKUP HP INI\n$galleryStatus\n$receiveStatus\n$backupStatus\nKetuk untuk mengatur persetujuan masing-masing fitur."
        childGalleryPanel.setTextColor(
            if (enabled && mediaAccess && serverConfirmed) Color.rgb(28, 132, 86) else Color.rgb(35, 53, 88)
        )
    }

    private fun updateGalleryConsentOnServer(enabled: Boolean, onSaved: (() -> Unit)? = null) {
        val prefs = getSharedPreferences("parent_audio", MODE_PRIVATE)
        val code = prefs.getString("pair_code", null)
        if (code.isNullOrBlank()) {
            prefs.edit().putBoolean("child_gallery_server_consent_confirmed", false).apply()
            updateChildGalleryPanel()
            Toast.makeText(this, "Kode pairing tidak ditemukan. Periksa status pasangan perangkat.", Toast.LENGTH_LONG).show()
            return
        }
        if (!enabled) {
            prefs.edit().putBoolean("child_gallery_server_consent_confirmed", false).apply()
            // Gallery browsing can be revoked while the separate Drive-backup
            // consent remains active; keep the transport only for that consent.
            if (!prefs.getBoolean("child_drive_backup_consent", false)) {
                stopChildGalleryP2p()
            }
        }
        db.collection("pairings").document(code).update(
            mapOf("gallerySharingEnabled" to enabled, "gallerySharingUpdatedAt" to Timestamp.now())
        ).addOnSuccessListener {
            prefs.edit().putBoolean("child_gallery_server_consent_confirmed", enabled).apply()
            if (enabled) onSaved?.invoke()
            updateChildGalleryPanel()
        }.addOnFailureListener { error ->
            prefs.edit().putBoolean("child_gallery_server_consent_confirmed", false).apply()
            updateChildGalleryPanel()
            Toast.makeText(this,
                "Status berbagi galeri belum tersimpan: ${error.localizedMessage ?: "periksa koneksi"}",
                Toast.LENGTH_LONG).show()
        }
    }

    private fun showChildMediaSettingsMenu() {
        if (currentRole != "child") return
        SantetHpDialogBuilder()
            .setTitle("Pengaturan Galeri & Backup")
            .setItems(arrayOf("Berbagi Galeri Anak", "Penerimaan dari HP Orang Tua", "Backup Google Drive")) { _, which ->
                when (which) {
                    0 -> showChildGalleryConsent()
                    1 -> showChildParentMediaReceiveConsent()
                    2 -> showChildDriveBackupConsent()
                }
            }
            .setNegativeButton("Tutup", null)
            .show()
    }

    private fun updateParentMediaReceiveConsentOnServer(enabled: Boolean) {
        val code = getSharedPreferences("parent_audio", MODE_PRIVATE).getString("pair_code", null)
        if (code.isNullOrBlank()) return
        db.collection("pairings").document(code).update(
            "parentMediaReceiveEnabled", enabled,
            "parentMediaReceiveUpdatedAt", Timestamp.now()
        ).addOnSuccessListener {
            val prefs = getSharedPreferences("parent_audio", MODE_PRIVATE)
            prefs.edit().putBoolean("child_parent_media_server_confirmed", enabled).apply()
            updateChildGalleryPanel()
            if (enabled) startChildGalleryP2pIfAllowed()
        }.addOnFailureListener { error ->
            getSharedPreferences("parent_audio", MODE_PRIVATE).edit()
                .putBoolean("child_parent_media_server_confirmed", false)
                .putBoolean("child_parent_media_receive_consent", false).apply()
            updateChildGalleryPanel()
            Toast.makeText(this,
                "Persetujuan penerimaan belum tersimpan di server: ${error.localizedMessage ?: "periksa koneksi"}",
                Toast.LENGTH_LONG).show()
        }
    }

    private fun showChildParentMediaReceiveConsent() {
        if (currentRole != "child") return
        val prefs = getSharedPreferences("parent_audio", MODE_PRIVATE)
        val enabled = prefs.getBoolean("child_parent_media_receive_consent", false)
        val infoCards = if (enabled) listOf(
            "STATUS" to "Penerimaan foto dan video dari HP orang tua sedang aktif.",
            "CARA KERJA" to "File yang dikirim melalui perangkat yang sudah dipasangkan akan otomatis disimpan ke galeri HP ini tanpa persetujuan per file.",
            "KENDALI" to "Persetujuan dapat dicabut kapan saja dari HP anak."
        ) else listOf(
            "CARA KERJA" to "Jika diaktifkan, HP orang tua yang sudah dipasangkan dapat mengirim foto dan video ke HP ini.",
            "PENYIMPANAN" to "File otomatis disimpan ke Galeri setelah transfer selesai dan lolos pemeriksaan. Tidak ada persetujuan berulang untuk setiap file.",
            "KENDALI" to "Anda dapat menonaktifkan penerimaan kapan saja dari HP anak."
        )
        SantetHpDialogBuilder()
            .setTitle(if (enabled) "Penerimaan foto/video aktif" else "Izinkan penerimaan foto/video")
            .setView(popupInfoCards(infoCards))
            .setNegativeButton("Tutup", null)
            .setPositiveButton(if (enabled) "Cabut persetujuan" else "Saya setuju • Aktifkan") { _, _ ->
                if (enabled) {
                    prefs.edit().putBoolean("child_parent_media_receive_consent", false)
                        .putBoolean("child_parent_media_server_confirmed", false).apply()
                    updateParentMediaReceiveConsentOnServer(false)
                    if (!prefs.getBoolean("child_gallery_sharing_enabled", false) &&
                        !prefs.getBoolean("child_drive_backup_consent", false)
                    ) stopChildGalleryP2p()
                    updateChildGalleryPanel()
                    Toast.makeText(this, "Persetujuan penerimaan dari orang tua dicabut.", Toast.LENGTH_LONG).show()
                } else if (Build.VERSION.SDK_INT < 29 &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
                ) {
                    requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), PARENT_MEDIA_WRITE_PERMISSION)
                } else {
                    prefs.edit().putBoolean("child_parent_media_receive_consent", true)
                        .putBoolean("child_parent_media_server_confirmed", false).apply()
                    updateParentMediaReceiveConsentOnServer(true)
                    Toast.makeText(this, "Mengaktifkan penerimaan foto/video dari HP orang tua…", Toast.LENGTH_LONG).show()
                }
            }.show()
    }

    private fun showChildGalleryConsent() {
        if (currentRole != "child") return
        val prefs = getSharedPreferences("parent_audio", MODE_PRIVATE)
        val enabled = prefs.getBoolean("child_gallery_sharing_enabled", false)
        val backupEnabled = prefs.getBoolean("child_drive_backup_consent", false)
        val infoCards = if (enabled) listOf(
            "STATUS" to "Berbagi galeri dari HP ini sedang aktif untuk HP orang tua yang sudah dipasangkan.",
            "CARA KERJA" to "Thumbnail foto dan video ditampilkan lebih dulu. File asli atau kualitas HD baru dikirim saat orang tua memilih item.",
            "KENDALI" to "Anda dapat menghentikan berbagi kapan saja dari HP anak."
        ) else listOf(
            "CARA KERJA" to "Jika diaktifkan, daftar foto dan video dari HP ini dapat dilihat oleh HP orang tua yang sudah dipasangkan.",
            "PENGIRIMAN FILE" to "Thumbnail ditampilkan lebih dulu. File asli atau kualitas HD baru dikirim saat orang tua memilih item.",
            "KENDALI DAN PRIVASI" to "Berbagi galeri hanya berjalan setelah persetujuan diberikan dan dapat dihentikan kapan saja dari HP anak."
        )
        val dialog = SantetHpDialogBuilder()
            .setTitle(if (enabled) "Berbagi galeri sedang aktif" else "Persetujuan berbagi galeri")
            .setView(popupInfoCards(infoCards))
            .setNegativeButton("Tutup", null)
            .setNeutralButton("Pengaturan Backup Drive", null)
            .setPositiveButton(if (enabled) "Hentikan berbagi" else "Saya setuju • Lanjutkan") { _, _ ->
                if (enabled) {
                    prefs.edit().putBoolean("child_gallery_sharing_enabled", false).apply()
                    // Keep the P2P service running if the child separately
                    // consented to Google Drive backup.
                    if (!prefs.getBoolean("child_drive_backup_consent", false)) {
                        stopChildGalleryP2p()
                    }
                    updateGalleryConsentOnServer(false)
                    updateChildGalleryPanel()
                } else {
                    prefs.edit()
                        .putBoolean("child_gallery_sharing_enabled", true)
                        .putBoolean("child_gallery_server_consent_confirmed", false)
                        .apply()
                    updateChildGalleryPanel()
                    if (hasGalleryMediaAccess()) {
                        updateGalleryConsentOnServer(true) { startChildGalleryP2pIfAllowed() }
                    } else {
                        requestPermissions(galleryMediaPermissions(), GALLERY_MEDIA_PERMISSION)
                    }
                }
            }
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                dialog.dismiss()
                showChildDriveBackupConsent()
            }
        }
        dialog.show()
    }

    /**
     * Separate child-side consent for future automatic Drive backup. The complete
     * MediaStore snapshot is captured on IO before enabling the tracker, so media
     * already on the device is never added to the new-file upload queue.
     */
    /**
     * A complete backup baseline must see the full image and video library.
     * Android 14's selected-media permission is intentionally insufficient:
     * otherwise an older, previously unselected file could later look new.
     */
    private fun hasFullGalleryMediaAccessForBackup(): Boolean {
        return if (Build.VERSION.SDK_INT >= 33) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun updateDriveBackupConsentOnServer(enabled: Boolean) {
        if (currentRole != "child") return
        val code = getSharedPreferences("parent_audio", MODE_PRIVATE).getString("pair_code", null)
        if (code.isNullOrBlank()) return
        db.collection("pairings").document(code)
            .update("driveBackupEnabled", enabled, "driveBackupUpdatedAt", Timestamp.now())
            .addOnFailureListener { error ->
                Toast.makeText(
                    this,
                    "Persetujuan backup tersimpan di HP ini, tetapi status server belum diperbarui: ${error.localizedMessage ?: "periksa koneksi"}",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    private fun showChildDriveBackupConsent() {
        if (currentRole != "child") return
        val prefs = getSharedPreferences("parent_audio", MODE_PRIVATE)
        val enabled = prefs.getBoolean("child_drive_backup_consent", false)
        val message = if (enabled) {
            "Persetujuan backup foto dan video baru sedang aktif. File yang sudah ada sebelum persetujuan tidak dimasukkan ke antrean. File baru akan dikirim saat HP orang tua terhubung dan Google Drive telah diotorisasi. Anda dapat mencabut persetujuan kapan saja."
        } else {
            "Jika disetujui, hanya foto dan video yang ditambahkan SETELAH persetujuan ini yang akan dipersiapkan untuk backup ke Google Drive orang tua. Semua file lama tetap hanya tampil di Galeri Anak dan tidak akan diunggah. Persetujuan ini terpisah dari Berbagi Galeri dan dapat dicabut kapan saja. Pengiriman dilakukan melalui koneksi P2P dan hanya diunggah setelah akun Google Drive orang tua diotorisasi."
        }
        SantetHpDialogBuilder()
            .setTitle(if (enabled) "Persetujuan Backup Drive aktif" else "Persetujuan Backup Google Drive")
            .setMessage(message)
            .setNegativeButton("Tutup", null)
            .setPositiveButton(if (enabled) "Cabut persetujuan" else "Saya setuju • Catat file lama sebagai awal") { _, _ ->
                if (enabled) {
                    GalleryBackupTracker(this).disable()
                    WorkManager.getInstance(this).cancelUniqueWork("santethp_gallery_backup_discovery")
                    prefs.edit().putBoolean("child_drive_backup_consent", false).apply()
                    updateDriveBackupConsentOnServer(false)
                    if (!prefs.getBoolean("child_gallery_sharing_enabled", false)) {
                        stopChildGalleryP2p()
                    }
                    updateChildGalleryPanel()
                    Toast.makeText(this, "Persetujuan Backup Drive dicabut.", Toast.LENGTH_LONG).show()
                } else if (!hasFullGalleryMediaAccessForBackup()) {
                    Toast.makeText(this, "Backup memerlukan izin akses penuh ke foto DAN video agar file lama yang belum dipilih tidak keliru dianggap file baru. Atur izin media penuh melalui pengaturan Android.", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, "Sedang mencatat galeri yang sudah ada sebagai batas awal backup…", Toast.LENGTH_LONG).show()
                    activityScope.launch {
                        try {
                            withContext(Dispatchers.IO) {
                                val repository = GalleryMediaRepository(this@MainActivity)
                                GalleryBackupTracker(this@MainActivity).enableWithCompleteBaseline(repository)
                            }
                            prefs.edit().putBoolean("child_drive_backup_consent", true).apply()
                            updateDriveBackupConsentOnServer(true)
                            GalleryBackupDiscoveryWorker.schedule(this@MainActivity)
                            // Start the foreground P2P service under backup consent
                            // without granting gallery-browsing consent.
                            startChildGalleryP2pIfAllowed()
                            updateChildGalleryPanel()
                            Toast.makeText(this@MainActivity, "Persetujuan tersimpan. File lama dikecualikan dari antrean backup.", Toast.LENGTH_LONG).show()
                            showChildDriveBackupConsent()
                        } catch (error: Exception) {
                            GalleryBackupTracker(this@MainActivity).disable()
                            Toast.makeText(this@MainActivity, "Gagal mencatat batas awal galeri: ${error.localizedMessage ?: "coba lagi"}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
            .show()
    }

    private fun showGaleriBackupMenu() {
        if (currentRole != "parent") return
        val options = arrayOf("Galeri Anak", "Kirim ke HP Anak", "Backup Google Drive")
        SantetHpDialogBuilder().setTitle("Galeri & Backup")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showParentGalleryStatus()
                    1 -> showSendMediaToChildInfo()
                    2 -> showGoogleDriveBackupSettings()
                }
            }.setNegativeButton("Tutup", null).show()
    }

    private fun showSendMediaToChildInfo() {
        if (currentRole != "parent") return
        val code = selectedChildCode
        if (code.isNullOrBlank()) {
            Toast.makeText(this, "Pilih HP anak terlebih dahulu.", Toast.LENGTH_LONG).show()
            return
        }
        if (getSharedPreferences("parent_audio", MODE_PRIVATE)
                .getBoolean("parent_drive_backup_service_active", false)
        ) {
            Toast.makeText(this, "Hentikan Backup Google Drive terlebih dahulu agar koneksi P2P tidak bertabrakan.", Toast.LENGTH_LONG).show()
            return
        }
        if (childSnapshots[code]?.getBoolean("parentMediaReceiveEnabled") != true) {
            SantetHpDialogBuilder().setTitle("Penerimaan belum diaktifkan")
                .setMessage("Aktifkan dan setujui terlebih dahulu menu Penerimaan dari HP Orang Tua pada HP anak. Setelah aktif, foto/video dapat disimpan otomatis tanpa persetujuan per file.")
                .setPositiveButton("Mengerti", null).show()
            return
        }
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "video/*"))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(intent, PARENT_MEDIA_PICKER)
    }

    private fun startParentMediaTransfer(uri: Uri) {
        val code = selectedChildCode
        if (code.isNullOrBlank()) {
            statusText.text = "Pilih HP anak terlebih dahulu"
            return
        }
        if (childSnapshots[code]?.getBoolean("parentMediaReceiveEnabled") != true) {
            statusText.text = "Penerimaan dari orang tua sudah tidak aktif di HP anak"
            return
        }
        if (getSharedPreferences("parent_audio", MODE_PRIVATE)
                .getBoolean("parent_drive_backup_service_active", false)
        ) {
            statusText.text = "Hentikan Backup Google Drive terlebih dahulu"
            return
        }
        parentGalleryDialog?.dismiss()
        stopParentGalleryP2p()
        stopParentMediaTransfer()
        parentSendStarted = false
        pendingParentSendUri = uri
        val resolver = contentResolver
        val mime = resolver.getType(uri).orEmpty()
        val name = try {
            resolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        } catch (_: Exception) { null } ?: "SantetHP-" + System.currentTimeMillis()
        if (!mime.startsWith("image/") && !mime.startsWith("video/")) {
            statusText.text = "File yang dipilih bukan foto atau video"
            return
        }
        pendingParentSendName = name
        pendingParentSendMime = mime
        statusText.text = "Menghubungkan ke HP anak untuk mengirim $name…"

        lateinit var manager: GalleryP2pManager
        val protocol = ParentToChildMediaProtocol(
            context = applicationContext,
            role = GalleryP2pManager.Role.PARENT,
            sendFrame = { bytes, binary -> manager.send(bytes, binary) },
            onStatus = { message ->
                runOnUiThread {
                    statusText.text = message
                    if (message.startsWith("Berhasil disimpan") ||
                        message.startsWith("HP anak gagal") ||
                        message.startsWith("HP anak menolak") ||
                        message.startsWith("Pengiriman gagal") ||
                        message.startsWith("Koneksi terputus")
                    ) {
                        rootLayout.postDelayed({ stopParentMediaTransfer() }, 1200)
                    }
                }
            }
        )
        parentSendProtocol = protocol
        manager = GalleryP2pManager(
            context = applicationContext,
            pairingCode = code,
            localRole = GalleryP2pManager.Role.PARENT,
            onState = { message ->
                runOnUiThread {
                    statusText.text = message
                    if (message.contains("P2P TERHUBUNG") && !parentSendStarted) {
                        parentSendStarted = true
                        val accepted = protocol.sendFromUri(uri, name, mime)
                        if (!accepted) {
                            statusText.text = "Transfer belum dapat dimulai"
                            rootLayout.postDelayed({ stopParentMediaTransfer() }, 1000)
                        }
                    } else if (message.contains("P2P gagal") || message.contains("gagal memulai") ||
                        message.contains("terputus") || message.contains("ditutup")
                    ) {
                        rootLayout.postDelayed({ stopParentMediaTransfer() }, 1500)
                    }
                }
            },
            onData = { bytes, binary -> protocol.onFrame(bytes, binary) }
        )
        parentSendP2pManager = manager
        manager.start()
    }

    private fun stopParentMediaTransfer() {
        parentSendProtocol?.close()
        parentSendProtocol = null
        parentSendP2pManager?.close()
        parentSendP2pManager = null
        parentSendStarted = false
        pendingParentSendUri = null
    }

    private fun showGoogleDriveBackupSettings() {
        if (currentRole != "parent") return
        val code = selectedChildCode
        if (code.isNullOrBlank()) {
            Toast.makeText(this, "Pilih HP anak terlebih dahulu.", Toast.LENGTH_LONG).show()
            return
        }
        val childName = childNames[code] ?: "HP Anak"
        val childKey = MessageDigest.getInstance("SHA-256")
            .digest(code.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        val backupPrefs = getSharedPreferences("parent_audio", MODE_PRIVATE)
        val status = TextView(this).apply {
            text = if (googleDriveAccessToken.isNullOrBlank()) "Google Drive belum diotorisasi pada sesi aplikasi ini." else "Izin Google Drive diberikan pada sesi ini. Token akses hanya disimpan sementara di memori."
            textSize = 13f
            setTextColor(Color.rgb(55, 68, 92))
            setPadding(dp(4), dp(8), dp(4), dp(8))
        }
        val statsView = TextView(this).apply {
            textSize = 13f
            setTextColor(Color.rgb(35, 53, 88))
            setPadding(dp(4), dp(4), dp(4), dp(8))
        }
        val folderNameInput = EditText(this).apply {
            hint = "Nama folder anak di Google Drive"
            setSingleLine(true)
            setText(backupPrefs.getString("parent_drive_child_folder_name_$childKey", childName))
        }
        val wifiOnlyCheckBox = CheckBox(this).apply {
            text = "Backup hanya saat HP anak dan HP orang tua terhubung ke Wi-Fi"
            isChecked = childSnapshots[code]?.getBoolean("driveBackupWifiOnly") ?: true
            setTextColor(Color.rgb(35, 53, 88))
            textSize = 13f
        }
        var restoringWifiCheck = false
        fun refreshBackupStats() {
            val successful = backupPrefs.getInt("parent_drive_backup_success_count", 0)
            val failed = backupPrefs.getInt("parent_drive_backup_failed_count", 0)
            val active = backupPrefs.getBoolean("parent_drive_backup_service_active", false)
            val last = backupPrefs.getString("parent_drive_backup_last_result", null)
            statsView.text = "RINGKASAN BACKUP\\nBerhasil: $successful file   •   Gagal: $failed file\\nLayanan: " +
                (if (active) "AKTIF" else "TIDAK AKTIF") +
                (last?.let { "\\nTerakhir: $it" } ?: "")
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(4), dp(8), dp(4))
            addView(TextView(this@MainActivity).apply {
                text = "Perangkat: $childName"
                textSize = 14f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(Color.rgb(35, 53, 88))
            })
            addView(status)
            addView(statsView)
            addView(TextView(this@MainActivity).apply {
                text = "Nama folder khusus perangkat ini:"
                textSize = 12.5f
                setTextColor(Color.rgb(75, 88, 112))
            })
            addView(folderNameInput)
            addView(wifiOnlyCheckBox)
            addView(TextView(this@MainActivity).apply {
                text = "Backup hanya memasukkan foto dan video BARU setelah diaktifkan dan disetujui dari HP anak. File lama tetap hanya tampil di Galeri Anak dan tidak diunggah otomatis. Akun Google yang dipilih adalah akun Drive milik orang tua.\n\nSetelah akun terhubung, layanan backup berjalan di latar depan. Jika token Google kedaluwarsa, hubungkan ulang akun untuk melanjutkan antrean."
                textSize = 12.5f
                setTextColor(Color.rgb(75, 88, 112))
                setPadding(dp(4), dp(4), dp(4), dp(8))
            })
        }
        val dialog = SantetHpDialogBuilder().setTitle("Backup Google Drive")
            .setView(popupContentCard(content)).setPositiveButton("Hubungkan Google Drive", null)
            .setNegativeButton("Hentikan backup", null)
            .setNeutralButton("Simpan nama folder", null).create()
        val statsRefresh = object : Runnable {
            override fun run() {
                if (!dialog.isShowing) return
                refreshBackupStats()
                rootLayout.postDelayed(this, 1500L)
            }
        }
        dialog.setOnShowListener {
            refreshBackupStats()
            rootLayout.post(statsRefresh)
            dialog.setOnDismissListener { rootLayout.removeCallbacks(statsRefresh) }
            wifiOnlyCheckBox.setOnCheckedChangeListener { _, enabled ->
                if (restoringWifiCheck) return@setOnCheckedChangeListener
                val previous = !enabled
                backupPrefs.edit().putBoolean("drive_backup_wifi_only", enabled).apply()
                db.collection("pairings").document(code)
                    .update("driveBackupWifiOnly", enabled)
                    .addOnSuccessListener {
                        status.text = if (enabled) {
                            "Pengaturan tersimpan • backup menunggu Wi-Fi pada kedua HP."
                        } else {
                            "Pengaturan tersimpan • backup boleh memakai Wi-Fi atau data seluler."
                        }
                    }
                    .addOnFailureListener { error ->
                        restoringWifiCheck = true
                        wifiOnlyCheckBox.isChecked = previous
                        backupPrefs.edit().putBoolean("drive_backup_wifi_only", previous).apply()
                        restoringWifiCheck = false
                        status.text = "Pengaturan jaringan gagal disimpan: ${error.localizedMessage ?: "periksa koneksi"}"
                    }
            }
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                val newName = folderNameInput.text.toString().trim()
                if (newName.isBlank() || newName.length > 80) {
                    folderNameInput.error = "Nama folder harus 1–80 karakter"
                    return@setOnClickListener
                }
                backupPrefs.edit()
                    .putString("parent_drive_child_folder_name_$childKey", newName)
                    .putBoolean("parent_drive_child_folder_rename_pending_$childKey", true)
                    .apply()
                val folderId = backupPrefs.getString("parent_drive_child_folder_id_$childKey", null)
                val token = googleDriveAccessToken
                if (!folderId.isNullOrBlank() && !token.isNullOrBlank()) {
                    status.text = "Mengubah nama folder Google Drive..."
                    Thread {
                        try {
                            GoogleDriveUploader(token).renameFolder(folderId, newName)
                            backupPrefs.edit().putBoolean("parent_drive_child_folder_rename_pending_$childKey", false).apply()
                            runOnUiThread { status.text = "Nama folder berhasil diubah menjadi: $newName" }
                        } catch (error: Exception) {
                            runOnUiThread { status.text = "Nama disimpan, tetapi perubahan Drive menunggu koneksi/otorisasi: ${error.localizedMessage ?: "coba lagi"}" }
                        }
                    }.start()
                } else {
                    status.text = "Nama folder disimpan. Nama ini akan digunakan saat folder anak dibuat atau backup berikutnya berjalan."
                }
            }
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener {
                startService(Intent(this, ParentDriveBackupService::class.java).setAction(ParentDriveBackupService.ACTION_STOP))
                googleDriveAccessToken = null
                status.text = "Layanan backup dihentikan. Persetujuan di HP anak tetap tersimpan dan dapat dihubungkan kembali kapan saja."
            }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                status.text = "Meminta izin akses Google Drive..."
                val authorization = GoogleDriveAuthorization(this)
                googleDriveAuthorization = authorization
                authorization.authorize { token, error ->
                    runOnUiThread {
                        if (token.isNullOrBlank()) {
                            status.text = "Belum terhubung: ${error ?: "izin tidak diberikan"}"
                            googleDriveAccessToken = null
                        } else {
                            googleDriveAccessToken = token
                            getSharedPreferences("parent_audio", MODE_PRIVATE).edit()
                                .putLong("parent_drive_backup_authorized_at", System.currentTimeMillis())
                                .putBoolean("parent_drive_backup_reauth_required", false)
                                .putString("parent_drive_backup_pair_code", code)
                                .putString("parent_drive_backup_child_name", folderNameInput.text.toString().trim().ifBlank { childName })
                                .putString("parent_drive_child_folder_name_$childKey", folderNameInput.text.toString().trim().ifBlank { childName })
                                .apply()
                            googleDriveBackupFolderId = null
                            status.text = "Izin Google Drive berhasil diberikan untuk sesi ini. Token tidak disimpan permanen."
                            Toast.makeText(this, "Izin Google Drive berhasil diberikan.", Toast.LENGTH_LONG).show()
                            if (childSnapshots[code]?.getBoolean("driveBackupEnabled") == true) {
                                // The gallery dialog owns a separate parent P2P session.
                                // Close it before the multi-child backup scheduler takes over.
                                if (parentGalleryDialog?.isShowing == true) {
                                    parentGalleryDialog?.dismiss()
                                    stopParentGalleryP2p()
                                }
                                childSnapshots[code]?.let {
                                    maybeStartParentDriveBackupForChild(code, it)
                                }
                                status.text = "Google Drive terhubung. Antrean backup otomatis bergantian sedang disiapkan."
                            } else {
                                status.text = "Google Drive terhubung. Persetujuan backup belum aktif dari HP anak."
                            }
                        }
                    }
                }
            }
        }
        dialog.show()
    }
    private fun showParentGalleryStatus() {
        if (currentRole != "parent") return
        if (parentSendProtocol != null) {
            Toast.makeText(this, "Selesaikan pengiriman foto/video ke HP anak terlebih dahulu.", Toast.LENGTH_LONG).show()
            return
        }
        val code = selectedChildCode
        if (code.isNullOrBlank()) {
            Toast.makeText(this, "Pilih HP anak terlebih dahulu.", Toast.LENGTH_LONG).show()
            return
        }
        val backupServiceActive = getSharedPreferences("parent_audio", MODE_PRIVATE)
            .getBoolean("parent_drive_backup_service_active", false)
        if (backupServiceActive && parentDriveBackupBinder == null) {
            if (!parentDriveBackupBindRequested) {
                parentDriveBackupBindRequested = bindService(
                    Intent(this, ParentDriveBackupService::class.java),
                    parentDriveBackupConnection,
                    BIND_AUTO_CREATE
                )
                parentDriveBackupBound = parentDriveBackupBindRequested
            }
            Toast.makeText(this, "Menghubungkan Galeri Anak ke layanan backup bersama…", Toast.LENGTH_SHORT).show()
            return
        }
        val enabled = childSnapshots[code]?.getBoolean("gallerySharingEnabled") == true
        val backupEnabled = childSnapshots[code]?.getBoolean("driveBackupEnabled") == true
        val name = childNames[code] ?: "HP Anak"
        if (!enabled && !backupEnabled) {
            SantetHpDialogBuilder()
                .setTitle(if (enabled) "Galeri • " + name else "Backup Google Drive • " + name)
                .setMessage("Berbagi galeri belum diaktifkan dari HP anak. Minta anak membuka panel BERBAGI GALERI HP INI, membaca penjelasan, lalu menyetujui izin media Android.")
                .setPositiveButton("Mengerti", null)
                .show()
            return
        }

        stopParentGalleryP2p()
        val status = TextView(this).apply {
            text = "Menyiapkan koneksi P2P langsung…"
            textSize = 13f
            setTextColor(Color.rgb(65, 82, 110))
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(12))
        }
        val scroll = ScrollView(this).apply {
            addView(list)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(440)
            )
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(status)
            addView(scroll)
        }
        parentGalleryStatus = status
        parentGalleryList = list
        val dialog = SantetHpDialogBuilder()
            .setTitle("Galeri • " + name)
            .setView(popupContentCard(content))
            .setNegativeButton("Tutup", null)
            .setNeutralButton("Muat ulang / sambung", null)
            .create()
        parentGalleryDialog = dialog
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                list.removeAllViews()
                parentGalleryRows.clear()
                parentGalleryMediaItems.clear()
                parentGalleryThumbQueue.clear()
                parentGalleryThumbInFlight = null
                parentGalleryIndexRequested = if (parentGalleryUsingBackupService) parentDriveBackupBinder?.requestGalleryIndex() == true else parentGalleryProtocol?.requestIndex() == true
                if (parentGalleryIndexRequested) {
                    status.text = "Meminta daftar foto dan video terbaru…"
                } else {
                    status.text = "Sesi P2P tidak aktif. Membuka ulang koneksi galeri…"
                    dialog.dismiss()
                    rootLayout.post { showParentGalleryStatus() }
                }
            }
        }
        dialog.setOnDismissListener {
            if (parentGalleryUsingBackupService) {
                parentDriveBackupBinder?.closeGallery()
                parentGalleryUsingBackupService = false
            } else {
                stopParentGalleryP2p()
            }
            parentGalleryDialog = null
            parentGalleryStatus = null
            parentGalleryList = null
        }
        dialog.show()

        if (backupServiceActive) {
            parentGalleryUsingBackupService = backupServiceActive
            parentDriveBackupBinder?.attachGalleryCallbacks(
                onMediaIndex = { items ->
                    runOnUiThread {
                        if (parentGalleryDialog?.isShowing != true) return@runOnUiThread
                        list.removeAllViews()
                        parentGalleryRows.clear()
                        parentGalleryMediaItems.clear()
                        parentGalleryThumbQueue.clear()
                        parentGalleryThumbInFlight = null
                        parentGalleryIndexRequested = true
                        items.forEach { parentGalleryMediaItems[it.key] = it }
                        if (items.isEmpty()) {
                            list.addView(TextView(this).apply {
                                text = "Belum ada foto atau video yang dapat dibagikan."
                                textSize = 13f
                                setPadding(dp(12), dp(24), dp(12), dp(24))
                            })
                        } else {
                            items.forEach { item ->
                                val row = LinearLayout(this).apply {
                                    orientation = LinearLayout.HORIZONTAL
                                    gravity = Gravity.CENTER_VERTICAL
                                    setPadding(dp(7), dp(6), dp(7), dp(6))
                                    background = GradientDrawable().apply {
                                        setColor(Color.rgb(250, 253, 251))
                                        cornerRadius = dp(13).toFloat()
                                        setStroke(dp(1), Color.rgb(216, 231, 222))
                                    }
                                    layoutParams = LinearLayout.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT, dp(80)
                                    ).apply { setMargins(0, 0, 0, dp(6)) }
                                }
                                val image = ImageView(this).apply {
                                    scaleType = ImageView.ScaleType.CENTER_CROP
                                    setBackgroundColor(Color.rgb(235, 245, 239))
                                    layoutParams = LinearLayout.LayoutParams(dp(66), dp(66))
                                }
                                val info = TextView(this).apply {
                                    text = (if (item.kind == "VIDEO") "▶  " else "▣  ") +
                                        item.name + "\n" +
                                        android.text.format.Formatter.formatFileSize(this@MainActivity, item.sizeBytes)
                                    textSize = 12f
                                    setTextColor(Color.rgb(31, 57, 43))
                                    setPadding(dp(10), 0, dp(4), 0)
                                    maxLines = 2
                                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                                }
                                row.addView(image)
                                row.addView(info)
                                row.setOnClickListener {
                                    SantetHpDialogBuilder()
                                        .setTitle(item.name)
                                        .setMessage(
                                            "Ukuran asli: " + android.text.format.Formatter.formatFileSize(this, item.sizeBytes) +
                                                "\nFile asli hanya akan diminta setelah Anda memilih tombol unduh. Transfer dibatasi 200 MB dan diverifikasi dengan SHA-256."
                                        )
                                        .setNegativeButton("Batal", null)
                                        .setPositiveButton("Unduh HD & simpan") { _, _ ->
                                            if (Build.VERSION.SDK_INT < 29) {
                                                Toast.makeText(this, "Simpan langsung ke galeri memerlukan Android 10 atau lebih baru.", Toast.LENGTH_LONG).show()
                                            } else if (item.sizeBytes <= 0 || item.sizeBytes > 200L * 1024L * 1024L) {
                                                Toast.makeText(this, "File kosong atau lebih besar dari batas transfer 200 MB.", Toast.LENGTH_LONG).show()
                                            } else {
                                                val savedPrefs = getSharedPreferences("parent_gallery_cache", MODE_PRIVATE)
                                                val prefKey = gallerySavedUriPreferenceKey(item.key)
                                                val savedUriText = savedPrefs.getString(prefKey, null)
                                                val savedUri = savedUriText?.let { Uri.parse(it) }
                                                val cachedFileExists = if (savedUri != null) {
                                                    try {
                                                        contentResolver.openFileDescriptor(savedUri, "r")?.use { true } ?: false
                                                    } catch (_: Exception) { false }
                                                } else false
                                                if (cachedFileExists && savedUri != null) {
                                                    openSavedGalleryMedia(savedUri, item.mime)
                                                } else {
                                                    if (savedUriText != null) savedPrefs.edit().remove(prefKey).apply()
                                                    if (!(if (parentGalleryUsingBackupService) parentDriveBackupBinder?.requestGalleryOriginal(item.key) == true else parentGalleryProtocol?.requestOriginal(item.key) == true)) {
                                                        Toast.makeText(this, "Permintaan unduh belum terkirim. Periksa koneksi P2P.", Toast.LENGTH_LONG).show()
                                                    } else {
                                                        status.text = "Meminta file asli: " + item.name
                                                    }
                                                }
                                            }
                                        }
                                        .show()
                                }
                                list.addView(row)
                                parentGalleryRows[item.key] = row
                                val cachedThumb = galleryThumbnailCacheFile(item.key)
                                if (cachedThumb.isFile && cachedThumb.length() > 0) {
                                    val cachedBitmap = BitmapFactory.decodeFile(cachedThumb.absolutePath)
                                    if (cachedBitmap != null) image.setImageBitmap(cachedBitmap)
                                    else parentGalleryThumbQueue.addLast(item.key)
                                } else {
                                    parentGalleryThumbQueue.addLast(item.key)
                                }
                            }
                            requestNextParentGalleryThumbnail()
                        }
                        status.text = "Daftar diterima: " + items.size +
                            " media • mengambil thumbnail satu per satu"
                    }
                },
                onThumbnail = { key, bytes ->
                    runOnUiThread {
                        if (parentGalleryDialog?.isShowing != true) return@runOnUiThread
                        if (bytes.isNotEmpty()) {
                            val cachedThumb = galleryThumbnailCacheFile(key)
                            try {
                                cachedThumb.parentFile?.mkdirs()
                                cachedThumb.writeBytes(bytes)
                                trimGalleryThumbnailCache()
                            } catch (_: Exception) {
                                // Keep the in-memory preview even if the local cache is full.
                            }
                            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            val row = parentGalleryRows[key]
                            val image = row?.getChildAt(0) as? ImageView
                            if (bitmap != null) image?.setImageBitmap(bitmap)
                        }
                        if (parentGalleryThumbInFlight == key) parentGalleryThumbInFlight = null
                        requestNextParentGalleryThumbnail()
                    }
                },
                onStatus = { message ->
                    runOnUiThread {
                        if (parentGalleryDialog?.isShowing == true) status.text = message
                    }
                },
                onOriginalReady = { key, file, fileName, mime ->
                    runOnUiThread {
                        try {
                            val uri = saveGalleryOriginalToDevice(file, fileName, mime)
                            if (uri != null) {
                                getSharedPreferences("parent_gallery_cache", MODE_PRIVATE).edit()
                                    .putString(gallerySavedUriPreferenceKey(key), uri.toString()).apply()
                                status.text = "File asli tersimpan di galeri HP orang tua."
                                if (!openSavedGalleryMedia(uri, mime)) {
                                    Toast.makeText(this, "File tersimpan di galeri, tetapi tidak ada aplikasi untuk membukanya.", Toast.LENGTH_LONG).show()
                                }
                            }
                        } catch (error: Exception) {
                            file.delete()
                            status.text = "Gagal menyimpan file: " + (error.localizedMessage ?: "kesalahan")
                            Toast.makeText(this, status.text, Toast.LENGTH_LONG).show()
                        }
                    }
                },
                onState = { message ->
                    runOnUiThread {
                        if (parentGalleryDialog?.isShowing == true) status.text = message
                        if (message.contains("P2P TERHUBUNG") &&
                            childSnapshots[code]?.getBoolean("gallerySharingEnabled") == true &&
                            !parentGalleryIndexRequested
                        ) {
                            parentGalleryIndexRequested = parentDriveBackupBinder?.requestGalleryIndex() == true
                        }
                        if (message.contains("terputus") || message.contains("ditutup") || message.contains("gagal")) {
                            status.text = message
                        }
                    }
                }
            )
            if (parentDriveBackupBinder?.openGallery(code, name) != true) {
                status.text = "Layanan backup belum siap membuka galeri. Coba muat ulang sebentar lagi."
            }
            parentGalleryProtocol = null
            parentGalleryP2pManager = null
        } else {
            parentGalleryUsingBackupService = false
            lateinit var manager: GalleryP2pManager
            lateinit var protocol: GalleryP2pGalleryProtocol
            protocol = GalleryP2pGalleryProtocol(
                context = this,
                role = GalleryP2pManager.Role.PARENT,
                sendFrame = { bytes, binary -> parentGalleryP2pManager?.send(bytes, binary) ?: false },
                onMediaIndex = { items ->
                    runOnUiThread {
                        if (parentGalleryDialog?.isShowing != true) return@runOnUiThread
                        list.removeAllViews()
                        parentGalleryRows.clear()
                        parentGalleryMediaItems.clear()
                        parentGalleryThumbQueue.clear()
                        parentGalleryThumbInFlight = null
                        parentGalleryIndexRequested = true
                        items.forEach { parentGalleryMediaItems[it.key] = it }
                        if (items.isEmpty()) {
                            list.addView(TextView(this).apply {
                                text = "Belum ada foto atau video yang dapat dibagikan."
                                textSize = 13f
                                setPadding(dp(12), dp(24), dp(12), dp(24))
                            })
                        } else {
                            items.forEach { item ->
                                val row = LinearLayout(this).apply {
                                    orientation = LinearLayout.HORIZONTAL
                                    gravity = Gravity.CENTER_VERTICAL
                                    setPadding(dp(7), dp(6), dp(7), dp(6))
                                    background = GradientDrawable().apply {
                                        setColor(Color.WHITE)
                                        cornerRadius = dp(12).toFloat()
                                        setStroke(dp(1), Color.rgb(226, 233, 243))
                                    }
                                    layoutParams = LinearLayout.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT, dp(80)
                                    ).apply { setMargins(0, 0, 0, dp(6)) }
                                }
                                val image = ImageView(this).apply {
                                    scaleType = ImageView.ScaleType.CENTER_CROP
                                    setBackgroundColor(Color.rgb(235, 240, 247))
                                    layoutParams = LinearLayout.LayoutParams(dp(66), dp(66))
                                }
                                val info = TextView(this).apply {
                                    text = (if (item.kind == "VIDEO") "▶  " else "▣  ") +
                                        item.name + "\n" +
                                        android.text.format.Formatter.formatFileSize(this@MainActivity, item.sizeBytes)
                                    textSize = 12f
                                    setTextColor(Color.rgb(39, 53, 78))
                                    setPadding(dp(10), 0, dp(4), 0)
                                    maxLines = 2
                                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                                }
                                row.addView(image)
                                row.addView(info)
                                row.setOnClickListener {
                                    SantetHpDialogBuilder()
                                        .setTitle(item.name)
                                        .setMessage(
                                            "Ukuran asli: " + android.text.format.Formatter.formatFileSize(this, item.sizeBytes) +
                                                "\nFile asli hanya akan diminta setelah Anda memilih tombol unduh. Transfer dibatasi 200 MB dan diverifikasi dengan SHA-256."
                                        )
                                        .setNegativeButton("Batal", null)
                                        .setPositiveButton("Unduh HD & simpan") { _, _ ->
                                            if (Build.VERSION.SDK_INT < 29) {
                                                Toast.makeText(this, "Simpan langsung ke galeri memerlukan Android 10 atau lebih baru.", Toast.LENGTH_LONG).show()
                                            } else if (item.sizeBytes <= 0 || item.sizeBytes > 200L * 1024L * 1024L) {
                                                Toast.makeText(this, "File kosong atau lebih besar dari batas transfer 200 MB.", Toast.LENGTH_LONG).show()
                                            } else {
                                                val savedPrefs = getSharedPreferences("parent_gallery_cache", MODE_PRIVATE)
                                                val prefKey = gallerySavedUriPreferenceKey(item.key)
                                                val savedUriText = savedPrefs.getString(prefKey, null)
                                                val savedUri = savedUriText?.let { Uri.parse(it) }
                                                val cachedFileExists = if (savedUri != null) {
                                                    try {
                                                        contentResolver.openFileDescriptor(savedUri, "r")?.use { true } ?: false
                                                    } catch (_: Exception) { false }
                                                } else false
                                                if (cachedFileExists && savedUri != null) {
                                                    openSavedGalleryMedia(savedUri, item.mime)
                                                } else {
                                                    if (savedUriText != null) savedPrefs.edit().remove(prefKey).apply()
                                                    if (parentGalleryProtocol?.requestOriginal(item.key) != true) {
                                                        Toast.makeText(this, "Permintaan unduh belum terkirim. Periksa koneksi P2P.", Toast.LENGTH_LONG).show()
                                                    } else {
                                                        status.text = "Meminta file asli: " + item.name
                                                    }
                                                }
                                            }
                                        }
                                        .show()
                                }
                                list.addView(row)
                                parentGalleryRows[item.key] = row
                                val cachedThumb = galleryThumbnailCacheFile(item.key)
                                if (cachedThumb.isFile && cachedThumb.length() > 0) {
                                    val cachedBitmap = BitmapFactory.decodeFile(cachedThumb.absolutePath)
                                    if (cachedBitmap != null) image.setImageBitmap(cachedBitmap)
                                    else parentGalleryThumbQueue.addLast(item.key)
                                } else {
                                    parentGalleryThumbQueue.addLast(item.key)
                                }
                            }
                            requestNextParentGalleryThumbnail()
                        }
                        status.text = "Daftar diterima: " + items.size +
                            " media • mengambil thumbnail satu per satu"
                    }
                },
                onThumbnail = { key, bytes ->
                    runOnUiThread {
                        if (parentGalleryDialog?.isShowing != true) return@runOnUiThread
                        if (bytes.isNotEmpty()) {
                            val cachedThumb = galleryThumbnailCacheFile(key)
                            try {
                                cachedThumb.parentFile?.mkdirs()
                                cachedThumb.writeBytes(bytes)
                                trimGalleryThumbnailCache()
                            } catch (_: Exception) {
                                // Keep the in-memory preview even if the local cache is full.
                            }
                            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            val row = parentGalleryRows[key]
                            val image = row?.getChildAt(0) as? ImageView
                            if (bitmap != null) image?.setImageBitmap(bitmap)
                        }
                        if (parentGalleryThumbInFlight == key) parentGalleryThumbInFlight = null
                        requestNextParentGalleryThumbnail()
                    }
                },
                onStatus = { message ->
                    runOnUiThread {
                        if (parentGalleryDialog?.isShowing == true) status.text = message
                    }
                },
                canReceiveBackup = { !googleDriveAccessToken.isNullOrBlank() },
                onBackupFileReady = { key, file, fileName, mime, complete ->
                    val accessToken = googleDriveAccessToken
                    if (accessToken.isNullOrBlank()) {
                        file.delete()
                        runOnUiThread {
                            status.text = "File baru sudah diterima, tetapi akun Google Drive belum dihubungkan. Antrean anak tetap menunggu."
                            complete(false)
                        }
                    } else {
                        Thread {
                            try {
                                val uploader = GoogleDriveUploader(accessToken)
                                val folderId = googleDriveBackupFolderId ?: uploader.ensureBackupFolder().also {
                                    googleDriveBackupFolderId = it
                                }
                                uploader.uploadFile(
                                    file, fileName, mime, folderId, stableKey = key,
                                    progress = GoogleDriveUploader.ProgressListener { sent, total ->
                                        runOnUiThread {
                                            if (parentGalleryDialog?.isShowing == true) {
                                                status.text = "Mengunggah ke Google Drive: " +
                                                    android.text.format.Formatter.formatFileSize(this, sent) +
                                                    " / " + android.text.format.Formatter.formatFileSize(this, total)
                                            }
                                        }
                                    }
                                )
                                file.delete()
                                runOnUiThread {
                                    status.text = "Backup berhasil diunggah ke Google Drive: $fileName"
                                    complete(true)
                                }
                            } catch (error: Exception) {
                                file.delete()
                                runOnUiThread {
                                    status.text = "Upload Drive gagal: ${error.localizedMessage ?: "coba hubungkan ulang akun Google"}"
                                    complete(false)
                                }
                            }
                        }.start()
                    }
                },
                onOriginalReady = { key, file, fileName, mime ->
                    runOnUiThread {
                        try {
                            val uri = saveGalleryOriginalToDevice(file, fileName, mime)
                            if (uri != null) {
                                getSharedPreferences("parent_gallery_cache", MODE_PRIVATE).edit()
                                    .putString(gallerySavedUriPreferenceKey(key), uri.toString()).apply()
                                status.text = "File asli tersimpan di galeri HP orang tua."
                                if (!openSavedGalleryMedia(uri, mime)) {
                                    Toast.makeText(this, "File tersimpan di galeri, tetapi tidak ada aplikasi untuk membukanya.", Toast.LENGTH_LONG).show()
                                }
                            }
                        } catch (error: Exception) {
                            file.delete()
                            status.text = "Gagal menyimpan file: " + (error.localizedMessage ?: "kesalahan")
                            Toast.makeText(this, status.text, Toast.LENGTH_LONG).show()
                        }
                    }
                }
            )
            parentGalleryProtocol = protocol
            manager = GalleryP2pManager(
                context = this,
                pairingCode = code,
                localRole = GalleryP2pManager.Role.PARENT,
                onState = { message ->
                    runOnUiThread {
                        if (parentGalleryDialog?.isShowing == true) status.text = message
                        if (message.contains("P2P TERHUBUNG")) {
                            if (childSnapshots[code]?.getBoolean("gallerySharingEnabled") == true &&
                                !parentGalleryIndexRequested
                            ) {
                                parentGalleryIndexRequested = protocol.requestIndex()
                            }
                            if (childSnapshots[code]?.getBoolean("driveBackupEnabled") == true &&
                                !googleDriveAccessToken.isNullOrBlank()
                            ) {
                                protocol.requestNextBackup()
                            }
                        }
                        if (message.contains("terputus") || message.contains("ditutup") || message.contains("gagal")) {
                            protocol.cancelOriginal()
                        }
                    }
                },
                onData = { bytes, binary -> protocol.onFrame(bytes, binary) }
            )
            parentGalleryP2pManager = manager
            manager.start()
        }

    }

    private fun requestNextParentGalleryThumbnail() {
        if (parentGalleryThumbInFlight != null || parentGalleryDialog?.isShowing != true) return
        val next = parentGalleryThumbQueue.removeFirstOrNull() ?: return
        parentGalleryThumbInFlight = next
        val requested = if (parentGalleryUsingBackupService) {
            parentDriveBackupBinder?.requestGalleryThumbnail(next) == true
        } else {
            parentGalleryProtocol?.requestThumbnail(next) == true
        }
        if (!requested) {
            parentGalleryThumbInFlight = null
            parentGalleryThumbQueue.addFirst(next)
            parentGalleryStatus?.text = "Koneksi belum siap mengirim thumbnail. Ketuk Muat ulang setelah P2P tersambung."
        }
    }

    private fun gallerySavedUriPreferenceKey(key: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(key.toByteArray(Charsets.UTF_8))
        val hash = digest.joinToString("") { "%02x".format(it.toInt() and 0xff) }
        return "media_" + hash
    }

    private fun openSavedGalleryMedia(uri: Uri, mime: String): Boolean {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return try {
            startActivity(Intent.createChooser(intent, "Buka media"))
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun trimGalleryThumbnailCache(maxBytes: Long = 40L * 1024L * 1024L) {
        val directory = File(cacheDir, "gallery-thumbnails")
        val files = directory.listFiles()?.filter { it.isFile }?.sortedBy { it.lastModified() } ?: return
        var total = files.sumOf { it.length() }
        for (file in files) {
            if (total <= maxBytes) break
            val size = file.length()
            if (file.delete()) total -= size
        }
    }

    private fun galleryThumbnailCacheFile(key: String): File {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(key.toByteArray(Charsets.UTF_8))
        val fileKey = digest.joinToString("") { "%02x".format(it.toInt() and 0xff) }
        val directory = File(cacheDir, "gallery-thumbnails")
        if (!directory.exists()) directory.mkdirs()
        return File(directory, fileKey + ".jpg")
    }

    private fun saveGalleryOriginalToDevice(file: File, name: String, mime: String): Uri? {
        if (Build.VERSION.SDK_INT < 29) {
            file.delete()
            throw IllegalStateException("Simpan ke galeri memerlukan Android 10 atau lebih baru")
        }
        val safeName = name.substringAfterLast('/').replace(Regex("[\\\\/:*?\\\"<>|]"), "_")
            .ifBlank { "SantetHP-media" }
        val isVideo = mime.startsWith("video/")
        val collection = if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, safeName)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, if (isVideo) "Movies/SantetHP" else "Pictures/SantetHP")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = contentResolver.insert(collection, values)
            ?: throw IllegalStateException("MediaStore tidak dapat membuat file galeri")
        try {
            val output = contentResolver.openOutputStream(uri)
                ?: throw IllegalStateException("Tidak dapat membuka tujuan penyimpanan")
            output.use { stream -> file.inputStream().use { input -> input.copyTo(stream) } }
            val finalized = contentResolver.update(uri, ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }, null, null)
            if (finalized <= 0) throw IllegalStateException("File belum dapat dipublikasikan ke galeri")
            file.delete()
            return uri
        } catch (error: Exception) {
            contentResolver.delete(uri, null, null)
            throw error
        }
    }

    private fun stopParentGalleryP2p() {
        parentGalleryProtocol?.close()
        parentGalleryProtocol = null
        parentGalleryP2pManager?.close()
        parentGalleryP2pManager = null
        parentGalleryThumbQueue.clear()
        parentGalleryThumbInFlight = null
        parentGalleryIndexRequested = false
        parentGalleryRows.clear()
    }

    private fun showChildNotificationsDialog() {
        if (currentRole != "parent") return
        val code = selectedChildCode
        if (code.isNullOrBlank()) {
            Toast.makeText(this, "Pilih HP anak terlebih dahulu.", Toast.LENGTH_LONG).show()
            return
        }
        notificationHistoryListener?.remove()
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(8), dp(14), dp(8))
        }
        val scroll = ScrollView(this).apply {
            addView(list)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(460)
            )
        }
        val dialog = SantetHpDialogBuilder()
            .setTitle("Notifikasi • ${childNames[code] ?: "HP Anak"}")
            .setView(scroll)
            .setNegativeButton("Tutup", null)
            .setNeutralButton("Hapus riwayat", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                SantetHpDialogBuilder()
                    .setTitle("Hapus semua riwayat?")
                    .setMessage("Notifikasi yang tersimpan untuk perangkat ini akan dihapus permanen.")
                    .setNegativeButton("Batal", null)
                    .setPositiveButton("Hapus") { _, _ -> clearChildNotificationHistory(code) }
                    .show()
            }
        }
        notificationHistoryListener = db.collection("pairings").document(code)
            .collection("notifications")
            .orderBy("postedAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(100)
            .addSnapshotListener { snapshot, error ->
                list.removeAllViews()
                if (error != null) {
                    list.addView(TextView(this).apply {
                        text = "Gagal memuat notifikasi: ${error.localizedMessage ?: "periksa aturan Firestore"}"
                        textSize = 12f
                        setTextColor(Color.rgb(170, 55, 55))
                    })
                    return@addSnapshotListener
                }
                val documents = snapshot?.documents.orEmpty()
                if (documents.isEmpty()) {
                    list.addView(TextView(this).apply {
                        text = "Belum ada notifikasi. Pastikan HP anak online dan akses notifikasi SantetHP sudah aktif."
                        textSize = 13f
                        setTextColor(Color.rgb(104, 117, 140))
                        setPadding(dp(8), dp(20), dp(8), dp(20))
                    })
                    return@addSnapshotListener
                }
                val formatter = java.text.SimpleDateFormat("dd MMM yyyy, HH:mm:ss", java.util.Locale("id", "ID"))
                documents.forEach { document ->
                    val appName = document.getString("appName") ?: document.getString("packageName") ?: "Aplikasi"
                    val title = document.getString("title").orEmpty()
                    val body = document.getString("text").orEmpty()
                    if (title.isBlank() && body.isBlank()) return@forEach
                    val timestamp = document.getTimestamp("postedAt")
                    val whenText = timestamp?.toDate()?.let { formatter.format(it) } ?: "Waktu tidak tersedia"
                    val card = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(dp(11), dp(9), dp(11), dp(9))
                        background = GradientDrawable(
                            GradientDrawable.Orientation.TL_BR,
                            intArrayOf(Color.rgb(248, 251, 255), Color.rgb(237, 245, 255))
                        ).apply {
                            cornerRadius = dp(13).toFloat()
                            setStroke(dp(1), Color.rgb(177, 202, 238))
                        }
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                        ).apply { setMargins(0, dp(3), 0, dp(3)) }
                    }
                    card.addView(TextView(this).apply {
                        text = appName
                        textSize = 12f
                        setTypeface(null, android.graphics.Typeface.BOLD)
                        setTextColor(Color.rgb(51, 76, 132))
                    })
                    if (title.isNotBlank()) card.addView(TextView(this).apply {
                        text = title
                        textSize = 13f
                        setTypeface(null, android.graphics.Typeface.BOLD)
                        setTextColor(Color.rgb(35, 47, 72))
                        setPadding(0, dp(4), 0, 0)
                    })
                    if (body.isNotBlank()) card.addView(TextView(this).apply {
                        text = body
                        textSize = 12f
                        setTextColor(Color.rgb(62, 73, 94))
                        setPadding(0, dp(3), 0, 0)
                    })
                    card.addView(TextView(this).apply {
                        text = whenText
                        textSize = 10.5f
                        setTextColor(Color.rgb(120, 130, 148))
                        setPadding(0, dp(7), 0, 0)
                    })
                    list.addView(card)
                }
            }
        dialog.setOnDismissListener {
            notificationHistoryListener?.remove()
            notificationHistoryListener = null
        }
        dialog.show()
    }

    private fun clearChildNotificationHistory(code: String) {
        val collection = db.collection("pairings").document(code).collection("notifications")
        collection.limit(400).get().addOnSuccessListener { snapshot ->
            if (snapshot.isEmpty) {
                Toast.makeText(this, "Riwayat notifikasi sudah kosong.", Toast.LENGTH_SHORT).show()
                return@addOnSuccessListener
            }
            val batch = db.batch()
            snapshot.documents.forEach { batch.delete(it.reference) }
            batch.commit().addOnSuccessListener {
                if (snapshot.size() >= 400) clearChildNotificationHistory(code)
                else Toast.makeText(this, "Riwayat notifikasi berhasil dihapus.", Toast.LENGTH_LONG).show()
            }.addOnFailureListener {
                Toast.makeText(this, "Gagal menghapus riwayat notifikasi.", Toast.LENGTH_LONG).show()
            }
        }.addOnFailureListener {
            Toast.makeText(this, "Gagal membaca riwayat notifikasi.", Toast.LENGTH_LONG).show()
        }
    }

    private fun createChildActivityCard(icon: String, title: String, content: LinearLayout): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(11), dp(10), dp(11), dp(10))
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = dp(16).toFloat()
                setStroke(dp(1), Color.rgb(211, 230, 221))
            }
            elevation = dp(1).toFloat()
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, dp(4), 0, dp(6)) }
            val headingRow = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(7), dp(6), dp(7), dp(6))
                background = GradientDrawable(
                    GradientDrawable.Orientation.LEFT_RIGHT,
                    intArrayOf(Color.rgb(239, 248, 243), Color.rgb(246, 250, 255))
                ).apply {
                    cornerRadius = dp(11).toFloat()
                    setStroke(dp(1), Color.rgb(207, 226, 218))
                }
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 0, 0, dp(8)) }
            }
            headingRow.addView(TextView(this@MainActivity).apply {
                text = icon
                textSize = 15f
                gravity = Gravity.CENTER
                setTextColor(Color.rgb(25, 91, 67))
                background = GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    intArrayOf(Color.rgb(232, 243, 255), Color.rgb(239, 248, 243))
                ).apply {
                    cornerRadius = dp(9).toFloat()
                    setStroke(dp(1), Color.rgb(215, 229, 241))
                }
                layoutParams = LinearLayout.LayoutParams(dp(29), dp(29)).apply {
                    marginEnd = dp(8)
                }
            })
            headingRow.addView(TextView(this@MainActivity).apply {
                text = title
                textSize = 12f
                setTypeface(null, android.graphics.Typeface.BOLD)
                letterSpacing = 0.035f
                setTextColor(Color.rgb(36, 86, 62))
                gravity = Gravity.CENTER_VERTICAL
            })
            addView(headingRow)
            addView(content)
        }
    }

    private fun addChildActivityRow(container: LinearLayout, value: String, muted: Boolean = false) {
        val row = TextView(this).apply {
            text = value
            textSize = 11.8f
            setTextColor(if (muted) Color.rgb(112, 128, 119) else Color.rgb(43, 67, 51))
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(9), dp(6), dp(9), dp(6))
            background = GradientDrawable().apply {
                setColor(if (muted) Color.rgb(248, 251, 249) else Color.rgb(246, 250, 247))
                cornerRadius = dp(10).toFloat()
                setStroke(dp(1), if (muted) Color.rgb(229, 237, 231) else Color.rgb(225, 237, 229))
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, dp(1), 0, dp(1)) }
        }
        container.addView(row)
    }

    private fun addChildActivityDataRow(container: LinearLayout, label: String, value: String) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(9), dp(6), dp(9), dp(6))
            background = GradientDrawable().apply {
                setColor(Color.rgb(244, 249, 245))
                cornerRadius = dp(10).toFloat()
                setStroke(dp(1), Color.rgb(227, 237, 229))
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, dp(1), 0, dp(1)) }
        }
        row.addView(TextView(this).apply {
            text = label
            textSize = 11.5f
            setTextColor(Color.rgb(43, 67, 51))
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        row.addView(TextView(this).apply {
            text = value
            textSize = 10.8f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(Color.rgb(27, 105, 68))
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(dp(8), dp(4), dp(8), dp(4))
            background = GradientDrawable().apply {
                setColor(Color.rgb(231, 244, 235))
                cornerRadius = dp(8).toFloat()
                setStroke(dp(1), Color.rgb(205, 228, 211))
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = dp(8) }
        })
        container.addView(row)
    }

    private fun renderChildDeviceStatus(snapshot: DocumentSnapshot) {
        childUsageRowsContainer.removeAllViews()
        val permissionGranted = snapshot.getBoolean("appUsagePermissionGranted") == true
        val usageAt = snapshot.getTimestamp("appUsageAt")
        val usageAge = usageAt?.toDate()?.time?.let { System.currentTimeMillis() - it }
        val usageFresh = usageAge != null && usageAge in -60_000L..30 * 60 * 1000L
        val usageEnabled = snapshot.getBoolean("appUsageEnabled") == true
        if (!usageEnabled) {
            addChildActivityRow(childUsageRowsContainer, "Riwayat penggunaan belum diaktifkan dari HP anak", true)
        } else if (!permissionGranted) {
            addChildActivityRow(childUsageRowsContainer, "Menunggu izin Usage Access dari HP anak", true)
        } else {
            val apps = snapshot.get("appUsageSummary") as? List<*>
            if (apps.isNullOrEmpty()) {
                addChildActivityRow(childUsageRowsContainer, "Belum ada data penggunaan yang tercatat", true)
            } else {
                apps.take(8).forEach { item ->
                    val app = item as? Map<*, *> ?: return@forEach
                    val name = app["appName"] as? String ?: "Aplikasi"
                    val minutes = (app["foregroundMinutes"] as? Number)?.toInt() ?: 0
                    val duration = if (minutes >= 60) {
                        "${minutes / 60} jam ${minutes % 60} mnt"
                    } else {
                        "$minutes menit"
                    }
                    addChildActivityDataRow(childUsageRowsContainer, name, duration)
                }
            }
            addChildActivityRow(childUsageRowsContainer, if (usageFresh) "✓ Data penggunaan diperbarui" else "ⓘ Data penggunaan mungkin sudah lama", true)
        }

        childActivityRowsContainer.removeAllViews()
        if (snapshot.getBoolean("appActivityConsent") != true) {
            addChildActivityRow(childActivityRowsContainer, "Pemantauan belum diaktifkan dari HP anak", true)
        } else {
            val history = (snapshot.get("appActivityHistory") as? List<*>)
                .orEmpty()
                .mapNotNull { raw ->
                    val item = raw as? Map<*, *> ?: return@mapNotNull null
                    val name = item["appName"] as? String ?: return@mapNotNull null
                    val openedAt = item["lastOpenedAt"] as? com.google.firebase.Timestamp
                    name to openedAt
                }
                .take(10)

            if (history.isEmpty()) {
                val activeName = snapshot.getString("appActivityName")
                val activityAt = snapshot.getTimestamp("appActivityAt")
                if (activeName.isNullOrBlank()) {
                    addChildActivityRow(childActivityRowsContainer, "Menunggu pergantian aplikasi pertama", true)
                } else {
                    val activityTime = activityAt?.let {
                        android.text.format.DateFormat.getTimeFormat(this@MainActivity).format(it.toDate())
                    }
                    addChildActivityRow(childActivityRowsContainer, "Terakhir terdeteksi: $activeName${if (activityTime != null) " • $activityTime" else ""}")
                }
            } else {
                addChildActivityRow(childActivityRowsContainer, "${history.size} aplikasi terakhir dibuka", true)
                history.forEachIndexed { index, entry ->
                    val time = entry.second?.let {
                        android.text.format.DateFormat.getTimeFormat(this@MainActivity).format(it.toDate())
                    } ?: "waktu tidak tersedia"
                    addChildActivityDataRow(childActivityRowsContainer, "${index + 1}. ${entry.first}", time)
                }
                addChildActivityRow(childActivityRowsContainer, "Diurutkan dari yang paling baru", true)
            }
        }

        val blockConsent = snapshot.getBoolean("appBlockConsent") == true
        val nowForBlocks = System.currentTimeMillis()
        val activeBlockCount = (snapshot.get("blockedApps") as? List<*>)
            .orEmpty().count { raw ->
                val item = raw as? Map<*, *> ?: return@count false
                ((item["blockedUntilMillis"] as? Number)?.toLong() ?: 0L) > nowForBlocks
            }
        val installedApps = (snapshot.get("appBlockInstalledApps") as? List<*>)?.size ?: 0
        parentAppBlockSubtitle.text = if (!blockConsent) {
            "Menunggu persetujuan HP anak"
        } else {
            "$activeBlockCount aplikasi diblokir • $installedApps tersedia"
        }

        val latitude = snapshot.getDouble("childLatitude")
        val longitude = snapshot.getDouble("childLongitude")
        val accuracy = snapshot.getDouble("childLocationAccuracyM")
        val locationAt = snapshot.getTimestamp("childLocationAt")
        val sharingEnabled = snapshot.getBoolean("childLocationSharingEnabled") == true
        val locationAge = locationAt?.toDate()?.time?.let { System.currentTimeMillis() - it }
        val locationFresh = locationAge != null && locationAge in -60_000L..45 * 60 * 1000L
        if (latitude != null && longitude != null) {
            childLocationMapView.showLocation(
                latitude, longitude, accuracy, locationAt, sharingEnabled, locationFresh,
                safeZonesJson(snapshot)
            )
            renderSafeZoneList(snapshot)
        } else {
            childLocationMapView.showUnavailable(sharingEnabled)
            renderSafeZoneList(snapshot)
        }
    }

    private fun maybeStartParentDriveBackupForChild(code: String, snapshot: DocumentSnapshot) {
        if (currentRole != "parent" || googleDriveAccessToken.isNullOrBlank() ||
            parentSendProtocol != null
        ) return

        // Build a stable queue from every paired child whose own backup consent is active.
        val queue = org.json.JSONArray()
        childNames.keys.forEach { childCode ->
            val childSnapshot = childSnapshots[childCode] ?: return@forEach
            if (childSnapshot.getBoolean("driveBackupEnabled") == true) {
                queue.put(org.json.JSONObject()
                    .put("code", childCode)
                    .put("name", childNames[childCode] ?: "HP Anak"))
            }
        }
        if (queue.length() == 0) return

        val queueJson = queue.toString()
        val first = queue.optJSONObject(0) ?: return
        val firstCode = first.optString("code")
        val firstName = first.optString("name", "HP Anak")
        val prefs = getSharedPreferences("parent_audio", MODE_PRIVATE)
        if (prefs.getBoolean("parent_drive_backup_service_active", false) &&
            prefs.getString("parent_drive_backup_child_queue_json", null) == queueJson
        ) return

        if (parentGalleryDialog?.isShowing == true) {
            parentGalleryDialog?.dismiss()
            stopParentGalleryP2p()
        }
        prefs.edit()
            .putLong("parent_drive_backup_authorized_at", System.currentTimeMillis())
            .putString("parent_drive_backup_pair_code", firstCode)
            .putString("parent_drive_backup_child_name", firstName)
            .putString("parent_drive_backup_child_queue_json", queueJson)
            .apply()

        val intent = Intent(this, ParentDriveBackupService::class.java).apply {
            action = ParentDriveBackupService.ACTION_START
            putExtra(ParentDriveBackupService.EXTRA_PAIR_CODE, firstCode)
            putExtra(ParentDriveBackupService.EXTRA_ACCESS_TOKEN, googleDriveAccessToken)
            putExtra(ParentDriveBackupService.EXTRA_CHILD_NAME, firstName)
            putExtra(ParentDriveBackupService.EXTRA_CHILD_QUEUE_JSON, queueJson)
        }
        try {
            ContextCompat.startForegroundService(this, intent)
            statusText.text = "Backup otomatis bergantian untuk ${queue.length()} perangkat anak dimulai"
        } catch (error: Exception) {
            statusText.text = "Persetujuan backup aktif, tetapi layanan belum dapat dimulai: ${error.localizedMessage ?: "coba lagi"}"
        }
    }

    private fun listenForChild(code: String) {
        childListeners[code]?.remove()

        childListeners[code] = db.collection("pairings")
            .document(code)
            .addSnapshotListener { snapshot, error ->

                if (error != null) {
                    if (statusText.text != "Gagal memeriksa pasangan") {
                        statusText.text = "Gagal memeriksa pasangan"
                    }
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    val previousStatusAt = childSnapshots[code]?.getTimestamp("deviceStatusAt")
                    childSnapshots[code] = snapshot
                    maybeStartParentDriveBackupForChild(code, snapshot)
                    val childUid = snapshot.getString("childUid")
                    val connected = !childUid.isNullOrEmpty()
                    val connectionStateChanged = childConnected[code] != connected
                    val currentStatusAt = snapshot.getTimestamp("deviceStatusAt")
                    val deviceStatusChanged = previousStatusAt?.seconds != currentStatusAt?.seconds ||
                        previousStatusAt?.nanoseconds != currentStatusAt?.nanoseconds
                    childConnected[code] = connected
                    // Refresh presence labels only when pairing or the device heartbeat changes.
                    // This avoids rebuilding the list for unrelated Firestore updates.
                    if (connectionStateChanged || deviceStatusChanged) renderChildrenList()
                    if (selectedChildCode == code) updateSelectedChildButton()
                    if (selectedChildCode == code) {
                        latestPairingSnapshot = snapshot
                        renderChildAudioActivity(snapshot)
                        renderChildDeviceStatus(snapshot)
                        childAudioActivityText.removeCallbacks(staleStatusRunnable)
                        childAudioActivityText.postDelayed(staleStatusRunnable, 36_000L)
                    }
                    if (connected) {
                        if (selectedChildCode == null) selectedChildCode = code
                        if (selectedChildCode == code && activeRoomCode != code && !isConnecting) {
                            if (selectedChildCode == null) selectChild(code)
                            else {
                                codeText.text = code
                                statusText.text = "HP ${childNames[code] ?: "ANAK"} TERHUBUNG • AUDIO NONAKTIF"
                            }
                        }
                        if (!childNames[code].isNullOrBlank() &&
                            childNames[code]?.startsWith("Anak ") == true &&
                            renamePromptedCodes.add(code) && selectedChildCode == code
                        ) promptRenameChild(code)
                        if (currentRole == "parent" &&
                            rootLayout.findViewWithTag<View>("santethp_maps_page")?.visibility != View.VISIBLE &&
                            rootLayout.findViewWithTag<View>("santethp_brand_header")?.visibility != View.VISIBLE
                        ) {
                            showParentDashboard()
                        }
                    }
                }
            }
    }

    private fun showCodeDialog() {
        val input = EditText(this).apply {
            hint = "Masukkan 8 karakter kode"
            isSingleLine = true
        }

        SantetHpDialogBuilder()
            .setTitle("Hubungkan dengan Orang Tua")
            .setMessage("Masukkan kode dari HP orang tua.")
            .setView(popupContentCard(input))
            .setNegativeButton("Batal", null)
            .setPositiveButton("Hubungkan") { _, _ ->

                val code = input.text.toString()
                    .trim()
                    .uppercase()

                if (code.length != 8) {
                    statusText.text = "Kode harus 8 karakter"
                } else {
                    joinPairing(code)
                }
            }
            .show()
    }

    private fun joinPairing(code: String) {
        val uid = auth.currentUser?.uid

        if (uid == null) {
            statusText.text = "Firebase belum terhubung!"
            return
        }

        statusText.text = "Memeriksa kode..."

        val document = db.collection("pairings").document(code)

        document.get()
            .addOnSuccessListener { snapshot ->

                if (!snapshot.exists()) {
                    statusText.text = "Kode tidak ditemukan atau kedaluwarsa"
                    return@addOnSuccessListener
                }

                val expiresAt = snapshot.getTimestamp("expiresAt")
                val childUid = snapshot.getString("childUid")
                val parentUid = snapshot.getString("parentUid")

                if (parentUid == uid) {
                    statusText.text = "Gunakan HP anak untuk memasangkan"
                    return@addOnSuccessListener
                }

                if (expiresAt == null ||
                    expiresAt.toDate().time <= System.currentTimeMillis()
                ) {
                    statusText.text = "Kode sudah kedaluwarsa"
                    return@addOnSuccessListener
                }

                if (!childUid.isNullOrEmpty()) {
                    statusText.text = "Kode sudah digunakan"
                    return@addOnSuccessListener
                }

                document.update("childUid", uid)
                    .addOnSuccessListener {

                        savePairing(code, "child")
                        currentRole = "child"

                        codeText.text = code
                        showChildConnectedScreen()
                        watchChildPairingRemoval(code)
                        statusText.text = "Berhasil dipasangkan • menunggu perintah audio dari orang tua"

                        refreshFcmToken()
                    }
                    .addOnFailureListener { error ->

                        statusText.text = "Gagal memasangkan perangkat"

                        Toast.makeText(
                            this,
                            error.localizedMessage ?: "Terjadi kesalahan",
                            Toast.LENGTH_LONG
                        ).show()
                    }
            }
            .addOnFailureListener { error ->

                statusText.text = "Gagal memeriksa kode"

                Toast.makeText(
                    this,
                    error.localizedMessage ?: "Terjadi kesalahan",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    private fun invalidatePendingRoomConnection() {
        roomConnectionGeneration++
        isConnecting = false
        connectingRoomCode = null
        pendingRoomConnectedCallbacks.clear()
        pendingRoomFailureCallbacks.clear()
    }

    private fun notifyRoomConnectionSucceeded() {
        val callbacks = pendingRoomConnectedCallbacks.toList()
        pendingRoomConnectedCallbacks.clear()
        pendingRoomFailureCallbacks.clear()
        callbacks.forEach { callback ->
            try {
                callback()
            } catch (error: Exception) {
                android.util.Log.w("ParentAudio", "Room success callback failed", error)
            }
        }
    }

    private fun notifyRoomConnectionFailed(message: String) {
        val callbacks = pendingRoomFailureCallbacks.toList()
        pendingRoomConnectedCallbacks.clear()
        pendingRoomFailureCallbacks.clear()
        callbacks.forEach { callback ->
            try {
                callback(message)
            } catch (error: Exception) {
                android.util.Log.w("ParentAudio", "Room failure callback failed", error)
            }
        }
    }

    private fun connectToLiveKit(
        code: String,
        onConnected: (() -> Unit)? = null,
        onFailure: ((String) -> Unit)? = null
    ) {
        if (room != null) {
            if (activeRoomCode == code) onConnected?.invoke()
            else onFailure?.invoke("Ruang audio perangkat lain masih aktif")
            return
        }

        if (isConnecting) {
            if (connectingRoomCode == code) {
                onConnected?.let { pendingRoomConnectedCallbacks.add(it) }
                onFailure?.let { pendingRoomFailureCallbacks.add(it) }
            } else {
                onFailure?.invoke("Sedang menyiapkan koneksi audio perangkat lain")
            }
            return
        }

        isConnecting = true
        connectingRoomCode = code
        val attemptGeneration = ++roomConnectionGeneration
        pendingRoomConnectedCallbacks.clear()
        pendingRoomFailureCallbacks.clear()
        onConnected?.let { pendingRoomConnectedCallbacks.add(it) }
        onFailure?.let { pendingRoomFailureCallbacks.add(it) }
        statusText.text = "Meminta akses audio..."

        val user = auth.currentUser
        if (user == null) {
            isConnecting = false
            connectingRoomCode = null
            statusText.text = "Firebase belum terhubung!"
            notifyRoomConnectionFailed("Firebase belum terhubung")
            return
        }

        user.getIdToken(false)
            .addOnSuccessListener { result ->
                if (attemptGeneration != roomConnectionGeneration) return@addOnSuccessListener
                val idToken = result.token
                if (idToken.isNullOrEmpty()) {
                    isConnecting = false
                    connectingRoomCode = null
                    statusText.text = "Gagal mendapatkan token Firebase"
                    notifyRoomConnectionFailed("Token Firebase tidak tersedia")
                    return@addOnSuccessListener
                }

                activityScope.launch {
                    var createdRoom: Room? = null
                    try {
                        val response = withContext(Dispatchers.IO) {
                            requestLiveKitToken(idToken, code)
                        }
                        if (attemptGeneration != roomConnectionGeneration || selectedChildCode != code) {
                            throw kotlinx.coroutines.CancellationException("Pilihan perangkat berubah")
                        }

                        val role = response.getString("role")
                        if (role != currentRole) {
                            throw Exception("Peran perangkat tidak sesuai")
                        }

                        val liveKitConnection = LiveKitFailover.connectWithServer(
                            response = response,
                            createRoom = {
                                if (role == "child") {
                                    LiveKit.create(
                                        appContext = applicationContext,
                                        overrides = LiveKitOverrides(
                                            audioOptions = AudioOptions(
                                                audioOutputType = AudioType.MediaAudioType()
                                            )
                                        )
                                    )
                                } else {
                                    LiveKit.create(applicationContext)
                                }
                            }
                        )
                        val newRoom = liveKitConnection.room
                        activeLiveKitServerId = liveKitConnection.serverId
                        createdRoom = newRoom
                        if (attemptGeneration != roomConnectionGeneration || selectedChildCode != code) {
                            throw kotlinx.coroutines.CancellationException("Pilihan perangkat berubah")
                        }
                        newRoom.localParticipant.setMicrophoneEnabled(false)

                        val newTransferManager = Mp3TransferManager(
                            context = this@MainActivity,
                            room = newRoom,
                            scope = activityScope,
                            onStatus = { message -> statusText.text = message },
                            onReceived = { file ->
                                receivedMp3File = file
                                playMp3Button.visibility = View.VISIBLE
                                playReceivedMp3()
                            }
                        )

                        if (attemptGeneration != roomConnectionGeneration || selectedChildCode != code) {
                            throw kotlinx.coroutines.CancellationException("Pilihan perangkat berubah")
                        }
                        room = newRoom
                        if (role == "parent") activeRoomCode = code
                        mp3TransferManager = newTransferManager

                        if (role == "parent") {
                            isMicrophoneActive = false
                            statusText.text = "Ruang audio siap"
                            showParentDashboard()
                            notifyRoomConnectionSucceeded()
                        } else {
                            statusText.text = "TERHUBUNG • MENUNGGU AUDIO ORANG TUA"
                            showChildConnectedScreen()
                        }
                    } catch (error: Exception) {
                        val failedRoom = createdRoom
                        if (failedRoom != null && room !== failedRoom) {
                            try {
                                failedRoom.disconnect()
                            } catch (_: Exception) { }
                            try {
                                failedRoom.release()
                            } catch (_: Exception) { }
                        }
                        if (attemptGeneration == roomConnectionGeneration) {
                            statusText.text = "Gagal menghubungkan audio: ${error.message}"
                            notifyRoomConnectionFailed(
                                error.localizedMessage ?: "Koneksi audio gagal"
                            )
                            Toast.makeText(
                                this@MainActivity,
                                error.localizedMessage ?: "Koneksi gagal",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    } finally {
                        if (attemptGeneration == roomConnectionGeneration) {
                            isConnecting = false
                            connectingRoomCode = null
                        }
                    }
                }
            }
            .addOnFailureListener { error ->
                if (attemptGeneration != roomConnectionGeneration) return@addOnFailureListener
                isConnecting = false
                connectingRoomCode = null
                statusText.text = "Gagal mendapatkan identitas Firebase"
                notifyRoomConnectionFailed(
                    error.localizedMessage ?: "Gagal mendapatkan identitas Firebase"
                )
                Toast.makeText(
                    this,
                    error.localizedMessage ?: "Koneksi gagal",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    private fun requestRemoteAudio(
        code: String,
        action: String,
        onComplete: (Boolean, String?) -> Unit
    ) {
        val user = auth.currentUser
        if (user == null) {
            onComplete(false, "Firebase belum terhubung")
            return
        }
        user.getIdToken(false)
            .addOnSuccessListener { result ->
                val idToken = result.token
                if (idToken.isNullOrBlank()) {
                    onComplete(false, "Token Firebase tidak tersedia")
                    return@addOnSuccessListener
                }
                activityScope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            val connection = URL(workerUrl.replace("/token", "/request-audio"))
                                .openConnection() as HttpURLConnection
                            try {
                                connection.requestMethod = "POST"
                                connection.connectTimeout = 15000
                                connection.readTimeout = 15000
                                connection.doOutput = true
                                connection.setRequestProperty("Content-Type", "application/json")
                                val body = JSONObject().apply {
                                    put("idToken", idToken)
                                    put("code", code)
                                    put("action", action)
                                }
                                connection.outputStream.use {
                                    it.write(body.toString().toByteArray(Charsets.UTF_8))
                                }
                                val status = connection.responseCode
                                val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                                val responseText = stream?.bufferedReader()?.use { it.readText() } ?: "{}"
                                val response = JSONObject(responseText)
                                if (status !in 200..299 || !response.optBoolean("sent", false)) {
                                    throw IllegalStateException(
                                        response.optString("error", "Server menolak perintah audio")
                                    )
                                }
                            } finally {
                                connection.disconnect()
                            }
                        }
                        onComplete(true, null)
                    } catch (error: Exception) {
                        onComplete(false, error.localizedMessage ?: "Gagal mengirim perintah")
                    }
                }
            }
            .addOnFailureListener { error ->
                onComplete(false, error.localizedMessage ?: "Gagal mendapatkan token Firebase")
            }
    }

    private fun selectLiveKitServer(
        code: String,
        kind: String,
        serverId: String,
        onComplete: (Boolean, String?) -> Unit
    ) {
        if (currentRole != "parent") {
            onComplete(false, "Hanya perangkat orang tua yang dapat memilih server")
            return
        }
        if (auth.currentUser == null) {
            onComplete(false, "Firebase belum terhubung")
            return
        }
        val field = when (kind) {
            "audio" -> "audioLiveKitServerId"
            "camera" -> "cameraLiveKitServerId"
            else -> {
                onComplete(false, "Jenis koneksi LiveKit tidak valid")
                return
            }
        }
        val allowedServers = setOf("main", "backup-2", "backup-3", "backup-4", "backup-5")
        if (serverId !in allowedServers) {
            onComplete(false, "Pilihan server LiveKit tidak valid")
            return
        }

        // Save through the authenticated Firestore SDK. The pairing security rules
        // verify that this signed-in account is the parent and that only this
        // approved server-selection field is changed.
        db.collection("pairings").document(code)
            .update(field, serverId)
            .addOnSuccessListener {
                onComplete(true, null)
            }
            .addOnFailureListener { error ->
                onComplete(
                    false,
                    error.localizedMessage ?: "Firestore menolak penyimpanan pilihan server"
                )
            }
    }

    private fun requestLiveKitToken(
        idToken: String,
        code: String
    ): JSONObject {

        val connection = URL(workerUrl).openConnection()
            as HttpURLConnection

        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.doOutput = true

            connection.setRequestProperty(
                "Content-Type",
                "application/json"
            )

            val body = JSONObject().apply {
                put("idToken", idToken)
                put("code", code)
            }

            connection.outputStream.use {
                it.write(body.toString().toByteArray(Charsets.UTF_8))
            }

            val status = connection.responseCode

            val stream = if (status in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }

            val responseText = stream.bufferedReader().use {
                it.readText()
            }

            val response = JSONObject(responseText)

            if (status !in 200..299) {
                throw Exception(
                    response.optString("error", "Server menolak permintaan")
                )
            }

            return response

        } finally {
            connection.disconnect()
        }
    }

    private fun stopRemoteAudioAfterSetupFailure(code: String, message: String) {
        statusText.text = message
        requestRemoteAudio(code, "stop") { sent, error ->
            if (!sent) {
                statusText.text = "$message • perintah berhenti ke HP anak belum terkirim: ${error ?: "koneksi"}"
            }
        }
    }

    private fun showLiveKitServerTestDialog() {
        if (currentRole != "parent") return
        val code = selectedChildCode
        if (code.isNullOrBlank() || childConnected[code] != true) {
            statusText.text = "Pilih HP anak yang sudah terhubung terlebih dahulu"
            return
        }
        if (isMicrophoneActive) {
            statusText.text = "Hentikan Audio Live sebelum mengganti server"
            return
        }

        val serverIds = arrayOf("main", "backup-2", "backup-3", "backup-4", "backup-5")
        val labels = arrayOf(
            "Server utama",
            "Server cadangan 2",
            "Server cadangan 3",
            "Server cadangan 4",
            "Server cadangan 5"
        )
        val selectedIndex = serverIds.indexOf(activeLiveKitServerId).coerceAtLeast(0)
        SantetHpDialogBuilder()
            .setTitle("Pilih Server LiveKit")
            .setSingleChoiceItems(labels, selectedIndex) { dialog, which ->
                dialog.dismiss()
                applyLiveKitTestServer(code, serverIds[which], labels[which])
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun applyLiveKitTestServer(code: String, serverId: String, serverLabel: String) {
        if (isMicrophoneActive) {
            statusText.text = "Hentikan Audio Live sebelum mengganti server"
            return
        }
        statusText.text = "Menyiapkan $serverLabel..."
        requestRemoteAudio(code, "stop") { stopped, stopError ->
            if (!stopped) {
                statusText.text = "Belum bisa menghentikan koneksi HP anak: ${stopError ?: "coba lagi"}"
                return@requestRemoteAudio
            }
            activityScope.launch {
                try {
                    invalidatePendingRoomConnection()
                    room?.localParticipant?.setMicrophoneEnabled(false)
                    room?.disconnect()
                    room?.release()
                    room = null
                    activeRoomCode = null
                    activeLiveKitServerId = null
                    mp3TransferManager = null
                    isMicrophoneActive = false

                    selectLiveKitServer(code, "audio", serverId) { saved, saveError ->
                        if (!saved) {
                            statusText.text = "Gagal menyimpan server: ${saveError ?: "coba lagi"}"
                            return@selectLiveKitServer
                        }
                        statusText.text = "$serverLabel tersimpan • menghubungkan HP orang tua..."
                        connectToLiveKit(
                            code,
                            onConnected = {
                                statusText.text = "$serverLabel siap • tekan BICARA LIVE untuk menguji"
                                Toast.makeText(
                                    this@MainActivity,
                                    "$serverLabel siap diuji",
                                    Toast.LENGTH_LONG
                                ).show()
                            },
                            onFailure = { error ->
                                statusText.text = "Gagal terhubung ke $serverLabel: $error"
                            }
                        )
                    }
                } catch (error: Exception) {
                    statusText.text = "Gagal menyiapkan server: ${error.localizedMessage ?: "kesalahan koneksi"}"
                }
            }
        }
    }

    private fun startMicrophone() {
        if (currentRole != "parent") return
        val code = selectedChildCode
        if (code.isNullOrBlank() || childConnected[code] != true) {
            statusText.text = "Pilih HP anak yang sudah terhubung terlebih dahulu"
            return
        }

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), MIC_PERMISSION)
            return
        }

        statusText.text = "Menyiapkan server audio bersama..."
        val startOnSelectedServer = {
            val serverId = activeLiveKitServerId
            if (serverId.isNullOrBlank()) {
                stopRemoteAudioAfterSetupFailure(code, "Server LiveKit belum terpilih")
            } else {
                selectLiveKitServer(code, "audio", serverId) { saved, saveError ->
                    if (!saved) {
                        statusText.text = "Gagal menyamakan server: ${saveError ?: "coba lagi"}"
                    } else {
                        requestRemoteAudio(code, "start") { sent, error ->
                            if (sent) enableMicrophoneOnConnectedRoom(code)
                            else statusText.text = "Perintah audio gagal: ${error ?: "periksa koneksi HP anak"}"
                        }
                    }
                }
            }
        }
        if (room != null && activeRoomCode == code) {
            startOnSelectedServer()
        } else {
            connectToLiveKit(
                code,
                onConnected = { startOnSelectedServer() },
                onFailure = { error ->
                    stopRemoteAudioAfterSetupFailure(code, "Audio Live gagal disiapkan: $error")
                }
            )
        }
    }

    private fun enableMicrophoneOnConnectedRoom(code: String) {
        activityScope.launch {
            if (selectedChildCode != code || activeRoomCode != code) {
                stopRemoteAudioAfterSetupFailure(code, "Pilihan perangkat berubah sebelum audio aktif")
                return@launch
            }
            try {
                val success = room?.localParticipant?.setMicrophoneEnabled(true) ?: false
                if (success) {
                    isMicrophoneActive = true
                    statusText.text = "AUDIO LIVE AKTIF • SUARA SEDANG DIKIRIM"
                    startButton.visibility = View.GONE
                    stopButton.visibility = View.VISIBLE
                } else {
                    stopRemoteAudioAfterSetupFailure(code, "Ruang audio belum siap. Coba mulai Audio Live lagi.")
                }
            } catch (_: Exception) {
                stopRemoteAudioAfterSetupFailure(code, "Gagal mengaktifkan mikrofon")
            }
        }
    }

    private fun stopMicrophone() {
        invalidatePendingRoomConnection()
        val code = selectedChildCode
        activityScope.launch {
            try {
                room?.localParticipant?.setMicrophoneEnabled(false)
                room?.disconnect()
                room?.release()
                room = null
                activeRoomCode = null
                mp3TransferManager = null
                isConnecting = false
                isMicrophoneActive = false
                startButton.visibility = View.VISIBLE
                stopButton.visibility = View.GONE
                statusText.text = "Menghentikan Audio Live..."
                if (!code.isNullOrBlank()) {
                    requestRemoteAudio(code, "stop") { sent, error ->
                        statusText.text = if (sent) {
                            "Audio Live berhenti"
                        } else {
                            "Audio orang tua berhenti • perintah penutup belum terkirim: ${error ?: "koneksi"}"
                        }
                    }
                } else {
                    statusText.text = "Audio Live berhenti"
                }
            } catch (_: Exception) {
                statusText.text = "Gagal menghentikan audio"
            }
        }
    }
    

private fun openMp3Picker() {
    if (currentRole != "parent") {
        statusText.text = "Hanya HP orang tua yang dapat mengirim MP3"
        return
    }

    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
        addCategory(Intent.CATEGORY_OPENABLE)
        type = "audio/mpeg"
    }

    startActivityForResult(intent, MP3_PICKER)
}

override fun onActivityResult(
    requestCode: Int,
    resultCode: Int,
    data: Intent?
) {
    super.onActivityResult(requestCode, resultCode, data)

    if (googleDriveAuthorization?.onActivityResult(requestCode, resultCode, data) == true) return

    if (requestCode == PARENT_MEDIA_PICKER && resultCode == RESULT_OK) {
        val uri = data?.data
        if (uri != null) {
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) { }
            startParentMediaTransfer(uri)
        }
        return
    }

    if (requestCode == MP3_PICKER &&
        resultCode == RESULT_OK
    ) {
        val uri = data?.data ?: return

        val code = selectedChildCode
        if (code.isNullOrBlank() || childConnected[code] != true) {
            statusText.text = "Pilih HP anak yang sudah terhubung terlebih dahulu"
            return
        }

        statusText.text = "Menyiapkan koneksi untuk mengirim MP3..."
        requestRemoteAudio(code, "start") { sent, error ->
            if (!sent) {
                statusText.text = "Gagal meminta koneksi audio: ${error ?: "koneksi"}"
                return@requestRemoteAudio
            }
            val sendFile = {
                val manager = mp3TransferManager
                if (manager == null) {
                    stopRemoteAudioAfterSetupFailure(code, "Ruang audio belum siap. Silakan pilih MP3 lagi.")
                } else {
                    manager.send(uri)
                }
            }
            if (room != null && activeRoomCode == code) {
                sendFile()
            } else {
                connectToLiveKit(
                    code,
                    onConnected = sendFile,
                    onFailure = { error ->
                        stopRemoteAudioAfterSetupFailure(code, "Gagal menyiapkan ruang MP3: $error")
                    }
                )
            }
        }
    }
}

private fun openAdminWhatsApp() {
    val message = "Halo Admin, saya membutuhkan bantuan terkait aplikasimu"
    val url = "https://wa.me/6282233886876?text=${Uri.encode(message)}"
    try {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (error: Exception) {
        Toast.makeText(this, "WhatsApp atau browser tidak tersedia", Toast.LENGTH_LONG).show()
    }
}

private fun playReceivedMp3() {
    val file = receivedMp3File

    if (file == null || !file.exists()) {
        statusText.text = "Belum ada MP3 yang diterima"
        return
    }

    try {
        val intent = Intent(this, AudioPlaybackService::class.java).apply {
            action = AudioPlaybackService.ACTION_PLAY
            putExtra(AudioPlaybackService.EXTRA_FILE_PATH, file.absolutePath)
        }
        ContextCompat.startForegroundService(this, intent)
        statusText.text = "Menyiapkan pemutaran audio..."
    } catch (error: Exception) {
        statusText.text = "Gagal memulai pemutaran: ${error.message}"
    }
}

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(
            requestCode,
            permissions,
            grantResults
        )

        if (requestCode == PARENT_MEDIA_WRITE_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                showChildParentMediaReceiveConsent()
            } else {
                Toast.makeText(this, "Izin penyimpanan diperlukan agar file dapat masuk ke galeri pada Android lama.", Toast.LENGTH_LONG).show()
            }
        }

        if (requestCode == GALLERY_MEDIA_PERMISSION) {
            val granted = hasGalleryMediaAccess()
            if (granted) {
                updateGalleryConsentOnServer(true) { startChildGalleryP2pIfAllowed() }
                Toast.makeText(this, "Izin media galeri aktif.", Toast.LENGTH_LONG).show()
            } else {
                stopChildGalleryP2p()
                getSharedPreferences("parent_audio", MODE_PRIVATE).edit()
                    .putBoolean("child_gallery_sharing_enabled", false)
                    .putBoolean("child_gallery_server_consent_confirmed", false).apply()
                updateGalleryConsentOnServer(false)
                Toast.makeText(this, "Izin foto/video tidak diberikan. Berbagi galeri tetap nonaktif.", Toast.LENGTH_LONG).show()
            }
            updateChildGalleryPanel()
        }

        if (requestCode == CAMERA_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                armRemoteCameraStandby()
            } else {
                statusText.text = "Izin kamera tidak diberikan. Fitur kamera jarak jauh tetap nonaktif."
            }
        }

        if (requestCode == MIC_PERMISSION) {
            if (grantResults.isNotEmpty() &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED
            ) {
                startMicrophone()
            } else {
                statusText.text = "Izin mikrofon tidak diberikan"
            }
        }

        if (requestCode == LOCATION_PERMISSION) {
            val locationGranted =
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            if (locationGranted) {
                continueStartingChildLocationSharing()
            } else {
                pendingLocationStart = false
                Toast.makeText(this, "Izin lokasi tidak diberikan. Berbagi lokasi tetap nonaktif.", Toast.LENGTH_LONG).show()
            }
        }

        if (requestCode == LOCATION_NOTIFICATION_PERMISSION) {
            // Location sharing remains user-visible through its foreground-service notification.
            continueStartingChildLocationSharing()
        }

        if (requestCode == OPERATOR_PERMISSION) {
            val granted = grantResults.isNotEmpty() &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED
            Toast.makeText(
                this,
                if (granted) {
                    "Izin nama operator diberikan. Informasi operator akan diperbarui."
                } else {
                    "Izin nama operator tidak diberikan. Fitur baterai dan jaringan tetap berjalan."
                },
                Toast.LENGTH_LONG
            ).show()
            if (granted) ChildDeviceStatusWorker.schedule(this)
        }

        if (requestCode == SCHEDULE_NOTIFICATION_PERMISSION) {
            val granted = grantResults.isNotEmpty() &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED
            Toast.makeText(
                this,
                if (granted) {
                    "Notifikasi pengingat jadwal diaktifkan"
                } else {
                    "Notifikasi jadwal belum diizinkan. Pengingat mungkin tidak muncul."
                },
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onDestroy() {
        stopChildPresenceRefresh()
        stopParentMediaTransfer()
        pairingListener?.remove()
        childListeners.values.forEach { it.remove() }
        childListeners.clear()
        childAudioActivityText.removeCallbacks(staleStatusRunnable)
        notificationHistoryListener?.remove()
        notificationHistoryListener = null

        activityScope.launch {
            room?.disconnect()
            room?.release()
            room = null
            activeRoomCode = null
        }

        activityScope.cancel()

        if (parentDriveBackupBound) {
            try { unbindService(parentDriveBackupConnection) } catch (_: Exception) { }
            parentDriveBackupBound = false
        }
        parentDriveBackupBinder = null
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED

        super.onDestroy()
    }
}
