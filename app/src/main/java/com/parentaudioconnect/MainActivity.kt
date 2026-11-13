
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

    private lateinit var auth: FirebaseAuth
    private val db = FirebaseFirestore.getInstance()

    private lateinit var statusText: TextView
    private lateinit var connectionStatusText: TextView
    private lateinit var updateInfoText: TextView
    private lateinit var childAudioActivityText: TextView
    private lateinit var childDeviceStatusText: TextView
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
    private lateinit var cancelPairingButton: Button
    private lateinit var updateButton: Button
    private lateinit var cameraStandbyButton: Button
    private lateinit var cameraLiveButton: LinearLayout
    private lateinit var liveKitTestButton: Button
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
    private var parentGalleryDialog: AlertDialog? = null
    private var parentGalleryStatus: TextView? = null
    private var parentGalleryList: LinearLayout? = null
    private val parentGalleryRows = linkedMapOf<String, LinearLayout>()
    private val parentGalleryMediaItems = linkedMapOf<String, GalleryP2pGalleryProtocol.RemoteMedia>()
    private val parentGalleryThumbQueue = ArrayDeque<String>()
    private var parentGalleryThumbInFlight: String? = null
    private var parentGalleryIndexRequested = false
    private var notificationHistoryListener: ListenerRegistration? = null
    private lateinit var parentAppBlockPanel: TextView
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
}
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        lifecycleRegistry.currentState = Lifecycle.State.STARTED

        auth = FirebaseAuth.getInstance()

        rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.TOP
            setPadding(dp(9), dp(5), dp(9), dp(8))
            setBackgroundColor(Color.rgb(244, 247, 252))
        }

        val title = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(11), dp(6), dp(11), dp(6))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(15, 29, 64), Color.rgb(35, 69, 139), Color.rgb(67, 104, 183))
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
                setTextColor(Color.rgb(190, 211, 255))
            })

            val brandRow = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(3), 0, dp(1))
            }
            brandRow.addView(TextView(this@MainActivity).apply {
                text = "SantetHP"
                textSize = 23f
                letterSpacing = 0.005f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            brandRow.addView(TextView(this@MainActivity).apply {
                text = "ORTU"
                textSize = 10f
                letterSpacing = 0.08f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                setPadding(dp(13), dp(7), dp(13), dp(7))
                background = GradientDrawable().apply {
                    setColor(Color.argb(46, 255, 255, 255))
                    cornerRadius = dp(20).toFloat()
                    setStroke(dp(1), Color.argb(85, 255, 255, 255))
                }
            })
            addView(brandRow)

            addView(TextView(this@MainActivity).apply {
                text = "Ruang kendali orang tua"
                textSize = 13.5f
                setTextColor(Color.rgb(224, 234, 255))
                letterSpacing = 0.015f
            })
        }

        statusText = TextView(this).apply {
            text = "Memeriksa koneksi..."
            textSize = 13.5f
            setTypeface(null, android.graphics.Typeface.BOLD)
            letterSpacing = 0.01f
            setTextColor(Color.rgb(34, 57, 96))
            gravity = Gravity.CENTER_VERTICAL
            setLineSpacing(dp(2).toFloat(), 1f)
            setPadding(dp(12), dp(7), dp(12), dp(7))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.WHITE, Color.rgb(242, 247, 255))
            ).apply {
                cornerRadius = dp(20).toFloat()
                setStroke(dp(1), Color.rgb(195, 215, 245))
            }
            elevation = dp(2).toFloat()
            minHeight = dp(42)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, dp(4), 0, dp(6)) }
        }

        connectionStatusText = TextView(this).apply {
            text = "○ OFFLINE"
            textSize = 10.5f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(Color.rgb(105, 116, 137))
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(4), dp(8), dp(4))
            background = GradientDrawable().apply {
                setColor(Color.rgb(239, 243, 249))
                cornerRadius = dp(14).toFloat()
            }
            maxLines = 1
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(34)
            ).apply { setMargins(dp(6), 0, dp(6), 0) }
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

        childAudioActivityText = TextView(this).apply {
            text = "AKTIVITAS HP ANAK\nTidak sedang memutar audio"
            textSize = 13f
            setTypeface(null, android.graphics.Typeface.BOLD)
            letterSpacing = 0.01f
            setTextColor(Color.rgb(32, 55, 92))
            gravity = Gravity.CENTER_VERTICAL
            setLineSpacing(dp(3).toFloat(), 1f)
            setPadding(dp(17), dp(16), dp(17), dp(16))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.WHITE, Color.rgb(237, 245, 255))
            ).apply {
                cornerRadius = dp(20).toFloat()
                setStroke(dp(1), Color.rgb(194, 215, 246))
            }
            elevation = dp(2).toFloat()
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, dp(4), 0, dp(6)) }
        }

        childDeviceStatusText = TextView(this).apply {
            text = "KONDISI HP ANAK\nMenunggu laporan pertama dari perangkat anak..."
            textSize = 12.5f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(Color.rgb(104, 117, 140))
            gravity = Gravity.CENTER_VERTICAL
            setLineSpacing(dp(3).toFloat(), 1f)
            setPadding(dp(17), dp(16), dp(17), dp(16))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.WHITE, Color.rgb(242, 247, 255))
            ).apply {
                cornerRadius = dp(20).toFloat()
                setStroke(dp(1), Color.rgb(194, 215, 246))
            }
            elevation = dp(2).toFloat()
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, dp(4), 0, dp(6)) }
        }

        childLocationMapView = ChildLocationMapView(this)
        codeText = TextView(this).apply {
            textSize = 28f
            setTypeface(null, android.graphics.Typeface.BOLD)
            letterSpacing = 0.12f
            setTextColor(Color.rgb(39, 78, 180))
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(17), dp(16), dp(17))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.WHITE, Color.rgb(232, 240, 255))
            ).apply {
                cornerRadius = dp(19).toFloat()
                setStroke(dp(1), Color.rgb(198, 216, 246))
            }
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
        startButton = createActionButton("🎙", "BICARA LIVE", Color.rgb(55, 80, 220))
        stopButton = createActionButton("🎙", "HENTIKAN LIVE", Color.rgb(220, 75, 75))
        sendMp3Button = createActionButton("♫", "PUTAR MP3", Color.rgb(55, 80, 220))
        playMp3Button = createButton("PUTAR MP3 DITERIMA")
        adminWhatsAppButton = createActionButton("☎", "CHAT ADMIN", Color.rgb(37, 211, 102))
        cameraLiveButton = createActionButton("📷", "KAMERA LIVE", Color.rgb(45, 91, 210))
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
        secondActionRow.addView(adminWhatsAppButton, LinearLayout.LayoutParams(actionParams))
        actionButtonsRow.addView(firstActionRow)
        actionButtonsRow.addView(secondActionRow)
        addChildButton = createButton("＋")
        addChildButton.textSize = 25f
        addChildButton.contentDescription = "Tambah anak"
        addChildButton.layoutParams = LinearLayout.LayoutParams(dp(42), dp(42)).apply { setMargins(dp(8), dp(7), 0, dp(7)) }
        selectedChildButton = createButton("Pilih anak  ▾")
        selectedChildButton.gravity = Gravity.CENTER_VERTICAL
        selectedChildButton.setOnClickListener { toggleChildrenPanel() }
        selectedChildButton.setTextColor(Color.rgb(35, 57, 96))
        selectedChildButton.textSize = 13.5f
        selectedChildButton.contentDescription = "Pilih perangkat anak"
        selectedChildButton.background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(Color.WHITE, Color.rgb(246, 249, 255))
        ).apply {
            cornerRadius = dp(17).toFloat()
            setStroke(dp(1), Color.rgb(211, 224, 246))
        }
        selectedChildButton.elevation = dp(2).toFloat()
        selectedChildButton.setTypeface(null, android.graphics.Typeface.BOLD)
        addChildButton.setTextColor(Color.WHITE)
        addChildButton.background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(Color.rgb(73, 112, 235), Color.rgb(43, 76, 190))
        ).apply { cornerRadius = dp(16).toFloat() }
        addChildButton.elevation = dp(3).toFloat()
        cancelPairingButton = createButton("BATALKAN KODE")
        updateButton = createButton("PERIKSA PEMBARUAN")
        cameraStandbyButton = createButton("AKTIFKAN SIAGA KAMERA")
        liveKitTestButton = createButton("PILIHAN SERVER")
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
        ).apply { bottomMargin = dp(15) })
        childModePanel = TextView(this).apply {
            text = "🛡️  HP ANAK SIAP\n\nPerangkat berhasil terhubung\nMenunggu perintah dari HP orang tua"
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(Color.rgb(35, 53, 88))
            gravity = Gravity.CENTER
            setLineSpacing(dp(4).toFloat(), 1f)
            setPadding(dp(22), dp(29), dp(22), dp(29))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.WHITE, Color.rgb(231, 239, 255))
            ).apply {
                cornerRadius = dp(23).toFloat()
                setStroke(dp(1), Color.rgb(207, 222, 248))
            }
            elevation = dp(3).toFloat()
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, dp(12), 0, dp(14)) }
            visibility = View.GONE
        }
        childModePanel.setOnClickListener {
            AlertDialog.Builder(this)
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
            textSize = 13f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(Color.rgb(35, 53, 88))
            gravity = Gravity.CENTER
            setLineSpacing(dp(3).toFloat(), 1f)
            setPadding(dp(18), dp(18), dp(18), dp(18))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.WHITE, Color.rgb(236, 246, 255))
            ).apply {
                cornerRadius = dp(19).toFloat()
                setStroke(dp(1), Color.rgb(190, 218, 245))
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, dp(3), 0, dp(12)) }
            visibility = View.GONE
            setOnClickListener { showChildLocationConsentDialog() }
        }
        rootLayout.addView(childLocationPanel)
        childAppBlockPanel = TextView(this).apply {
            text = "🚫  BLOKIR APLIKASI\nNonaktif • Ketuk untuk membaca penjelasan dan mengaktifkan kontrol aplikasi"
            textSize = 13f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(Color.rgb(35, 53, 88))
            gravity = Gravity.CENTER
            setLineSpacing(dp(3).toFloat(), 1f)
            setPadding(dp(18), dp(18), dp(18), dp(18))
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.WHITE, Color.rgb(255, 244, 237))).apply {
                cornerRadius = dp(19).toFloat()
                setStroke(dp(1), Color.rgb(245, 205, 180))
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, dp(3), 0, dp(12)) }
            visibility = View.GONE
            setOnClickListener { showChildAppBlockConsent() }
        }
        rootLayout.addView(childAppBlockPanel)
        childAppActivityPanel = TextView(this).apply {
            text = "📱  PEMANTAUAN APLIKASI AKTIF\nNonaktif • Ketuk untuk membaca penjelasan dan memberi persetujuan"
            textSize = 13f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(Color.rgb(35, 53, 88))
            gravity = Gravity.CENTER
            setLineSpacing(dp(3).toFloat(), 1f)
            setPadding(dp(18), dp(18), dp(18), dp(18))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.WHITE, Color.rgb(237, 248, 244))
            ).apply {
                cornerRadius = dp(19).toFloat()
                setStroke(dp(1), Color.rgb(190, 225, 208))
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, dp(3), 0, dp(12)) }
            visibility = View.GONE
            setOnClickListener { showChildAppActivityConsent() }
        }
        rootLayout.addView(childAppActivityPanel)
        childNotificationPanel = TextView(this).apply {
            text = "🔔  PEMANTAUAN NOTIFIKASI\nNonaktif • Ketuk untuk membaca penjelasan dan mengaktifkan akses notifikasi"
            textSize = 13f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(Color.rgb(35, 53, 88))
            gravity = Gravity.CENTER
            setLineSpacing(dp(3).toFloat(), 1f)
            setPadding(dp(18), dp(18), dp(18), dp(18))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.WHITE, Color.rgb(245, 241, 255))
            ).apply {
                cornerRadius = dp(19).toFloat()
                setStroke(dp(1), Color.rgb(211, 198, 239))
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, dp(3), 0, dp(12)) }
            visibility = View.GONE
            setOnClickListener { showChildNotificationConsent() }
        }
        rootLayout.addView(childNotificationPanel)
        childGalleryPanel = TextView(this).apply {
            text = "🖼️  BERBAGI GALERI HP INI\nNonaktif • Ketuk untuk membaca penjelasan dan memilih media yang boleh dibagikan"
            textSize = 13f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(Color.rgb(35, 53, 88))
            gravity = Gravity.CENTER
            setLineSpacing(dp(3).toFloat(), 1f)
            setPadding(dp(18), dp(18), dp(18), dp(18))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.WHITE, Color.rgb(239, 246, 255))
            ).apply {
                cornerRadius = dp(19).toFloat()
                setStroke(dp(1), Color.rgb(190, 218, 245))
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, dp(3), 0, dp(12)) }
            visibility = View.GONE
            setOnClickListener { showChildGalleryConsent() }
        }
        rootLayout.addView(childGalleryPanel)
        val deviceSectionTitle = TextView(this).apply {
            text = "PERANGKAT ANAK"
            textSize = 11.5f
            setTextColor(Color.rgb(49, 82, 145))
            setTypeface(null, android.graphics.Typeface.BOLD)
            letterSpacing = 0.105f
            setPadding(dp(4), dp(7), dp(4), dp(8))
        }
        rootLayout.addView(deviceSectionTitle)
        parentOnlyViews.add(deviceSectionTitle)
        val childHeaderRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        childHeaderRow.addView(selectedChildButton, LinearLayout.LayoutParams(0, dp(42), 1f))
        childHeaderRow.addView(connectionStatusText)
        childHeaderRow.addView(addChildButton)
        rootLayout.addView(childHeaderRow)
        parentOnlyViews.add(childHeaderRow)
        rootLayout.addView(childrenContainer)
        parentOnlyViews.add(childrenContainer)
        rootLayout.addView(updateButton)
        rootLayout.addView(updateInfoText)
        rootLayout.addView(parentButton)
        rootLayout.addView(childButton)
        val statusHeading = TextView(this).apply {
            text = "STATUS APLIKASI"
            textSize = 11.5f
            setTextColor(Color.rgb(49, 82, 145))
            setTypeface(null, android.graphics.Typeface.BOLD)
            letterSpacing = 0.105f
            setPadding(dp(4), dp(11), dp(4), dp(7))
        }
        rootLayout.addView(statusHeading)
        parentOnlyViews.add(statusHeading)
        rootLayout.addView(statusText)
        parentOnlyViews.add(statusText)
        rootLayout.addView(pairingInstructionText)
        rootLayout.addView(codeText)
        val activityHeading = TextView(this).apply {
            text = "STATUS PEMUTARAN AUDIO"
            textSize = 11.5f
            setTextColor(Color.rgb(49, 82, 145))
            setTypeface(null, android.graphics.Typeface.BOLD)
            letterSpacing = 0.105f
            setPadding(dp(4), dp(11), dp(4), dp(7))
        }
        rootLayout.addView(activityHeading)
        parentOnlyViews.add(activityHeading)
        rootLayout.addView(childAudioActivityText)
        parentOnlyViews.add(childAudioActivityText)
        // Keep the four primary parent actions directly below the child's activity card.
        rootLayout.addView(actionButtonsRow)
        rootLayout.addView(liveKitTestButton)
        parentOnlyViews.add(liveKitTestButton)
        rootLayout.addView(childDeviceStatusText)
        parentOnlyViews.add(childDeviceStatusText)
        parentAppBlockPanel = TextView(this).apply {
            text = "🚫  BLOKIR APLIKASI\nMenunggu persetujuan dan daftar aplikasi dari HP anak • Ketuk untuk mengatur"
            textSize = 12.5f
            setTextColor(Color.rgb(35, 53, 88))
            setLineSpacing(dp(3).toFloat(), 1f)
            setPadding(dp(17), dp(16), dp(17), dp(16))
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.WHITE, Color.rgb(255, 247, 241))).apply {
                cornerRadius = dp(19).toFloat()
                setStroke(dp(1), Color.rgb(245, 205, 180))
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, dp(4), 0, dp(8)) }
            setOnClickListener { showParentAppBlockDialog() }
        }
        rootLayout.addView(parentAppBlockPanel)
        parentOnlyViews.add(parentAppBlockPanel)
        val parentNotificationsButton = TextView(this).apply {
            text = "🔔  NOTIFIKASI HP ANAK\nLihat riwayat notifikasi dari semua aplikasi"
            textSize = 13.5f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(Color.rgb(47, 55, 110))
            gravity = Gravity.CENTER_VERTICAL
            setLineSpacing(dp(3).toFloat(), 1f)
            setPadding(dp(18), dp(17), dp(18), dp(17))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.WHITE, Color.rgb(245, 241, 255))
            ).apply {
                cornerRadius = dp(18).toFloat()
                setStroke(dp(1), Color.rgb(211, 198, 239))
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, dp(5), 0, dp(8)) }
            setOnClickListener { showChildNotificationsDialog() }
        }
        rootLayout.addView(parentNotificationsButton)
        parentOnlyViews.add(parentNotificationsButton)
        val parentGalleryButton = TextView(this).apply {
            text = "🖼️  GALERI ANAK\nLihat foto dan video yang dibagikan dari HP anak"
            textSize = 13.5f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(Color.rgb(35, 65, 125))
            gravity = Gravity.CENTER_VERTICAL
            setLineSpacing(dp(3).toFloat(), 1f)
            setPadding(dp(18), dp(17), dp(18), dp(17))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.WHITE, Color.rgb(235, 243, 255))
            ).apply {
                cornerRadius = dp(18).toFloat()
                setStroke(dp(1), Color.rgb(190, 211, 244))
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, dp(5), 0, dp(8)) }
            setOnClickListener { showParentGalleryStatus() }
        }
        rootLayout.addView(parentGalleryButton)
        parentOnlyViews.add(parentGalleryButton)
        val mapsButton = TextView(this).apply {
            text = "🗺️  MAPS & GEO-FENCE\nBuka peta lokasi perangkat anak"
            textSize = 14f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(Color.rgb(35, 65, 125))
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), dp(17), dp(18), dp(17))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.WHITE, Color.rgb(235, 243, 255))
            ).apply {
                cornerRadius = dp(18).toFloat()
                setStroke(dp(1), Color.rgb(190, 211, 244))
            }
            elevation = dp(2).toFloat()
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, dp(5), 0, dp(8)) }
            setOnClickListener { showMapsPage() }
        }
        rootLayout.addView(mapsButton)
        parentOnlyViews.add(mapsButton)
        rootLayout.addView(cancelPairingButton)
        val controlHeading = TextView(this).apply {
            text = "KONTROL UTAMA"
            textSize = 11.5f
            setTextColor(Color.rgb(49, 82, 145))
            setTypeface(null, android.graphics.Typeface.BOLD)
            letterSpacing = 0.105f
            setPadding(dp(4), dp(11), dp(4), dp(7))
        }
        controlHeading.text = "KONTROL TAMBAHAN"
        rootLayout.addView(controlHeading)
        parentOnlyViews.add(controlHeading)
        rootLayout.addView(playMp3Button)
        rootLayout.addView(cameraStandbyButton)

        val mapsPage = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(18))
            setBackgroundColor(Color.rgb(244, 247, 252))
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
            setTextColor(Color.rgb(35, 65, 125))
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
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
            setTextColor(Color.rgb(27, 48, 84))
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
            backgroundTintList = android.content.res.ColorStateList.valueOf(Color.rgb(36, 91, 169))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52)
            ).apply { setMargins(0, dp(12), 0, dp(8)) }
            setOnClickListener { startSafeZoneMapSelection() }
        }
        mapsPage.addView(addSafeZoneButton)
        mapsPage.addView(TextView(this).apply {
            text = "Pilih lokasi langsung pada peta. Setiap titik dapat diberi nama dan radius 50–5.000 meter."
            textSize = 11f
            setTextColor(Color.rgb(104, 117, 140))
            setPadding(dp(4), dp(2), dp(4), dp(8))
        })
        safeZonesContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        mapsPage.addView(safeZonesContainer)
        rootLayout.addView(mapsPage)
        mapsPage.tag = "santethp_maps_page"

        showRoleSelection()
        val scrollView = ScrollView(this).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            setBackgroundColor(Color.TRANSPARENT)
            addView(rootLayout)
        }
        setContentView(scrollView)

        connectFirebase()
    }

    override fun onResume() {
        super.onResume()
        AppUpdateManager.resumePendingInstall(this)
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
            // Device battery/network reporting is independent of Usage Access.
            // Always re-enqueue the lightweight status worker when the child app resumes.
            ChildDeviceStatusWorker.schedule(this)
            refreshFcmToken()
            restoreCameraStandbyIfNeeded()
        }
    }

    override fun onPause() {
        if (currentRole == "parent" && parentGalleryDialog?.isShowing == true) {
            parentGalleryDialog?.dismiss()
        }
        super.onPause()
    }

    private fun startChildGalleryP2pIfAllowed() {
        if (currentRole != "child") return
        val prefs = getSharedPreferences("parent_audio", MODE_PRIVATE)
        if (!prefs.getBoolean("child_gallery_sharing_enabled", false) ||
            !prefs.getBoolean("child_gallery_server_consent_confirmed", false) ||
            !hasGalleryMediaAccess()
        ) return
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
        val serviceIntent = Intent(this, ChildGalleryP2pService::class.java)
            .setAction(ChildGalleryP2pService.ACTION_STOP)
        try {
            startService(serviceIntent)
        } catch (_: Exception) {
            stopService(Intent(this, ChildGalleryP2pService::class.java))
        }
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

        AlertDialog.Builder(this)
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

        AlertDialog.Builder(this)
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
            text = "TITIK AMAN TERSIMPAN (" + zones.size + "/10)"
            textSize = 12f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(Color.rgb(39, 70, 122))
            setPadding(dp(3), dp(10), dp(3), dp(7))
        })
        if (zones.isEmpty()) {
            safeZonesContainer.addView(TextView(this).apply {
                text = "Belum ada titik aman. Tekan tombol tambah, lalu pilih lokasi pada peta."
                textSize = 12f
                setTextColor(Color.rgb(104, 117, 140))
            })
            renderSafeZoneHistory(snapshot)
            return
        }
        zones.forEach { zone ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), dp(10), dp(12), dp(10))
                background = GradientDrawable().apply {
                    setColor(Color.WHITE)
                    cornerRadius = dp(14).toFloat()
                    setStroke(dp(1), Color.rgb(218, 228, 242))
                }
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, dp(4), 0, dp(4)) }
            }
            card.addView(TextView(this).apply {
                text = zone.name + "  •  radius " + zone.radiusM + " m"
                textSize = 13f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(Color.rgb(36, 59, 96))
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
                setOnClickListener {
                    childLocationMapView.focusLocation(zone.latitude, zone.longitude)
                    showSafeZoneEditor(zone)
                }
            }, LinearLayout.LayoutParams(0, dp(42), 1f))
            row.addView(Button(this).apply {
                text = "Hapus"
                isAllCaps = false
                setOnClickListener {
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("Hapus titik aman?")
                        .setMessage("Area " + zone.name + " akan dihapus.")
                        .setNegativeButton("Batal", null)
                        .setPositiveButton("Hapus") { _, _ ->
                            val latest = selectedChildCode?.let { childSnapshots[it] } ?: return@setPositiveButton
                            saveSafeZones(readSafeZones(latest).filterNot { it.id == zone.id })
                        }.show()
                }
            }, LinearLayout.LayoutParams(0, dp(42), 1f))
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
            text = "RIWAYAT AKTIVITAS ZONA AMAN"
            textSize = 12f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(Color.rgb(39, 70, 122))
            setPadding(dp(3), dp(16), dp(3), dp(7))
        })
        if (events.isEmpty()) {
            safeZonesContainer.addView(TextView(this).apply {
                text = "Belum ada aktivitas masuk atau keluar zona aman."
                textSize = 11.5f
                setTextColor(Color.rgb(104, 117, 140))
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
                setTextColor(Color.rgb(48, 68, 105))
                setPadding(dp(11), dp(9), dp(11), dp(9))
                background = GradientDrawable().apply {
                    setColor(Color.WHITE)
                    cornerRadius = dp(11).toFloat()
                    setStroke(dp(1), Color.rgb(225, 233, 247))
                }
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, dp(3), 0, dp(3)) }
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

        val dialog = AlertDialog.Builder(this)
            .setTitle(if (existing == null) "Tambah titik aman" else "Edit titik aman")
            .setView(form).setNegativeButton("Batal", null)
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
        AlertDialog.Builder(this)
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
        requestParentNotificationPermission()
        childLocationMapView.setMapTapMode(false)
        roleIntroText.visibility = View.GONE
        rootLayout.findViewWithTag<View>("santethp_maps_page")?.visibility = View.GONE
        childModePanel.visibility = View.GONE
        childLocationPanel.visibility = View.GONE
        childGalleryPanel.visibility = View.GONE
        childAppActivityPanel.visibility = View.GONE
        parentOnlyViews.forEach { it.visibility = View.VISIBLE }
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

    private fun showChildConnectedScreen() {
        roleIntroText.visibility = View.GONE
        parentOnlyViews.forEach { it.visibility = View.GONE }
        childModePanel.visibility = View.VISIBLE
        childLocationPanel.visibility = View.VISIBLE
        childAppBlockPanel.visibility = View.VISIBLE
        childAppActivityPanel.visibility = View.VISIBLE
        childNotificationPanel.visibility = View.VISIBLE
        childGalleryPanel.visibility = View.VISIBLE
        updateChildGalleryPanel()
        updateChildLocationPanel()
        updateChildAppBlockPanel()
        updateChildAppActivityPanel()
        updateChildNotificationPanel()
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

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

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

    private fun createActionButton(icon: String, label: String, accent: Int): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(5), dp(4), dp(5), dp(4))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.WHITE, Color.rgb(246, 249, 255))
            ).apply {
                cornerRadius = dp(14).toFloat()
                setStroke(dp(1), Color.rgb(215, 226, 245))
            }
            elevation = dp(2).toFloat()
            isClickable = true
            isFocusable = true
            contentDescription = label
        }
        val iconView = TextView(this).apply {
            text = icon
            textSize = 20f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(accent, blendWithWhite(accent, 0.22f))
            ).apply {
                cornerRadius = dp(15).toFloat()
                setStroke(dp(1), Color.argb(48, 255, 255, 255))
            }
            elevation = dp(1).toFloat()
            layoutParams = LinearLayout.LayoutParams(dp(29), dp(29)).apply {
                rightMargin = dp(5)
            }
        }
        val helper = when (label) {
            "BICARA LIVE" -> "Dengarkan suara sekitar"
            "HENTIKAN LIVE" -> "Akhiri sesi audio"
            "PUTAR MP3" -> "Kirim audio ke perangkat"
            "KAMERA LIVE" -> "Lihat kamera perangkat"
            "CHAT ADMIN" -> "Bantuan dan dukungan"
            else -> "Kontrol perangkat"
        }
        val textStack = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(1), 0, dp(1))
        }
        val labelView = TextView(this).apply {
            text = label
            textSize = 11.7f
            setTypeface(null, android.graphics.Typeface.BOLD)
            letterSpacing = 0.012f
            setTextColor(Color.rgb(27, 43, 73))
            maxLines = 2
            setLineSpacing(dp(1).toFloat(), 1f)
        }
        val helperView = TextView(this).apply {
            text = helper
            textSize = 9f
            setTextColor(Color.rgb(88, 105, 134))
            setPadding(0, dp(2), 0, 0)
            maxLines = 2
            setLineSpacing(dp(1).toFloat(), 1f)
        }
        textStack.addView(labelView)
        textStack.addView(helperView)
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
                intArrayOf(Color.rgb(55, 91, 220), Color.rgb(39, 67, 178))
            ).apply { cornerRadius = dp(16).toFloat() }
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

    private fun updateSelectedChildButton() {
        val name = selectedChildCode?.let { childNames[it] }
        val arrow = if (isChildrenPanelExpanded) "▴" else "▾"
        selectedChildButton.text = if (name.isNullOrBlank()) "▣  Pilih perangkat anak  $arrow" else "▣  $name  $arrow"
        val connected = selectedChildCode?.let { childConnected[it] == true } == true
        connectionStatusText.text = if (connected) "● ONLINE" else "○ OFFLINE"
        connectionStatusText.setTextColor(
            if (connected) Color.rgb(24, 128, 82) else Color.rgb(105, 116, 137)
        )
        (connectionStatusText.background as? GradientDrawable)?.setColor(
            if (connected) Color.rgb(226, 247, 237) else Color.rgb(239, 243, 249)
        )
        val conditionTitle = "KONDISI HP ${name?.takeIf { it.isNotBlank() } ?: "ANAK"}"
        childDeviceStatusText.text = childDeviceStatusText.text.toString()
            .replaceFirst(Regex("^KONDISI HP [^\n]*"), conditionTitle)
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
        childrenContainer.setPadding(dp(8), dp(9), dp(8), dp(9))
        childrenContainer.background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(Color.WHITE, Color.rgb(242, 247, 255))
        ).apply {
            cornerRadius = dp(21).toFloat()
            setStroke(dp(1), Color.rgb(215, 226, 245))
        }
        childrenContainer.elevation = dp(4).toFloat()

        if (childNames.isEmpty()) {
            val empty = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(14), dp(16), dp(14), dp(16))
                background = GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    intArrayOf(Color.WHITE, Color.rgb(247, 250, 255))
                ).apply {
                    cornerRadius = dp(16).toFloat()
                    setStroke(dp(1), Color.rgb(225, 233, 247))
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
                setTextColor(Color.rgb(48, 68, 105))
                gravity = Gravity.CENTER
            })
            empty.addView(TextView(this).apply {
                text = "Tekan tombol + untuk mulai menghubungkan HP anak."
                textSize = 11f
                setLineSpacing(dp(2).toFloat(), 1f)
                setTextColor(Color.rgb(105, 120, 146))
                setPadding(0, dp(5), 0, 0)
                gravity = Gravity.CENTER
            })
            childrenContainer.addView(empty)
        }

        childNames.forEach { (code, name) ->
            val connected = childConnected[code] == true
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(10), dp(9), dp(8), dp(9))
                background = GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    if (selectedChildCode == code)
                        intArrayOf(Color.rgb(235, 243, 255), Color.rgb(248, 251, 255))
                    else
                        intArrayOf(Color.WHITE, Color.rgb(247, 250, 255))
                ).apply {
                    cornerRadius = dp(17).toFloat()
                    setStroke(
                        dp(if (selectedChildCode == code) 1.5f.toInt() else 1),
                        if (selectedChildCode == code) Color.rgb(133, 169, 235)
                        else Color.rgb(218, 229, 246)
                    )
                }
                isClickable = true
                isFocusable = true
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, dp(3), 0, dp(3)) }
            }
            val phoneIcon = ChildAvatarView().apply {
                layoutParams = LinearLayout.LayoutParams(dp(50), dp(50)).apply {
                    rightMargin = dp(9)
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
                setTextColor(Color.rgb(27, 43, 73))
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            })
            details.addView(TextView(this).apply {
                text = "Perangkat terdaftar"
                textSize = 10f
                setTextColor(Color.rgb(112, 127, 151))
                setPadding(0, dp(2), 0, 0)
            })
            details.addView(TextView(this).apply {
                text = if (connected) "● ONLINE  •  Terhubung" else "○ OFFLINE  •  Menunggu koneksi"
                textSize = 9f
                setTypeface(null, android.graphics.Typeface.BOLD)
                letterSpacing = 0.005f
                setPadding(dp(7), dp(4), dp(7), dp(4))
                setTextColor(if (connected) Color.rgb(24, 128, 82) else Color.rgb(105, 116, 137))
                background = GradientDrawable().apply {
                    setColor(if (connected) Color.rgb(226, 247, 237) else Color.rgb(239, 243, 249))
                    cornerRadius = dp(8).toFloat()
                }
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            })
            row.addView(details)
            if (selectedChildCode == code) row.addView(TextView(this).apply {
                text = "✓"
                textSize = 18f
                setTypeface(null, android.graphics.Typeface.BOLD)
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                setPadding(dp(6), dp(3), dp(6), dp(3))
                background = GradientDrawable().apply {
                    setColor(Color.rgb(55, 100, 210))
                    cornerRadius = dp(20).toFloat()
                }
            })
            val removeButton = Button(this).apply {
                text = "Lepaskan"
                textSize = 10.5f
                isAllCaps = false
                minHeight = dp(34)
                setPadding(dp(7), 0, dp(7), 0)
                setTextColor(Color.rgb(169, 62, 72))
                background = GradientDrawable().apply {
                    setColor(Color.rgb(255, 247, 247))
                    cornerRadius = dp(10).toFloat()
                    setStroke(dp(1), Color.rgb(245, 220, 222))
                }
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, dp(34)
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
            setTextColor(Color.rgb(42, 82, 174))
            gravity = Gravity.CENTER
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.rgb(239, 245, 255), Color.rgb(229, 238, 255))
            ).apply {
                cornerRadius = dp(13).toFloat()
                setStroke(dp(1), Color.rgb(213, 226, 250))
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp(2), dp(8), dp(2), dp(2)) }
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
        AlertDialog.Builder(this)
            .setTitle("Beri nama anak")
            .setView(input)
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
        AlertDialog.Builder(this)
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
            childAudioActivityText.text = "AKTIVITAS HP ANAK\nMenunggu data perangkat yang dipilih..."
            childDeviceStatusText.text = "KONDISI HP ${childNames[code] ?: "ANAK"}\nMenunggu laporan dari perangkat ini..."
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
        val message = "Berbagi lokasi mengirim koordinat perangkat ini ke HP orang tua yang sudah dipasangkan, kira-kira setiap 15 menit saat layanan aktif. Akurasi mengikuti izin lokasi Android (perkiraan atau presisi). Notifikasi SantetHP akan terlihat selama berbagi berlangsung. Lokasi tidak dikirim sebelum Anda mengaktifkannya dan Anda dapat menghentikannya kapan saja."
        AlertDialog.Builder(this)
            .setTitle(if (enabled) "Berbagi lokasi sedang aktif" else "Izin berbagi lokasi")
            .setMessage(message)
            .setNegativeButton("Tutup", null)
            .setPositiveButton(if (enabled) "Hentikan berbagi" else "Aktifkan berbagi") { _, _ ->
                if (enabled) stopChildLocationSharing() else requestChildLocationSharing()
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
        AlertDialog.Builder(this)
            .setTitle("Kontrol blokir aplikasi")
            .setMessage(
                "Jika disetujui, HP anak mengirim daftar nama aplikasi yang tersedia agar orang tua dapat memilih aplikasi yang dibatasi. Saat aplikasi yang diblokir dibuka, SantetHP akan mengembalikan ke layar utama.\n\nUntuk menjalankannya Android meminta Accessibility Service diaktifkan secara manual. Layanan hanya memeriksa nama paket aplikasi yang sedang tampil; tidak membaca isi layar, pesan, foto, kata sandi, atau mengetik untuk pengguna. Anak dapat mencabut akses kapan saja dari Pengaturan Android."
            )
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
        val message = if (enabled) {
            "Pemantauan aplikasi aktif sedang diizinkan. SantetHP hanya membagikan nama aplikasi yang tampil ketika berpindah aplikasi dan waktu deteksinya. SantetHP tidak membaca isi layar, pesan, foto, kata sandi, atau ketikan. Anda dapat menghentikannya kapan saja."
        } else {
            "Jika disetujui, SantetHP mengirim nama aplikasi yang sedang tampil ke HP orang tua setiap kali aplikasi berganti. Data dikirim melalui layanan Aksesibilitas Android yang diaktifkan secara manual. SantetHP hanya membaca nama paket aplikasi, bukan isi layar, pesan, foto, kata sandi, atau ketikan. Fitur ini terpisah dari blokir aplikasi dan dapat dihentikan kapan saja dari HP anak."
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("Pemantauan aplikasi aktif")
            .setMessage(message)
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
                    AlertDialog.Builder(this)
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

                AlertDialog.Builder(this)
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
                        val durationDialog = AlertDialog.Builder(this)
                            .setTitle("Durasi untuk $name")
                            .setMessage(
                                if (currentUntil != null) {
                                    "Aplikasi ini sedang diblokir. Masukkan durasi baru dalam menit (1 sampai 10080) untuk memulai ulang hitungan waktunya."
                                } else {
                                    "Masukkan durasi blokir dalam menit (1 sampai 10080). Setelah waktunya habis, aplikasi otomatis dapat dibuka kembali."
                                }
                            )
                            .setView(durationInput)
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

        AlertDialog.Builder(this)
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

        childAudioActivityText.text = when {
            liveAudioActive && mp3AudioActive ->
                "AKTIVITAS HP ANAK\nMP3 dan audio live sedang aktif"
            liveAudioActive ->
                "AKTIVITAS HP ANAK\nAudio langsung sedang diputar"
            mp3AudioActive ->
                "AKTIVITAS HP ANAK\nMP3 sedang diputar"
            else ->
                "AKTIVITAS HP ANAK\nTidak sedang memutar audio"
        }
        childAudioActivityText.setTextColor(
            if (liveAudioActive || mp3AudioActive) Color.rgb(28, 150, 92)
            else Color.rgb(104, 117, 140)
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
        AlertDialog.Builder(this)
            .setTitle("Pemantauan semua notifikasi")
            .setMessage(
                "Jika diaktifkan, SantetHP dapat membaca nama aplikasi, judul, dan isi notifikasi yang ditampilkan Android pada HP ini, lalu menyimpannya agar dapat dilihat dari HP orang tua. Termasuk notifikasi pesan dan kode verifikasi jika aplikasi menampilkannya. Jangan aktifkan jika tidak setuju membagikan informasi tersebut. Izin khusus tetap harus diaktifkan secara manual pada Pengaturan Android."
            )
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
        val enabled = getSharedPreferences("parent_audio", MODE_PRIVATE)
            .getBoolean("child_gallery_sharing_enabled", false)
        val mediaAccess = hasGalleryMediaAccess()
        val serverConfirmed = getSharedPreferences("parent_audio", MODE_PRIVATE)
            .getBoolean("child_gallery_server_consent_confirmed", false)
        childGalleryPanel.text = when {
            !enabled -> "🖼️  BERBAGI GALERI HP INI\nNonaktif • Ketuk untuk membaca penjelasan dan memilih media yang boleh dibagikan"
            !mediaAccess -> "🖼️  BERBAGI GALERI BELUM SIAP\nIzin foto/video Android belum tersedia. Ketuk untuk mengatur."
            !serverConfirmed -> "🖼️  MENUNGGU KONFIRMASI\nPersetujuan lokal aktif, tetapi status server belum tersimpan. Periksa koneksi internet."
            else -> "🖼️  BERBAGI GALERI AKTIF\nIzin media dan persetujuan server tersedia. File asli tidak dikirim otomatis. Ketuk untuk menghentikan."
        }
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
            stopChildGalleryP2p()
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

    private fun showChildGalleryConsent() {
        if (currentRole != "child") return
        val prefs = getSharedPreferences("parent_audio", MODE_PRIVATE)
        val enabled = prefs.getBoolean("child_gallery_sharing_enabled", false)
        val message = "Jika diaktifkan, daftar foto dan video dari HP ini dapat dilihat oleh HP orang tua yang sudah dipasangkan. Pada rancangan ini, thumbnail ditampilkan lebih dulu; file asli/HD tidak dikirim otomatis dan baru diminta saat orang tua memilih item. Android akan meminta izin media. Anda dapat menghentikan berbagi kapan saja."
        AlertDialog.Builder(this)
            .setTitle(if (enabled) "Berbagi galeri sedang aktif" else "Persetujuan berbagi galeri")
            .setMessage(message)
            .setNegativeButton("Tutup", null)
            .setPositiveButton(if (enabled) "Hentikan berbagi" else "Saya setuju • Lanjutkan") { _, _ ->
                if (enabled) {
                    prefs.edit().putBoolean("child_gallery_sharing_enabled", false).apply()
                    stopChildGalleryP2p()
                    updateGalleryConsentOnServer(false)
                    updateChildGalleryPanel()
                } else {
                    prefs.edit()
                        .putBoolean("child_gallery_sharing_enabled", true)
                        .putBoolean("child_gallery_server_consent_confirmed", false)
                        .apply()
                    updateChildGalleryPanel()
                    // If Android permissions were already granted, do not wait for a
                    // permission callback that may not arrive; save consent immediately.
                    if (hasGalleryMediaAccess()) {
                        updateGalleryConsentOnServer(true) { startChildGalleryP2pIfAllowed() }
                    } else {
                        requestPermissions(galleryMediaPermissions(), GALLERY_MEDIA_PERMISSION)
                    }
                }
            }
            .show()
    }

    private fun showParentGalleryStatus() {
        if (currentRole != "parent") return
        val code = selectedChildCode
        if (code.isNullOrBlank()) {
            Toast.makeText(this, "Pilih HP anak terlebih dahulu.", Toast.LENGTH_LONG).show()
            return
        }
        val enabled = childSnapshots[code]?.getBoolean("gallerySharingEnabled") == true
        val name = childNames[code] ?: "HP Anak"
        if (!enabled) {
            AlertDialog.Builder(this)
                .setTitle("Galeri • " + name)
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
        val dialog = AlertDialog.Builder(this)
            .setTitle("Galeri • " + name)
            .setView(content)
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
                parentGalleryIndexRequested = parentGalleryProtocol?.requestIndex() == true
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
            stopParentGalleryP2p()
            parentGalleryDialog = null
            parentGalleryStatus = null
            parentGalleryList = null
        }
        dialog.show()

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
                                setPadding(dp(7), dp(7), dp(7), dp(7))
                                background = GradientDrawable().apply {
                                    setColor(Color.WHITE)
                                    cornerRadius = dp(12).toFloat()
                                    setStroke(dp(1), Color.rgb(226, 233, 243))
                                }
                                layoutParams = LinearLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT, dp(82)
                                ).apply { setMargins(0, 0, 0, dp(7)) }
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
                                AlertDialog.Builder(this)
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
                    if (message.contains("P2P TERHUBUNG") && !parentGalleryIndexRequested) {
                        parentGalleryIndexRequested = protocol.requestIndex()
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

    private fun requestNextParentGalleryThumbnail() {
        if (parentGalleryThumbInFlight != null || parentGalleryDialog?.isShowing != true) return
        val next = parentGalleryThumbQueue.removeFirstOrNull() ?: return
        parentGalleryThumbInFlight = next
        if (parentGalleryProtocol?.requestThumbnail(next) != true) {
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
        val dialog = AlertDialog.Builder(this)
            .setTitle("Notifikasi • ${childNames[code] ?: "HP Anak"}")
            .setView(scroll)
            .setNegativeButton("Tutup", null)
            .setNeutralButton("Hapus riwayat", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                AlertDialog.Builder(this)
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
                    val timestamp = document.getTimestamp("postedAt")
                    val whenText = timestamp?.toDate()?.let { formatter.format(it) } ?: "Waktu tidak tersedia"
                    val card = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(dp(12), dp(10), dp(12), dp(10))
                        background = GradientDrawable().apply {
                            setColor(Color.WHITE)
                            cornerRadius = dp(13).toFloat()
                            setStroke(dp(1), Color.rgb(224, 229, 241))
                        }
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                        ).apply { setMargins(0, dp(4), 0, dp(4)) }
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

    private fun renderChildDeviceStatus(snapshot: DocumentSnapshot) {
        val battery = snapshot.getLong("deviceBatteryPercent")?.toInt() ?: -1
        val charging = snapshot.getBoolean("deviceCharging") == true
        val temperature = snapshot.getDouble("deviceBatteryTempC")
        val network = snapshot.getString("deviceNetworkType") ?: "Belum diketahui"
        val operator = snapshot.getString("deviceOperatorName")?.takeIf { it.isNotBlank() }
            ?: "Tidak terdeteksi / tanpa SIM"
        val internetValidated = snapshot.getBoolean("deviceInternetValidated") == true
        val updatedAt = snapshot.getTimestamp("deviceStatusAt")
        val ageMs = updatedAt?.toDate()?.time?.let { System.currentTimeMillis() - it }
        val fresh = ageMs != null && ageMs in -60_000L..15 * 60 * 1000L
        val updateLabel = updatedAt?.let {
            android.text.format.DateFormat.getTimeFormat(this@MainActivity).format(it.toDate())
        } ?: "belum tersedia"
        val batteryLabel = if (battery in 0..100) "$battery%" else "Tidak tersedia"
        val chargeLabel = if (charging) " • Mengisi daya" else ""
        val temperatureLabel = temperature?.takeIf { it >= 0.0 }?.let {
            " • ${"%.1f".format(java.util.Locale.US, it)}°C"
        } ?: ""
        val internetLabel = if (internetValidated) "Internet aktif" else "Internet belum terverifikasi"

        val selectedName = selectedChildCode?.let { childNames[it] }
            ?.takeIf { it.isNotBlank() } ?: "ANAK"
        childDeviceStatusText.text = buildString {
            append("KONDISI HP $selectedName\n")
            append("Baterai: $batteryLabel$chargeLabel$temperatureLabel\n")
            append("Jaringan: $network\n")
            append("Operator SIM: $operator\n")
            append("$internetLabel\n")
            append(if (fresh) "Diperbarui: $updateLabel" else "Data mungkin sudah lama • terakhir: $updateLabel")
            append("\n\nRIWAYAT PENGGUNAAN APLIKASI (24 JAM)")
            val permissionGranted = snapshot.getBoolean("appUsagePermissionGranted") == true
            val usageAt = snapshot.getTimestamp("appUsageAt")
            val usageAge = usageAt?.toDate()?.time?.let { System.currentTimeMillis() - it }
            val usageFresh = usageAge != null && usageAge in -60_000L..30 * 60 * 1000L
            val usageEnabled = snapshot.getBoolean("appUsageEnabled") == true
            if (!usageEnabled) {
                append("\nRiwayat penggunaan belum diaktifkan dari HP anak")
            } else if (!permissionGranted) {
                append("\nMenunggu izin Usage Access dari HP anak")
            } else {
                val apps = snapshot.get("appUsageSummary") as? List<*>
                if (apps.isNullOrEmpty()) {
                    append("\nBelum ada data penggunaan yang tercatat")
                } else {
                    apps.take(8).forEach { item ->
                        val app = item as? Map<*, *> ?: return@forEach
                        val name = app["appName"] as? String ?: "Aplikasi"
                        val minutes = (app["foregroundMinutes"] as? Number)?.toInt() ?: 0
                        append("\n" + name + ": " + (minutes / 60) + " jam " + (minutes % 60) + " menit")
                    }
                }
                append(if (usageFresh) "\nData penggunaan diperbarui" else "\nData penggunaan mungkin sudah lama")
            }
            append("\n\nPEMANTAUAN APLIKASI AKTIF")
            if (snapshot.getBoolean("appActivityConsent") != true) {
                append("\nBelum diaktifkan dari HP anak")
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
                        append("\nMenunggu pergantian aplikasi pertama")
                    } else {
                        val activityTime = activityAt?.let {
                            android.text.format.DateFormat.getTimeFormat(this@MainActivity).format(it.toDate())
                        }
                        append("\nTerakhir terdeteksi: $activeName")
                        if (activityTime != null) append(" • $activityTime")
                    }
                } else {
                    append("\n10 aplikasi terakhir dibuka:")
                    history.forEachIndexed { index, entry ->
                        val time = entry.second?.let {
                            android.text.format.DateFormat.getTimeFormat(this@MainActivity).format(it.toDate())
                        } ?: "waktu tidak tersedia"
                        append("\n${index + 1}. ${entry.first} • $time")
                    }
                    append("\nDiurutkan dari yang paling baru")
                }
            }
        }
        childDeviceStatusText.setTextColor(
            if (fresh) Color.rgb(38, 59, 99) else Color.rgb(104, 117, 140)
        )


        val blockConsent = snapshot.getBoolean("appBlockConsent") == true
        val nowForBlocks = System.currentTimeMillis()
        val activeBlockCount = (snapshot.get("blockedApps") as? List<*>)
            .orEmpty().count { raw ->
                val item = raw as? Map<*, *> ?: return@count false
                ((item["blockedUntilMillis"] as? Number)?.toLong() ?: 0L) > nowForBlocks
            }
        val installedApps = (snapshot.get("appBlockInstalledApps") as? List<*>)?.size ?: 0
        parentAppBlockPanel.text = if (!blockConsent) {
            "🚫  BLOKIR APLIKASI PER DURASI\nMenunggu persetujuan dari HP anak"
        } else {
            "🚫  BLOKIR APLIKASI PER DURASI\nAplikasi tersedia: $installedApps\nSedang diblokir: $activeBlockCount\nKetuk untuk memilih aplikasi dan menentukan durasi"
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

    private fun listenForChild(code: String) {
        childListeners[code]?.remove()

        childListeners[code] = db.collection("pairings")
            .document(code)
            .addSnapshotListener { snapshot, error ->

                if (error != null) {
                    statusText.text = "Gagal memeriksa pasangan"
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    childSnapshots[code] = snapshot
                    val childUid = snapshot.getString("childUid")
                    val connected = !childUid.isNullOrEmpty()
                    childConnected[code] = connected
                    renderChildrenList()
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
                        if (currentRole == "parent") showParentDashboard()
                    }
                }
            }
    }

    private fun showCodeDialog() {
        val input = EditText(this).apply {
            hint = "Masukkan 8 karakter kode"
            isSingleLine = true
        }

        AlertDialog.Builder(this)
            .setTitle("Hubungkan dengan Orang Tua")
            .setMessage("Masukkan kode dari HP orang tua.")
            .setView(input)
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
        AlertDialog.Builder(this)
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

        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED

        super.onDestroy()
    }
}
