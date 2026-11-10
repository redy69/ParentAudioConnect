
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
    private lateinit var childAudioActivityText: TextView
    private lateinit var codeText: TextView
    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    private lateinit var sendMp3Button: Button
private lateinit var playMp3Button: Button
    private lateinit var adminWhatsAppButton: Button
    private lateinit var parentButton: Button
    private lateinit var childButton: Button
    private lateinit var rootLayout: LinearLayout
    private lateinit var childrenContainer: LinearLayout
    private lateinit var addChildButton: Button
    private lateinit var cancelPairingButton: Button
    private lateinit var updateButton: Button
    private var pendingPairingCode: String? = null

    private val childNames = linkedMapOf<String, String>()
    private val childConnected = linkedMapOf<String, Boolean>()
    private val childListeners = linkedMapOf<String, ListenerRegistration>()
    private val renamePromptedCodes = mutableSetOf<String>()
    private var selectedChildCode: String? = null
    private var activeRoomCode: String? = null

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
}
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        lifecycleRegistry.currentState = Lifecycle.State.STARTED

        auth = FirebaseAuth.getInstance()

        rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.TOP
            setPadding(dp(18), dp(18), dp(18), dp(28))
            setBackgroundColor(Color.rgb(244, 246, 250))
        }

        val title = TextView(this).apply {
            text = "SantetHP\\nKontrol Perangkat Keluarga"
            textSize = 23f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(22), dp(24), dp(22), dp(24))
            background = roundedPanel(Color.rgb(55, 80, 220), 24)
            elevation = dp(3).toFloat()
        }

        statusText = TextView(this).apply {
            text = "Memeriksa koneksi..."
            textSize = 14f
            setTextColor(Color.rgb(75, 83, 102))
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = roundedPanel(Color.WHITE, 16)
        }

        childAudioActivityText = TextView(this).apply {
            text = "AKTIVITAS HP ANAK\nTidak sedang memutar audio"
            textSize = 15f
            setTextColor(Color.rgb(180, 190, 210))
            gravity = Gravity.CENTER
            setPadding(16, 18, 16, 18)
        }

        codeText = TextView(this).apply {
            textSize = 26f
            setTextColor(Color.rgb(55, 80, 220))
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = roundedPanel(Color.WHITE, 18)
        }

        parentButton = createButton("ORTU")
        childButton = createButton("ANAK")
        startButton = createButton("BICARA LIVE")
        stopButton = createButton("HENTIKAN SUARA")
        sendMp3Button = createButton("PUTAR MP3")
        playMp3Button = createButton("PUTAR MP3 DITERIMA")
        adminWhatsAppButton = createButton("CHAT ADMIN")
        addChildButton = createButton("TAMBAH ANAK")
        cancelPairingButton = createButton("BATALKAN KODE")
        updateButton = createButton("PERIKSA PEMBARUAN")
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
        cancelPairingButton.setOnClickListener { cancelPendingPairingCode() }
        updateButton.setOnClickListener {
            val versionCode = packageManager.getPackageInfo(packageName, 0).versionCode
            AppUpdateManager.checkForUpdates(this, versionCode) { message -> statusText.text = message }
        }

        rootLayout.addView(title, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(16) })
        rootLayout.addView(updateButton)
        rootLayout.addView(parentButton)
        rootLayout.addView(childButton)
        rootLayout.addView(statusText)
        rootLayout.addView(codeText)
        rootLayout.addView(childAudioActivityText)
        rootLayout.addView(childrenContainer)
        rootLayout.addView(addChildButton)
        rootLayout.addView(cancelPairingButton)
        rootLayout.addView(startButton)
        rootLayout.addView(stopButton)
        rootLayout.addView(sendMp3Button)
        rootLayout.addView(playMp3Button)
        rootLayout.addView(adminWhatsAppButton)

        showRoleSelection()
        val scrollView = ScrollView(this).apply {
            fillViewport = true
            isVerticalScrollBarEnabled = false
            setBackgroundColor(Color.rgb(244, 246, 250))
            addView(rootLayout)
        }
        setContentView(scrollView)

        connectFirebase()
    }

    override fun onResume() {
        super.onResume()
        AppUpdateManager.resumePendingInstall(this)
    }

    private fun showRoleSelection() {
        parentButton.visibility = View.VISIBLE
        childButton.visibility = View.VISIBLE
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
    }

    private fun showPairingCodeScreen() {
        parentButton.visibility = View.GONE
        childButton.visibility = View.GONE
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
    }

    private fun showParentDashboard() {
        parentButton.visibility = View.GONE
        childButton.visibility = View.GONE
        codeText.visibility = View.VISIBLE
        childAudioActivityText.visibility = View.VISIBLE
        startButton.visibility = if (isMicrophoneActive) View.GONE else View.VISIBLE
        stopButton.visibility = if (isMicrophoneActive) View.VISIBLE else View.GONE
        sendMp3Button.visibility = View.VISIBLE
        playMp3Button.visibility = View.GONE
        adminWhatsAppButton.visibility = View.VISIBLE
        childrenContainer.visibility = View.VISIBLE
        addChildButton.visibility = View.VISIBLE
        cancelPairingButton.visibility = View.GONE
        renderChildrenList()
    }

    private fun showChildConnectedScreen() {
        parentButton.visibility = View.GONE
        childButton.visibility = View.GONE
        codeText.visibility = View.VISIBLE
        childAudioActivityText.visibility = View.GONE
        startButton.visibility = View.GONE
        stopButton.visibility = View.GONE
        sendMp3Button.visibility = View.GONE
        playMp3Button.visibility = View.GONE
        adminWhatsAppButton.visibility = View.GONE
        childrenContainer.visibility = View.GONE
        addChildButton.visibility = View.GONE
        cancelPairingButton.visibility = View.GONE
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

    private fun roundedPanel(color: Int, radiusDp: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radiusDp).toFloat()
        }

    private fun createButton(label: String): Button {
        return Button(this).apply {
            text = label
            textSize = 14f
            isAllCaps = false
            setTextColor(Color.WHITE)
            background = roundedPanel(Color.rgb(66, 95, 235), 16)
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

    private fun renderChildrenList() {
        if (!::childrenContainer.isInitialized) return
        childrenContainer.removeAllViews()
        childNames.forEach { (code, name) ->
            val connected = childConnected[code] == true
            val item = TextView(this).apply {
                text = "$name\\n${if (connected) "● TERHUBUNG" else "○ MENUNGGU / OFFLINE"}"
                textSize = 15f
                setTextColor(Color.rgb(48, 56, 74))
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(18), dp(14), dp(18), dp(14))
                background = roundedPanel(Color.WHITE, 16)
                elevation = dp(1).toFloat()
                isClickable = true
                isFocusable = true
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, dp(5), 0, dp(5)) }
            }
            item.setOnClickListener { selectChild(code) }
            item.setOnLongClickListener { promptRenameChild(code); true }
            childrenContainer.addView(item)
        }
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

    private fun selectChild(code: String) {
        if (childConnected[code] != true) {
            selectedChildCode = code
            codeText.text = code
            statusText.text = "Menunggu HP ${childNames[code] ?: "anak"} terhubung..."
            return
        }
        if (activeRoomCode == code && room != null) return
        selectedChildCode = code
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
            statusText.text = "Memulihkan koneksi audio (mikrofon tetap mati)..."
            startChildAudioConnection()
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
                "AKTIVITAS HP ANAK\nMP3 dan audio live sedang aktif"
            liveAudioActive ->
                "AKTIVITAS HP ANAK\nAudio langsung sedang diputar"
            mp3AudioActive ->
                "AKTIVITAS HP ANAK\nMP3 sedang diputar"
            else ->
                "AKTIVITAS HP ANAK\nTidak sedang memutar audio"
        }
        childAudioActivityText.setTextColor(
            if (liveAudioActive || mp3AudioActive) Color.rgb(105, 230, 170)
            else Color.rgb(180, 190, 210)
        )
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
                        statusText.text = "Berhasil dipasangkan! Memulihkan koneksi audio..."

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
