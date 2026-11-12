
package com.santethp
import android.content.Intent
import android.net.Uri
import java.io.File
import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.os.Bundle
import android.graphics.Color
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Path
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
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

import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom
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
    private lateinit var childModePanel: TextView
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
    private val staleStatusRunnable = Runnable {
        latestPairingSnapshot?.let { renderChildAudioActivity(it) }
    }
    private var room: Room? = null
    private var isConnecting = false
    private var isMicrophoneActive = false
    private var currentRole = ""

    private val characters = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    private val workerUrl =
        "https://parent-audio-token.yudhis-r15.workers.dev/token"

    companion object {
    private const val MIC_PERMISSION = 1001
    private const val MP3_PICKER = 1002
    private const val CAMERA_PERMISSION = 1003
}
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        lifecycleRegistry.currentState = Lifecycle.State.STARTED

        auth = FirebaseAuth.getInstance()

        rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.TOP
            setPadding(dp(12), dp(8), dp(12), dp(12))
            setBackgroundColor(Color.rgb(244, 247, 252))
        }

        val title = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(15), dp(9), dp(15), dp(9))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(15, 29, 64), Color.rgb(35, 69, 139), Color.rgb(67, 104, 183))
            ).apply {
                cornerRadius = dp(22).toFloat()
                setStroke(dp(1), Color.argb(68, 255, 255, 255))
            }
            elevation = dp(5).toFloat()

            addView(TextView(this@MainActivity).apply {
                text = "PARENTAL CARE  •  FAMILY CONNECT"
                textSize = 9.5f
                letterSpacing = 0.13f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(Color.rgb(190, 211, 255))
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            })

            val brandRow = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(5), 0, dp(2))
            }
            brandRow.addView(TextView(this@MainActivity).apply {
                text = "SantetHP"
                textSize = 26f
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
                textSize = 12.5f
                setTextColor(Color.rgb(224, 234, 255))
                letterSpacing = 0.015f
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            })
        }

        statusText = TextView(this).apply {
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            text = "Memeriksa koneksi..."
            textSize = 13f
            setTypeface(null, android.graphics.Typeface.BOLD)
            letterSpacing = 0.012f
            setTextColor(Color.rgb(34, 57, 96))
            gravity = Gravity.CENTER_VERTICAL
            setLineSpacing(dp(2).toFloat(), 1f)
            setPadding(dp(13), dp(10), dp(13), dp(10))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.WHITE, Color.rgb(242, 247, 255))
            ).apply {
                cornerRadius = dp(16).toFloat()
                setStroke(dp(1), Color.rgb(190, 211, 242))
            }
            elevation = dp(2).toFloat()
            minHeight = dp(48)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, dp(2), 0, dp(4)) }
        }

        connectionStatusText = TextView(this).apply {
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            text = "○ OFFLINE"
            textSize = 11f
            setTypeface(null, android.graphics.Typeface.BOLD)
            letterSpacing = 0.025f
            setTextColor(Color.rgb(105, 116, 137))
            gravity = Gravity.CENTER
            setPadding(dp(9), dp(6), dp(9), dp(6))
            background = GradientDrawable().apply {
                setColor(Color.rgb(239, 243, 249))
                cornerRadius = dp(14).toFloat()
                setStroke(dp(1), Color.rgb(215, 223, 235))
            }
            minWidth = dp(76)
            maxLines = 1
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(40)
            ).apply { setMargins(dp(4), 0, dp(4), 0) }
        }

        updateInfoText = TextView(this).apply {
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            text = "Belum ada pemeriksaan pembaruan."
            textSize = 11.5f
            setTextColor(Color.rgb(91, 108, 137))
            gravity = Gravity.CENTER_VERTICAL
            setLineSpacing(dp(2).toFloat(), 1f)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = GradientDrawable().apply {
                setColor(Color.rgb(247, 249, 253))
                cornerRadius = dp(14).toFloat()
                setStroke(dp(1), Color.rgb(222, 230, 243))
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, dp(3), 0, dp(5)) }
            visibility = View.GONE
        }

        childAudioActivityText = TextView(this).apply {
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            text = "STATUS AUDIO HP ANAK\nTidak sedang memutar audio"
            textSize = 13f
            setTypeface(null, android.graphics.Typeface.BOLD)
            letterSpacing = 0.012f
            setTextColor(Color.rgb(32, 55, 92))
            gravity = Gravity.CENTER_VERTICAL
            setLineSpacing(dp(3).toFloat(), 1f)
            setPadding(dp(12), dp(9), dp(12), dp(9))
            minHeight = dp(58)
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.WHITE, Color.rgb(237, 245, 255))
            ).apply {
                cornerRadius = dp(18).toFloat()
                setStroke(dp(1), Color.rgb(190, 211, 242))
            }
            elevation = dp(2).toFloat()
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, dp(2), 0, dp(4)) }
        }

        codeText = TextView(this).apply {
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            textSize = 26f
            setTypeface(null, android.graphics.Typeface.BOLD)
            letterSpacing = 0.14f
            setTextColor(Color.rgb(39, 78, 180))
            gravity = Gravity.CENTER
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.WHITE, Color.rgb(232, 240, 255))
            ).apply {
                cornerRadius = dp(17).toFloat()
                setStroke(dp(1), Color.rgb(190, 211, 242))
            }
            elevation = dp(2).toFloat()
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, dp(4), 0, dp(6)) }
        }

        pairingInstructionText = TextView(this).apply {
            text = "HUBUNGKAN HP ANAK\nMasukkan kode ini pada aplikasi SantetHP di perangkat anak. Kode berlaku selama 15 menit."
            textSize = 13.5f
            setTypeface(null, android.graphics.Typeface.BOLD)
            letterSpacing = 0.008f
            setTextColor(Color.rgb(38, 59, 99))
            gravity = Gravity.CENTER
            setLineSpacing(dp(3).toFloat(), 1f)
            setPadding(dp(15), dp(14), dp(15), dp(14))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.WHITE, Color.rgb(239, 245, 255))
            ).apply {
                cornerRadius = dp(17).toFloat()
                setStroke(dp(1), Color.rgb(195, 215, 243))
            }
            elevation = dp(2).toFloat()
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, dp(7), 0, dp(4)) }
            visibility = View.GONE
        }

        roleIntroText = TextView(this).apply {
            text = "SELAMAT DATANG DI SANTETHP\nPilih jenis perangkat untuk melanjutkan. Pastikan Anda memilih peran yang sesuai dengan HP ini."
            textSize = 13.5f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(Color.rgb(48, 67, 103))
            gravity = Gravity.CENTER
            setLineSpacing(dp(3).toFloat(), 1f)
            setPadding(dp(16), dp(15), dp(16), dp(15))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.WHITE, Color.rgb(237, 244, 255))
            ).apply {
                cornerRadius = dp(18).toFloat()
                setStroke(dp(1), Color.rgb(205, 220, 246))
            }
            elevation = dp(2).toFloat()
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, dp(10), 0, dp(5)) }
            visibility = View.GONE
        }
        parentButton = createButton("👨‍👩‍👧  ORANG TUA\nKelola perangkat anak")
        childButton = createButton("📱  PERANGKAT ANAK\nHubungkan ke orang tua")
        parentButton.contentDescription = "Pilih mode orang tua untuk mengelola dan menghubungkan perangkat anak"
        childButton.contentDescription = "Pilih mode perangkat anak untuk menghubungkan HP ini ke orang tua"
        listOf(parentButton, childButton).forEach { button ->
            button.textSize = 13.5f
            button.gravity = Gravity.CENTER
            button.setLineSpacing(dp(2).toFloat(), 1f)
            button.minHeight = dp(76)
            button.setPadding(dp(12), dp(9), dp(12), dp(9))
            button.layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, dp(4), 0, dp(4)) }
        }
        parentButton.background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(Color.rgb(35, 73, 174), Color.rgb(55, 105, 220))
        ).apply { cornerRadius = dp(18).toFloat() }
        childButton.background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(Color.rgb(18, 139, 151), Color.rgb(29, 104, 176))
        ).apply {
            cornerRadius = dp(18).toFloat()
            setStroke(dp(1), Color.rgb(89, 184, 194))
        }
        startButton = createActionButton("🎙", "BICARA LIVE", Color.rgb(55, 80, 220))
        stopButton = createActionButton("🎙", "HENTIKAN LIVE", Color.rgb(220, 75, 75))
        sendMp3Button = createActionButton("♫", "PUTAR MP3", Color.rgb(55, 80, 220))
        playMp3Button = createButton("PUTAR MP3 DITERIMA")
        playMp3Button.contentDescription = "Putar file MP3 yang sudah diterima dari perangkat orang tua"
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
        val actionParams = LinearLayout.LayoutParams(0, dp(72), 1f).apply {
            setMargins(dp(3), dp(3), dp(3), dp(3))
        }
        microphoneSlot.addView(startButton, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(72)
        ))
        microphoneSlot.addView(stopButton, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(72)
        ))
        firstActionRow.addView(microphoneSlot, LinearLayout.LayoutParams(actionParams))
        firstActionRow.addView(sendMp3Button, LinearLayout.LayoutParams(actionParams))
        secondActionRow.addView(cameraLiveButton, LinearLayout.LayoutParams(actionParams))
        secondActionRow.addView(adminWhatsAppButton, LinearLayout.LayoutParams(actionParams))
        actionButtonsRow.addView(firstActionRow)
        actionButtonsRow.addView(secondActionRow)
        addChildButton = createButton("＋")
        addChildButton.textSize = 25f
        addChildButton.contentDescription = "Tambahkan perangkat anak baru dan buat kode pairing untuk menghubungkannya"
        addChildButton.layoutParams = LinearLayout.LayoutParams(dp(48), dp(48)).apply { setMargins(dp(6), dp(3), 0, dp(3)) }
        selectedChildButton = createButton("Pilih anak  ▾")
        selectedChildButton.gravity = Gravity.CENTER_VERTICAL
        selectedChildButton.setOnClickListener { toggleChildrenPanel() }
        selectedChildButton.setTextColor(Color.rgb(35, 57, 96))
        selectedChildButton.textSize = 13f
        selectedChildButton.maxLines = 1
        selectedChildButton.ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
        selectedChildButton.setPadding(dp(12), 0, dp(8), 0)
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
        updateButton.contentDescription = "Periksa apakah tersedia pembaruan aplikasi SantetHP"
        cameraStandbyButton = createButton("AKTIFKAN SIAGA KAMERA")
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
            AppUpdateManager.checkForUpdates(this, versionCode) { message -> updateInfoText.text = message }
        }

        rootLayout.addView(title, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(8) })
        childModePanel = TextView(this).apply {
            text = "🛡️  HP ANAK SIAP\n\nPerangkat berhasil terhubung\nMenunggu perintah dari HP orang tua"
            textSize = 15f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(Color.rgb(35, 53, 88))
            gravity = Gravity.CENTER
            setLineSpacing(dp(3).toFloat(), 1f)
            setPadding(dp(18), dp(22), dp(18), dp(22))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.WHITE, Color.rgb(231, 239, 255))
            ).apply {
                cornerRadius = dp(20).toFloat()
                setStroke(dp(1), Color.rgb(207, 222, 248))
            }
            elevation = dp(3).toFloat()
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, dp(8), 0, dp(10)) }
            visibility = View.GONE
        }
        rootLayout.addView(roleIntroText)
        rootLayout.addView(childModePanel)
        val deviceSectionTitle = TextView(this).apply {
            text = "PERANGKAT ANAK"
            textSize = 11.5f
            setTextColor(Color.rgb(49, 82, 145))
            setTypeface(null, android.graphics.Typeface.BOLD)
            letterSpacing = 0.105f
            setPadding(dp(4), dp(4), dp(4), dp(3))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, dp(2), 0, 0) }
        }
        androidx.core.view.ViewCompat.setAccessibilityHeading(deviceSectionTitle, true)
        rootLayout.addView(deviceSectionTitle)
        parentOnlyViews.add(deviceSectionTitle)
        val childHeaderRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        childHeaderRow.addView(selectedChildButton, LinearLayout.LayoutParams(0, dp(48), 1f))
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
            setPadding(dp(4), dp(4), dp(4), dp(3))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, dp(2), 0, 0) }
        }
        androidx.core.view.ViewCompat.setAccessibilityHeading(statusHeading, true)
        rootLayout.addView(statusHeading)
        parentOnlyViews.add(statusHeading)
        rootLayout.addView(statusText)
        rootLayout.addView(pairingInstructionText)
        rootLayout.addView(codeText)
        val activityHeading = TextView(this).apply {
            text = "STATUS PEMUTARAN AUDIO"
            textSize = 11.5f
            setTextColor(Color.rgb(49, 82, 145))
            setTypeface(null, android.graphics.Typeface.BOLD)
            letterSpacing = 0.105f
            setPadding(dp(4), dp(4), dp(4), dp(3))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, dp(2), 0, 0) }
        }
        androidx.core.view.ViewCompat.setAccessibilityHeading(activityHeading, true)
        rootLayout.addView(activityHeading)
        parentOnlyViews.add(activityHeading)
        rootLayout.addView(childAudioActivityText)
        parentOnlyViews.add(childAudioActivityText)
        val controlHeading = TextView(this).apply {
            text = "KONTROL UTAMA"
            textSize = 11.5f
            setTextColor(Color.rgb(49, 82, 145))
            setTypeface(null, android.graphics.Typeface.BOLD)
            letterSpacing = 0.105f
            setPadding(dp(4), dp(4), dp(4), dp(3))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, dp(2), 0, 0) }
        }
        androidx.core.view.ViewCompat.setAccessibilityHeading(controlHeading, true)
        rootLayout.addView(controlHeading)
        parentOnlyViews.add(controlHeading)
        rootLayout.addView(actionButtonsRow)
        rootLayout.addView(cancelPairingButton)
        rootLayout.addView(playMp3Button)
        rootLayout.addView(cameraStandbyButton)

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
            updateCameraStandbyButton()
            refreshFcmToken()
            restoreCameraStandbyIfNeeded()
        }
    }

    private fun showRoleSelection() {
        roleIntroText.visibility = View.VISIBLE
        childModePanel.visibility = View.GONE
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

    private fun showParentDashboard() {
        roleIntroText.visibility = View.GONE
        childModePanel.visibility = View.GONE
        parentOnlyViews.forEach { it.visibility = View.VISIBLE }
        updateButton.visibility = View.VISIBLE
        actionButtonsRow.visibility = View.VISIBLE
        parentButton.visibility = View.GONE
        childButton.visibility = View.GONE
        selectedChildButton.visibility = View.VISIBLE
        connectionStatusText.visibility = View.VISIBLE
        updateInfoText.visibility = View.VISIBLE
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
            setPadding(dp(8), dp(7), dp(7), dp(8))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.WHITE, Color.rgb(246, 249, 255))
            ).apply {
                cornerRadius = dp(16).toFloat()
                setStroke(dp(1), Color.argb(88, accent))
            }
            elevation = dp(2).toFloat()
            isClickable = true
            isFocusable = true
            contentDescription = label
        }
        val iconView = TextView(this).apply {
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            text = icon
            textSize = 22.5f
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
            layoutParams = LinearLayout.LayoutParams(dp(36), dp(36)).apply {
                rightMargin = dp(6)
            }
        }
        val helper = when (label) {
            "BICARA LIVE" -> "Dengar suara sekitar"
            "HENTIKAN LIVE" -> "Akhiri audio live"
            "PUTAR MP3" -> "Kirim MP3 ke anak"
            "KAMERA LIVE" -> "Lihat kamera anak"
            "CHAT ADMIN" -> "Hubungi admin"
            else -> "Kontrol perangkat"
        }
        card.contentDescription = "$label. $helper"

        val textStack = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(1), 0, dp(1))
        }
        val labelView = TextView(this).apply {
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            text = label
            textSize = 11.5f
            setTypeface(null, android.graphics.Typeface.BOLD)
            letterSpacing = 0.012f
            setTextColor(Color.rgb(27, 43, 73))
            maxLines = 2
            setLineSpacing(dp(1).toFloat(), 1f)
        }
        val helperView = TextView(this).apply {
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            text = helper
            textSize = 9.5f
            setTextColor(Color.rgb(88, 105, 134))
            setPadding(0, dp(3), 0, 0)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
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
                setMargins(0, dp(6), 0, dp(6))
            }
        }
    }

    private fun savePairing(code: String, role: String) {
        val editor = getSharedPreferences("parent_audio", MODE_PRIVATE).edit()
            .putString("pair_role", role)
        if (role == "child") editor.putString("pair_code", code)
        editor.apply()

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
        renderChildrenList()
    }

    private fun updateSelectedChildButton() {
        val name = selectedChildCode?.let { childNames[it] }
        val arrow = if (isChildrenPanelExpanded) "▴" else "▾"
        selectedChildButton.text = if (name.isNullOrBlank()) "▣  Pilih perangkat anak  $arrow" else "▣  $name  $arrow"
        val connected = selectedChildCode?.let { childConnected[it] == true } == true
        connectionStatusText.text = if (connected) "● ONLINE" else "○ OFFLINE"
        val listStateDescription = if (isChildrenPanelExpanded) {
            "Daftar perangkat sedang terbuka. Ketuk untuk menutup daftar."
        } else {
            "Daftar perangkat sedang tertutup. Ketuk untuk membukanya."
        }
        selectedChildButton.contentDescription = if (name.isNullOrBlank()) {
            "Pilih perangkat anak. $listStateDescription"
        } else {
            "Perangkat anak $name, status " + (if (connected) "online" else "offline") +
                ". $listStateDescription"
        }
        connectionStatusText.contentDescription = if (connected) {
            "Status koneksi: online"
        } else {
            "Status koneksi: offline"
        }
        connectionStatusText.setTextColor(
            if (connected) Color.rgb(24, 128, 82) else Color.rgb(105, 116, 137)
        )
        (connectionStatusText.background as? GradientDrawable)?.apply {
            setColor(if (connected) Color.rgb(226, 247, 237) else Color.rgb(239, 243, 249))
            setStroke(
                dp(1),
                if (connected) Color.rgb(196, 230, 211) else Color.rgb(215, 223, 235)
            )
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
        childrenContainer.setPadding(dp(8), dp(8), dp(8), dp(8))
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
                minHeight = dp(112)
                setPadding(dp(14), dp(14), dp(14), dp(14))
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
                textSize = 28f
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, dp(5))
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
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
                textSize = 11.5f
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
                setPadding(dp(9), dp(7), dp(7), dp(7))
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
                contentDescription = "Perangkat anak $name. " +
                    (if (connected) "Status online. " else "Status offline. ") +
                    (if (selectedChildCode == code) "Sedang dipilih. " else "") +
                    "Ketuk untuk memilih, tekan lama untuk mengubah nama."
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, dp(2), 0, dp(2)) }
            }
            val phoneIcon = ChildAvatarView().apply {
                layoutParams = LinearLayout.LayoutParams(dp(44), dp(44)).apply {
                    rightMargin = dp(9)
                }
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            row.addView(phoneIcon)
            val details = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            details.addView(TextView(this).apply {
                text = name
                textSize = 14f
                setTypeface(null, android.graphics.Typeface.BOLD)
                letterSpacing = 0.005f
                setTextColor(Color.rgb(27, 43, 73))
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            })
            details.addView(TextView(this).apply {
                text = if (connected) "● ONLINE  •  Terhubung" else "○ OFFLINE  •  Menunggu koneksi"
                contentDescription = if (connected) "Perangkat $name sedang online dan terhubung" else "Perangkat $name sedang offline dan menunggu koneksi"
                textSize = 10f
                setTypeface(null, android.graphics.Typeface.BOLD)
                letterSpacing = 0.005f
                setPadding(dp(8), dp(4), dp(8), dp(4))
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
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                textSize = 16f
                setTypeface(null, android.graphics.Typeface.BOLD)
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                minWidth = dp(28)
                minHeight = dp(28)
                setPadding(0, 0, 0, 0)
                background = GradientDrawable().apply {
                    setColor(Color.rgb(55, 100, 210))
                    cornerRadius = dp(20).toFloat()
                }
            })
            val removeButton = Button(this).apply {
                text = "Lepaskan"
                contentDescription = "Lepaskan perangkat anak $name"
                textSize = 10.5f
                isAllCaps = false
                minHeight = dp(48)
                minWidth = dp(72)
                setPadding(dp(9), dp(6), dp(9), dp(6))
                setTextColor(Color.rgb(169, 62, 72))
                background = GradientDrawable().apply {
                    setColor(Color.rgb(255, 247, 247))
                    cornerRadius = dp(10).toFloat()
                    setStroke(dp(1), Color.rgb(245, 220, 222))
                }
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, dp(48)
                ).apply { leftMargin = dp(7) }
                setOnClickListener { promptRemoveChild(code) }
            }
            row.addView(removeButton)
            row.setOnClickListener { selectChild(code) }
            row.setOnLongClickListener { promptRenameChild(code); true }
            childrenContainer.addView(row)
        }

        val addRow = TextView(this).apply {
            text = "＋    Tambahkan perangkat anak"
            contentDescription = "Tambahkan perangkat anak baru"
            textSize = 12f
            setTypeface(null, android.graphics.Typeface.BOLD)
            letterSpacing = 0.008f
            setTextColor(Color.rgb(42, 82, 174))
            gravity = Gravity.CENTER
            minHeight = dp(48)
            setPadding(dp(12), dp(10), dp(12), dp(10))
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
            contentDescription = "Kolom untuk memasukkan nama perangkat anak"
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
            .apply()

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
        selectedChildCode = code
        isChildrenPanelExpanded = false
        childrenContainer.visibility = View.GONE
        updateSelectedChildButton()
        renderChildrenList()
        if (childConnected[code] != true) {
            codeText.text = code
            statusText.text = "Menunggu HP ${childNames[code] ?: "anak"} terhubung..."
            return
        }
        if (activeRoomCode == code && room != null) return
        selectedChildCode = code
        updateSelectedChildButton()
        renderChildrenList()
        codeText.text = code
        statusText.text = "Menghubungkan ke ${childNames[code] ?: "anak"}..."
        activityScope.launch {
            try {
                room?.localParticipant?.setMicrophoneEnabled(false)
                room?.disconnect()
                room?.release()
            } catch (_: Exception) { }
            isMicrophoneActive = false
            room = null
            activeRoomCode = null
            mp3TransferManager = null
            isConnecting = false
            connectToLiveKit(code)
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
                selectedChildCode = childNames.keys.first()
                codeText.text = selectedChildCode
                statusText.text = "Pilih anak yang ingin dihubungkan"
            }
        } else {
            val code = prefs.getString("pair_code", null) ?: return
            codeText.text = code
            showChildConnectedScreen()
            watchChildPairingRemoval(code)
            statusText.text = "Memulihkan koneksi audio (mikrofon tetap mati)..."
            startChildAudioConnection()
        }
    }



    private fun requestRemoteCamera() {
        if (currentRole != "parent") return
        val code = selectedChildCode
        if (code.isNullOrBlank()) {
            statusText.text = "Pilih HP anak terlebih dahulu"
            return
        }
        val user = auth.currentUser
        if (user == null) {
            statusText.text = "Sesi Firebase orang tua belum tersedia"
            return
        }
        statusText.text = "Mengirim permintaan kamera ke HP anak…"
        user.getIdToken(false).addOnSuccessListener { result ->
            val idToken = result.token
            if (idToken.isNullOrBlank()) {
                statusText.text = "Token autentikasi kosong"
                return@addOnSuccessListener
            }
            activityScope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        val connection = URL(workerUrl.replace("/token", "/request-camera"))
                            .openConnection() as HttpURLConnection
                        try {
                            connection.requestMethod = "POST"
                            connection.connectTimeout = 15000
                            connection.readTimeout = 15000
                            connection.doOutput = true
                            connection.setRequestProperty("Content-Type", "application/json")
                            val body = JSONObject()
                                .put("idToken", idToken)
                                .put("code", code)
                                .put("requestId", java.util.UUID.randomUUID().toString())
                                .put("action", "start")
                                .put("facing", "back")
                            connection.outputStream.use {
                                it.write(body.toString().toByteArray(Charsets.UTF_8))
                            }
                            val status = connection.responseCode
                            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                            val response = JSONObject(stream?.bufferedReader()?.use { it.readText() } ?: "{}")
                            if (status !in 200..299) {
                                throw Exception(response.optString("error", "Permintaan kamera ditolak"))
                            }
                        } finally {
                            connection.disconnect()
                        }
                    }
                    statusText.text = "Permintaan terkirim. Menunggu video HP anak…"
                    startActivity(Intent(this@MainActivity, CameraViewerActivity::class.java)
                        .putExtra(CameraViewerActivity.EXTRA_CODE, code))
                } catch (error: Exception) {
                    statusText.text = "Gagal meminta kamera: ${error.localizedMessage ?: "periksa koneksi dan pengaturan server"}"
                }
            }
        }.addOnFailureListener {
            statusText.text = "Tidak dapat mengambil token autentikasi"
        }
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
        cameraStandbyButton.contentDescription =
            "Aktifkan siaga kamera agar perangkat orang tua dapat meminta tampilan kamera HP ini"
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

    private fun startChildAudioConnection() {
        if (currentRole != "child") return
        val intent = Intent(this, ChildAudioConnectionService::class.java).apply {
            action = ChildAudioConnectionService.ACTION_CONNECT
        }
        try {
            ContextCompat.startForegroundService(this, intent)
            statusText.text = "Koneksi audio berjalan dengan notifikasi • mikrofon mati"
        } catch (error: Exception) {
            statusText.text = "Tidak dapat memulai koneksi latar: ${error.localizedMessage ?: "izin sistem"}"
            Toast.makeText(this, "Buka aplikasi dan izinkan notifikasi agar koneksi dapat dipulihkan.", Toast.LENGTH_LONG).show()
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
            "expiresAt" to Timestamp(Date(now + 15 * 60 * 1000))
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
                "STATUS AUDIO HP ANAK\nMP3 dan audio live sedang aktif"
            liveAudioActive ->
                "STATUS AUDIO HP ANAK\nAudio langsung sedang diputar"
            mp3AudioActive ->
                "STATUS AUDIO HP ANAK\nMP3 sedang diputar"
            else ->
                "STATUS AUDIO HP ANAK\nTidak sedang memutar audio"
        }
        val audioIsActive = liveAudioActive || mp3AudioActive
        childAudioActivityText.setTextColor(
            if (audioIsActive) Color.rgb(23, 107, 67)
            else Color.rgb(82, 98, 122)
        )
        (childAudioActivityText.background as? GradientDrawable)?.apply {
            setColors(
                if (audioIsActive) {
                    intArrayOf(Color.rgb(249, 255, 251), Color.rgb(231, 247, 237))
                } else {
                    intArrayOf(Color.WHITE, Color.rgb(237, 245, 255))
                }
            )
            setStroke(
                dp(1),
                if (audioIsActive) Color.rgb(194, 230, 207)
                else Color.rgb(194, 215, 246)
            )
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
                    val childUid = snapshot.getString("childUid")
                    val connected = !childUid.isNullOrEmpty()
                    childConnected[code] = connected
                    renderChildrenList()
                    if (selectedChildCode == code) {
                        latestPairingSnapshot = snapshot
                        renderChildAudioActivity(snapshot)
                        childAudioActivityText.removeCallbacks(staleStatusRunnable)
                        childAudioActivityText.postDelayed(staleStatusRunnable, 36_000L)
                    }
                    if (connected) {
                        if (selectedChildCode == null) selectedChildCode = code
                        if (selectedChildCode == code && activeRoomCode != code && !isConnecting) {
                            statusText.text = "HP ${childNames[code] ?: "ANAK"} BERHASIL TERHUBUNG!"
                            codeText.text = code
                            selectChild(code)
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
            contentDescription = "Kolom kode pairing 8 karakter dari HP orang tua"
            isSingleLine = true
            textSize = 18f
            letterSpacing = 0.08f
            gravity = Gravity.CENTER
            setTypeface(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
            filters = arrayOf(
                android.text.InputFilter.LengthFilter(8),
                android.text.InputFilter.AllCaps()
            )
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
                        statusText.text = "Berhasil dipasangkan! Memulihkan koneksi audio..."

                        refreshFcmToken()
                        startChildAudioConnection()
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

    private fun connectToLiveKit(code: String) {
        if (room != null || isConnecting) return

        isConnecting = true
        statusText.text = "Meminta akses audio..."

        val user = auth.currentUser

        if (user == null) {
            isConnecting = false
            statusText.text = "Firebase belum terhubung!"
            return
        }

        user.getIdToken(false)
            .addOnSuccessListener { result ->

                val idToken = result.token

                if (idToken.isNullOrEmpty()) {
                    isConnecting = false
                    statusText.text = "Gagal mendapatkan token Firebase"
                    return@addOnSuccessListener
                }

                activityScope.launch {
                    try {
                        val response = withContext(Dispatchers.IO) {
                            requestLiveKitToken(idToken, code)
                        }

                        val token = response.getString("token")
                        val serverUrl = response.getString("serverUrl")
                        val role = response.getString("role")

                        if (role != currentRole) {
                            throw Exception("Peran perangkat tidak sesuai")
                        }

                        val newRoom = if (role == "child") {
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

                        newRoom.connect(
                            url = serverUrl,
                            token = token
                        )

                        room = newRoom
                        if (role == "parent") activeRoomCode = code
                        mp3TransferManager = Mp3TransferManager(
    context = this@MainActivity,
    room = newRoom,
    scope = activityScope,
    onStatus = { message ->
        statusText.text = message
    },
    onReceived = { file ->
    receivedMp3File = file
    playMp3Button.visibility = View.VISIBLE
    playReceivedMp3()
}
)

                        if (role == "parent") {
                            newRoom.localParticipant
                                .setMicrophoneEnabled(false)
                            isMicrophoneActive = false

                            statusText.text =
                                "HP ANAK TERHUBUNG"
                            showParentDashboard()

                        } else {
                            newRoom.localParticipant
                                .setMicrophoneEnabled(false)

                            statusText.text =
                                "TERHUBUNG • MENUNGGU AUDIO ORANG TUA"
                            showChildConnectedScreen()
                        }

                    } catch (error: Exception) {
                        statusText.text =
                            "Gagal menghubungkan audio: ${error.message}"

                        Toast.makeText(
                            this@MainActivity,
                            error.localizedMessage ?: "Koneksi gagal",
                            Toast.LENGTH_LONG
                        ).show()
                    } finally {
                        isConnecting = false
                    }
                }
            }
            .addOnFailureListener { error ->
                isConnecting = false
                statusText.text = "Gagal mendapatkan identitas Firebase"

                Toast.makeText(
                    this,
                    error.localizedMessage ?: "Koneksi gagal",
                    Toast.LENGTH_LONG
                ).show()
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

    private fun startMicrophone() {
        if (currentRole != "parent") return

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(Manifest.permission.RECORD_AUDIO),
                MIC_PERMISSION
            )
            return
        }

        activityScope.launch {
            try {
                val success = room?.localParticipant
                    ?.setMicrophoneEnabled(true) ?: false

                if (success) {
                    isMicrophoneActive = true
                    statusText.text = "MIKROFON AKTIF - SUARA SEDANG DIKIRIM"
                    startButton.visibility = View.GONE
                    stopButton.visibility = View.VISIBLE
                } else {
                    statusText.text = "Gagal mengaktifkan mikrofon"
                }

            } catch (error: Exception) {
                statusText.text = "Gagal mengaktifkan mikrofon"
            }
        }
    }

    private fun stopMicrophone() {
        activityScope.launch {
            try {
                room?.localParticipant?.setMicrophoneEnabled(false)

                isMicrophoneActive = false
                statusText.text = "Mikrofon berhenti"
                startButton.visibility = View.VISIBLE
                stopButton.visibility = View.GONE

            } catch (error: Exception) {
                statusText.text = "Gagal menghentikan mikrofon"
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

        val manager = mp3TransferManager

        if (manager == null) {
            statusText.text = "Ruang audio belum terhubung"
            return
        }

        manager.send(uri)
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
    }

    override fun onDestroy() {
        pairingListener?.remove()
        childListeners.values.forEach { it.remove() }
        childListeners.clear()
        childAudioActivityText.removeCallbacks(staleStatusRunnable)

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
