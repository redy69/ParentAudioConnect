
package com.parentaudioconnect
import android.content.Intent
import android.net.Uri
import java.io.File
import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.Timestamp
import com.google.firebase.messaging.FirebaseMessaging

import io.livekit.android.LiveKit
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

private var mp3TransferManager: Mp3TransferManager? = null
private var receivedMp3File: File? = null

    private var pairingListener: ListenerRegistration? = null
    private var room: Room? = null
    private var isConnecting = false
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

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(32, 40, 32, 40)
            setBackgroundColor(Color.rgb(15, 20, 35))
        }

        val title = TextView(this).apply {
            text = "Parent Audio Connect"
            textSize = 26f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }

        statusText = TextView(this).apply {
            text = "Memeriksa koneksi..."
            textSize = 16f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            setPadding(0, 24, 0, 24)
        }

        childAudioActivityText = TextView(this).apply {
            text = "Aktivitas HP anak: belum ada audio aktif"
            textSize = 15f
            setTextColor(Color.rgb(180, 190, 210))
            gravity = Gravity.CENTER
            setPadding(16, 16, 16, 16)
        }

        codeText = TextView(this).apply {
            textSize = 30f
            setTextColor(Color.rgb(128, 222, 234))
            gravity = Gravity.CENTER
            setPadding(0, 20, 0, 20)
        }

        val parentButton = createButton("BUAT KODE ORANG TUA")
        val childButton = createButton("MASUKKAN KODE ANAK")

        startButton = createButton("MULAI KIRIM SUARA")
        stopButton = createButton("HENTIKAN SUARA")
        sendMp3Button = createButton("KIRIM FILE MP3")
playMp3Button = createButton("PUTAR MP3 DITERIMA")

sendMp3Button.visibility = View.GONE
playMp3Button.visibility = View.GONE

        startButton.visibility = View.GONE
        stopButton.visibility = View.GONE
        sendMp3Button.visibility = View.VISIBLE

        parentButton.setOnClickListener {
            createPairingCode()
        }

        childButton.setOnClickListener {
            showCodeDialog()
        }

        startButton.setOnClickListener {
            startMicrophone()
        }

        stopButton.setOnClickListener {
            stopMicrophone()
        }
        sendMp3Button.setOnClickListener {
    openMp3Picker()
}

playMp3Button.setOnClickListener {
    playReceivedMp3()
}

        layout.addView(title)
        layout.addView(statusText)
        layout.addView(childAudioActivityText)
        layout.addView(codeText)
        layout.addView(parentButton)
        layout.addView(childButton)
        layout.addView(startButton)
        layout.addView(stopButton)
        layout.addView(sendMp3Button)
        layout.addView(playMp3Button)

        setContentView(layout)

        connectFirebase()
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
                val savedCode = getSharedPreferences("parent_audio", MODE_PRIVATE)
                    .getString("pair_code", null)
                val savedRole = getSharedPreferences("parent_audio", MODE_PRIVATE)
                    .getString("pair_role", null)
                if (!savedCode.isNullOrBlank() && !savedRole.isNullOrBlank()) {
                    registerDeviceToken(savedCode, savedRole, token)
                }
            }
            .addOnFailureListener {
                // Keep the app usable if token retrieval is temporarily unavailable.
            }
    }

    private fun createButton(label: String): Button {
        return Button(this).apply {
            text = label
            textSize = 15f
            isAllCaps = false

            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 12, 0, 12)
            }
        }
    }

    private fun savePairing(code: String, role: String) {
        getSharedPreferences("parent_audio", MODE_PRIVATE)
            .edit()
            .putString("pair_code", code)
            .putString("pair_role", role)
            .apply()

        val token = getSharedPreferences("parent_audio", MODE_PRIVATE)
            .getString("fcm_token", null)
        if (!token.isNullOrBlank()) {
            registerDeviceToken(code, role, token)
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

        val code = prefs.getString("pair_code", null)
        val role = prefs.getString("pair_role", null)

        if (code.isNullOrEmpty() || role.isNullOrEmpty()) {
            return
        }

        currentRole = role
        codeText.text = code

        if (role == "parent") {
            statusText.text = "Menunggu HP anak..."
            listenForChild(code)
        } else {
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

                savePairing(code, "parent")
                currentRole = "parent"

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

    private fun listenForChild(code: String) {
        pairingListener?.remove()

        pairingListener = db.collection("pairings")
            .document(code)
            .addSnapshotListener { snapshot, error ->

                if (error != null) {
                    statusText.text = "Gagal memeriksa pasangan"
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    val liveAudioActive = snapshot.getBoolean("liveAudioActive") == true
                    val mp3AudioActive = snapshot.getBoolean("mp3AudioActive") == true
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

                    val childUid = snapshot.getString("childUid")

                    if (!childUid.isNullOrEmpty()) {
                        statusText.text = "HP ANAK BERHASIL TERHUBUNG!"
                        connectToLiveKit(code)
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

                        val newRoom = LiveKit.create(applicationContext)

                        newRoom.connect(
                            url = serverUrl,
                            token = token
                        )

                        room = newRoom
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

                            statusText.text =
                                "TERHUBUNG! Siap mengirim suara"

                            startButton.visibility = View.VISIBLE
                            stopButton.visibility = View.GONE

                        } else {
                            newRoom.localParticipant
                                .setMicrophoneEnabled(false)

                            statusText.text =
                                "TERHUBUNG! MENUNGGU SUARA ORANG TUA"
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

        activityScope.launch {
            room?.disconnect()
            room?.release()
            room = null
        }

        activityScope.cancel()

        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED

        super.onDestroy()
    }
}
